package com.blindnav.trip

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.blindnav.BlindNavApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps GPS running while the screen is off. Holds no game logic: it feeds fixes to [TripSession]
 * and stops itself when the trip ends. Not sticky: a killed process loses the trip (v1 limitation).
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_NOT_STICKY
        running = true
        val container = (application as BlindNavApp).container
        try {
            ServiceCompat.startForeground(
                this,
                TripNotifications.TRACKING_ID,
                TripNotifications.tracking(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: RuntimeException) {
            // SecurityException, or ForegroundServiceStartNotAllowedException if the app lost the foreground.
            container.session.fail("Couldn't start location tracking")
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch { container.location.updates().collect(container.session::onFix) }
        scope.launch {
            container.session.state.collect { state ->
                when (state) {
                    is TripState.Finished -> {
                        TripNotifications.showArrived(this@TrackingService, state.tripId)
                        stop()
                    }
                    TripState.Idle, is TripState.Failed -> stop()
                    else -> Unit
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        scope.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
