package com.blindnav.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.blindnav.geo.GeoPoint
import com.blindnav.trip.Fix
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Fused location. Trip code only runs with location permission; [position] is safe without it. */
@SuppressLint("MissingPermission")
class LocationClient(private val context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    /** 1 Hz high-accuracy updates; every batched location is emitted in order. */
    fun updates(): Flow<Fix> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000)
            .setMinUpdateIntervalMillis(1_000)
            .setWaitForAccurateLocation(false)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    /** First fix accurate to [maxAccuracyM], or null after [timeoutMs]. */
    suspend fun preciseFix(maxAccuracyM: Float = 25f, timeoutMs: Long = 15_000): Fix? =
        withTimeoutOrNull(timeoutMs) { updates().first { (it.accuracyM ?: Float.MAX_VALUE) <= maxAccuracyM } }

    /** A fix at most 10 s old; the plain cached location can be minutes stale after moving. */
    suspend fun position(): GeoPoint? {
        // COARSE is granted whenever any location access is.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(10_000)
            .build()
        return (fused.getCurrentLocation(request, null).await() ?: fused.lastLocation.await())
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }
}

fun Location.toFix() = Fix(
    lat = latitude,
    lng = longitude,
    accuracyM = if (hasAccuracy()) accuracy else null,
    speedMps = if (hasSpeed()) speed else null,
    elapsedNanos = elapsedRealtimeNanos,
)
