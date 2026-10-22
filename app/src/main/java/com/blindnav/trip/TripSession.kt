package com.blindnav.trip

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.blindnav.BuildConfig
import com.blindnav.data.TripEntity
import com.blindnav.data.TripRepository
import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.PolylineCodec
import com.blindnav.location.LocationClient
import com.blindnav.routes.OptimalRoute
import com.blindnav.routes.RoutesClient
import com.blindnav.routes.RoutesException
import com.blindnav.scoring.MemorizePreset
import com.blindnav.scoring.ScoreCalculator
import com.blindnav.scoring.ScoreInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

sealed interface TripState {
    data object Idle : TripState
    data class Preparing(val destination: Destination) : TripState
    data class Memorizing(
        val destination: Destination,
        val route: OptimalRoute,
        val origin: GeoPoint,
        val seconds: Int,
        val endsAtNanos: Long,
    ) : TripState

    /** [movementStartNanos] is null until the car first moves; the clock runs from there. */
    data class Driving(val destination: Destination, val movementStartNanos: Long?, val canFinish: Boolean) : TripState
    data object Finishing : TripState
    data class Finished(val tripId: Long) : TripState
    data class Failed(val message: String) : TripState
}

/**
 * App-scoped game loop: fetch route, memorize countdown, blind drive, score and save.
 * [TrackingService] feeds fixes in via [onFix]; UI calls the other methods. Main thread only.
 */
class TripSession(
    private val appContext: Context,
    private val routes: RoutesClient,
    private val location: LocationClient,
    private val repository: TripRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TripState>(TripState.Idle)
    val state: StateFlow<TripState> = _state.asStateFlow()

    private var destination: Destination? = null
    private var preset: MemorizePreset? = null
    private var route: OptimalRoute? = null
    private var tracker: TripTracker? = null
    private var startedAtMs = 0L
    private var countdown: Job? = null

    fun start(destination: Destination, preset: MemorizePreset) {
        if (_state.value != TripState.Idle) return
        this.destination = destination
        this.preset = preset
        startedAtMs = System.currentTimeMillis()
        _state.value = TripState.Preparing(destination)
        // Must start while the app is in the foreground (location FGS rules); also warms up GPS.
        ContextCompat.startForegroundService(appContext, Intent(appContext, TrackingService::class.java))
        countdown = scope.launch {
            try {
                val origin = location.preciseFix()?.point
                    ?: return@launch fail("Couldn't get a precise GPS fix. Try again with a clear view of the sky.")
                if (GeoMath.distanceM(origin, destination.point) < MIN_TRIP_M) {
                    return@launch fail("You're already within 300 m of ${destination.name}. Pick somewhere farther away.")
                }
                val route = routes.computeOptimalRoute(origin, destination.point, destination.placeId)
                if (BuildConfig.DEBUG) logRoute(route.encodedPolyline)
                this@TripSession.route = route
                tracker = TripTracker(origin, destination.point, route.end)
                val endsAt = SystemClock.elapsedRealtimeNanos() + preset.seconds * 1_000_000_000L
                _state.value = TripState.Memorizing(destination, route, origin, preset.seconds, endsAt)
                delay(preset.seconds * 1_000L)
                hideMap()
            } catch (e: RoutesException) {
                fail(e.message ?: "No route found")
            } catch (e: IOException) {
                fail("Network error: couldn't reach Google Routes")
            } catch (e: JSONException) {
                fail("Unexpected response from Google Routes")
            }
        }
    }

    /** "I'm ready": end the memorize phase early. */
    fun endMemorize() {
        countdown?.cancel()
        hideMap()
    }

    fun onFix(fix: Fix) {
        val tracker = tracker ?: return
        tracker.onFix(fix)
        val state = _state.value
        val destination = when (state) {
            is TripState.Memorizing -> state.destination
            is TripState.Driving -> state.destination
            else -> return
        }
        if (tracker.arrivedAtNanos != null) return finish()
        if (state is TripState.Memorizing) {
            if (tracker.movementStartNanos == null) return
            countdown?.cancel() // Moving with the map still up: hide it now.
        }
        _state.value = TripState.Driving(destination, tracker.movementStartNanos, tracker.canFinishManually)
    }

    fun finishManually() {
        if ((_state.value as? TripState.Driving)?.canFinish == true) finish()
    }

    fun giveUp() = reset(TripState.Idle)

    fun fail(message: String) = reset(TripState.Failed(message))

    /** Results (or the failure) has been shown. */
    fun acknowledge() {
        if (_state.value is TripState.Finished || _state.value is TripState.Failed) _state.value = TripState.Idle
    }

    private fun hideMap() {
        val state = _state.value as? TripState.Memorizing ?: return
        val tracker = tracker ?: return
        _state.value = TripState.Driving(state.destination, tracker.movementStartNanos, tracker.canFinishManually)
    }

    private fun finish() {
        val outcome = tracker?.outcome() ?: return
        val destination = destination ?: return
        val preset = preset ?: return
        val route = route ?: return
        _state.value = TripState.Finishing
        scope.launch {
            val trip = withContext(Dispatchers.Default) { scoredTrip(destination, preset, route, outcome) }
            val id = repository.save(trip)
            reset(TripState.Finished(id))
        }
    }

    private fun scoredTrip(destination: Destination, preset: MemorizePreset, route: OptimalRoute, outcome: TripOutcome): TripEntity {
        val score = ScoreCalculator.score(
            ScoreInput(
                optimalPath = route.path,
                optimalDurationS = route.durationS,
                optimalDistanceM = route.distanceM.toDouble(),
                track = outcome.track,
                durationS = outcome.durationS,
                distanceM = outcome.distanceM,
                multiplier = preset.multiplier,
            )
        )
        return TripEntity(
            startedAtMs = startedAtMs,
            destName = destination.name,
            destAddress = destination.address,
            destPlaceId = destination.placeId,
            destLat = destination.point.lat,
            destLng = destination.point.lng,
            memorizeSeconds = preset.seconds,
            multiplier = preset.multiplier,
            optimalPolyline = route.encodedPolyline,
            optimalDistanceM = route.distanceM,
            optimalDurationS = route.durationS,
            trackPolyline = PolylineCodec.encode(outcome.track),
            distanceM = outcome.distanceM,
            durationS = outcome.durationS,
            timeScore = score.timeScore,
            distanceScore = score.distanceScore,
            routeMatch = score.routeMatch,
            detourCount = score.detourCount,
            base = score.base,
            points = score.points,
            grade = score.grade,
        )
    }

    /** For tools/replay_route.py. Chunked because logcat truncates long lines. */
    private fun logRoute(encoded: String) {
        val chunks = encoded.chunked(3_000)
        chunks.forEachIndexed { i, chunk -> Log.d("BlindNavRoute", "${i + 1}/${chunks.size} $chunk") }
    }

    companion object {
        /** Shorter trips are dominated by pulling out and parking. */
        const val MIN_TRIP_M = 300.0
    }

    private fun reset(next: TripState) {
        countdown?.cancel()
        destination = null
        preset = null
        route = null
        tracker = null
        _state.value = next
    }
}
