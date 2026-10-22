package com.blindnav

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blindnav.ui.AppNavHost
import com.blindnav.ui.theme.BlindNavTheme

class MainActivity : ComponentActivity() {
    /** Trip to show, from tapping the "arrived" notification. */
    private var openTripId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light UI in both system themes, so always dark bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openTripId = intent.tripId()
        setContent {
            BlindNavTheme {
                AppNavHost(openTripId, onTripOpened = { openTripId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTripId = intent.tripId()
    }

    private fun Intent.tripId(): Long? = getLongExtra(EXTRA_TRIP_ID, -1).takeIf { it >= 0 }

    companion object {
        const val EXTRA_TRIP_ID = "com.blindnav.TRIP_ID"
    }
}
