package com.blindnav.trip

import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import kotlin.math.min

/** One location update. [elapsedNanos] is on the elapsedRealtime clock, never wall time. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val speedMps: Float?,
    val elapsedNanos: Long,
) {
    val point get() = GeoPoint(lat, lng)
}

data class TrackerConfig(
    val maxAccuracyM: Double = 25.0,
    val maxPlausibleSpeedMps: Double = 70.0,
    val minStepM: Double = 10.0,
    val movementStartM: Double = 25.0,
    val movementConfirmFixes: Int = 2,
    val arrivalRadiusM: Double = 75.0,
    val arrivalMaxSpeedMps: Double = 8.0,
    val finishRadiusM: Double = 250.0,
    val speedWindowNanos: Long = 3_000_000_000,
)

sealed interface TrackerEvent {
    data object None : TrackerEvent
    data class MovementStarted(val atNanos: Long) : TrackerEvent
    data class Arrived(val atNanos: Long) : TrackerEvent
}

/** What gets scored: the track up to the finish point, its length and the time from first movement. */
data class TripOutcome(val track: List<GeoPoint>, val distanceM: Double, val durationS: Double)

/**
 * Pure trip logic: filters fixes, builds the track, detects first movement and arrival.
 * Arrival counts within [TrackerConfig.arrivalRadiusM] of the destination or the road-snapped route end.
 */
class TripTracker(
    private val origin: GeoPoint,
    private val destination: GeoPoint,
    private val routeEnd: GeoPoint,
    private val config: TrackerConfig = TrackerConfig(),
) {
    private val track = mutableListOf(origin)
    private val cumulativeM = mutableListOf(0.0)
    private val pendingStart = mutableListOf<Fix>()
    private val recent = ArrayDeque<Fix>()
    private var lastAccepted: Fix? = null

    /** Closest approach to the target since entering the finish zone, as (track index, nanos, metres). */
    private var finishCandidate: Triple<Int, Long, Double>? = null

    var movementStartNanos: Long? = null
        private set
    var arrivedAtNanos: Long? = null
        private set

    val distanceM: Double get() = cumulativeM.last()
    val canFinishManually: Boolean get() = finishCandidate != null

    fun onFix(fix: Fix): TrackerEvent {
        if (arrivedAtNanos != null || !accept(fix)) return TrackerEvent.None

        var event: TrackerEvent = TrackerEvent.None
        if (movementStartNanos == null) {
            if (GeoMath.distanceM(origin, fix.point) <= config.movementStartM) {
                pendingStart.clear()
                return TrackerEvent.None
            }
            pendingStart += fix
            if (pendingStart.size < config.movementConfirmFixes) return TrackerEvent.None
            val start = pendingStart.first().elapsedNanos
            movementStartNanos = start
            pendingStart.forEach { append(it) }
            pendingStart.clear()
            event = TrackerEvent.MovementStarted(start)
        } else {
            append(fix)
        }

        val toTarget = min(GeoMath.distanceM(fix.point, destination), GeoMath.distanceM(fix.point, routeEnd))
        finishCandidate = when {
            toTarget > config.finishRadiusM -> null
            finishCandidate.let { it == null || toTarget < it.third } ->
                Triple(track.lastIndex, fix.elapsedNanos, toTarget)
            else -> finishCandidate
        }
        if (toTarget <= config.arrivalRadiusM && speedOf(fix) < config.arrivalMaxSpeedMps) {
            arrivedAtNanos = fix.elapsedNanos
            return TrackerEvent.Arrived(fix.elapsedNanos)
        }
        return event
    }

    /** After auto-arrival: the full track. Otherwise (manual Finish): truncated at the closest approach. */
    fun outcome(): TripOutcome? {
        val start = movementStartNanos ?: return null
        arrivedAtNanos?.let { return TripOutcome(track.toList(), distanceM, (it - start) / 1e9) }
        val (index, nanos, _) = finishCandidate ?: return null
        return TripOutcome(track.subList(0, index + 1).toList(), cumulativeM[index], (nanos - start) / 1e9)
    }

    /** Rejects inaccurate fixes and spikes implying an implausible speed from the last accepted fix. */
    private fun accept(fix: Fix): Boolean {
        val accuracy = fix.accuracyM ?: return false
        if (accuracy > config.maxAccuracyM) return false
        lastAccepted?.let { last ->
            val dt = (fix.elapsedNanos - last.elapsedNanos) / 1e9
            if (dt <= 0 || GeoMath.distanceM(last.point, fix.point) / dt > config.maxPlausibleSpeedMps) return false
        }
        lastAccepted = fix
        recent.addLast(fix)
        while (fix.elapsedNanos - recent.first().elapsedNanos > config.speedWindowNanos) recent.removeFirst()
        return true
    }

    /** Adds to the track only every [TrackerConfig.minStepM] so stationary jitter doesn't inflate distance. */
    private fun append(fix: Fix) {
        val step = GeoMath.distanceM(track.last(), fix.point)
        if (step < config.minStepM) return
        track += fix.point
        cumulativeM += cumulativeM.last() + step
    }

    /** Reported speed when meaningful; otherwise derived over the recent window (emulator/missing speed = 0). */
    private fun speedOf(fix: Fix): Double {
        fix.speedMps?.let { if (it > 0.5f) return it.toDouble() }
        val oldest = recent.first()
        val dt = (fix.elapsedNanos - oldest.elapsedNanos) / 1e9
        return if (dt < 1.0) 0.0 else GeoMath.distanceM(oldest.point, fix.point) / dt
    }
}
