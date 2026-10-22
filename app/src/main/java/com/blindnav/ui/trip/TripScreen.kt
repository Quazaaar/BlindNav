package com.blindnav.ui.trip

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindnav.geo.toLatLng
import com.blindnav.trip.Destination
import com.blindnav.trip.TripState
import com.blindnav.ui.common.Format
import com.blindnav.ui.common.RouteLine
import com.blindnav.ui.common.appViewModel
import com.blindnav.ui.common.boundsOf
import com.blindnav.ui.theme.Blue
import com.blindnav.ui.theme.DetourRed
import com.blindnav.ui.theme.SecondaryText
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val DimText = Color(0xFF9AA0A6)

/** One route for the whole trip; content follows [TripState]. */
@Composable
fun TripScreen(onFinished: (Long) -> Unit, onExit: () -> Unit) {
    val vm = appViewModel { container, _ -> TripViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Idle on entry only happens when the process died mid-trip; Idle later means we're already leaving.
    val enteredIdle = remember { state == TripState.Idle }
    var confirmGiveUp by remember { mutableStateOf(false) }
    val inProgress = state is TripState.Preparing || state is TripState.Memorizing || state is TripState.Driving
    val context = LocalContext.current
    // Once the map is hidden, giving up hands the rest of the drive to Google Maps in case they're lost.
    val rescueTo = (state as? TripState.Driving)?.destination
    val giveUp = {
        confirmGiveUp = false
        vm.giveUp()
        rescueTo?.let(context::openNavigation)
        onExit()
    }

    BackHandler(enabled = inProgress) { confirmGiveUp = true }

    when (val s = state) {
        is TripState.Preparing -> Status("Getting a precise GPS fix and the fastest route…", "Cancel", onAction = giveUp)
        is TripState.Memorizing -> MemorizeContent(s, onReady = vm::endMemorize, onGiveUp = { confirmGiveUp = true })
        is TripState.Driving -> DriveContent(s, onFinish = vm::finish, onGiveUp = { confirmGiveUp = true })
        TripState.Finishing -> Status("You've arrived. Scoring your drive…")
        is TripState.Finished -> LaunchedEffect(s.tripId) { onFinished(s.tripId) }
        is TripState.Failed -> Status(s.message, "Back to map", showSpinner = false) {
            vm.acknowledge()
            onExit()
        }
        TripState.Idle -> if (enteredIdle) Status("No trip in progress.", "Back to map", showSpinner = false, onAction = onExit)
    }

    if (confirmGiveUp && inProgress) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text("Give up this trip?") },
            text = {
                Text(
                    if (rescueTo == null) "Nothing will be saved."
                    else "Nothing will be saved. Google Maps will guide you the rest of the way to ${rescueTo.name}."
                )
            },
            confirmButton = {
                TextButton(onClick = giveUp) { Text(if (rescueTo == null) "Give up" else "Give up & navigate") }
            },
            dismissButton = { TextButton(onClick = { confirmGiveUp = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Status(text: String, actionLabel: String? = null, showSpinner: Boolean = true, onAction: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

private fun secondsUntil(nanos: Long) = ((nanos - SystemClock.elapsedRealtimeNanos()) / 1e9).coerceAtLeast(0.0)

@Composable
private fun MemorizeContent(state: TripState.Memorizing, onReady: () -> Unit, onGiveUp: () -> Unit) {
    val camera = rememberCameraPositionState()
    val paddingPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val secondsLeft by produceState(secondsUntil(state.endsAtNanos), state.endsAtNanos) {
        while (true) {
            value = secondsUntil(state.endsAtNanos)
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, mapToolbarEnabled = false, zoomControlsEnabled = false),
            mapColorScheme = ComposeMapColorScheme.LIGHT,
            contentPadding = PaddingValues(top = 170.dp, bottom = 130.dp),
            onMapLoaded = {
                val points = state.route.path + state.origin + state.destination.point
                camera.move(CameraUpdateFactory.newLatLngBounds(boundsOf(points), paddingPx))
            },
        ) {
            RouteLine(state.route.path)
            MarkerComposable(state = rememberUpdatedMarkerState(state.origin.toLatLng()), anchor = Offset(0.5f, 0.5f)) {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(3.dp, Color.White, CircleShape)
                        .background(Blue, CircleShape)
                )
            }
            Marker(state = rememberUpdatedMarkerState(state.destination.point.toLatLng()), title = state.destination.name)
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 4.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Memorize the route", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        Format.duration(ceil(secondsLeft)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 5) DetourRed else Blue,
                    )
                }
                LinearProgressIndicator(
                    progress = { (secondsLeft / state.seconds).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    "Target ${Format.duration(state.route.durationS)} · ${Format.distance(state.route.distanceM.toDouble())} " +
                        "to ${state.destination.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onReady,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("I'm ready: hide the map")
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onGiveUp) { Text("Give up") }
            }
        }
    }
}

@Composable
private fun DriveContent(state: TripState.Driving, onFinish: () -> Unit, onGiveUp: () -> Unit) {
    LightSystemBarIcons()
    val elapsed by produceState<Double?>(null, state.movementStartNanos) {
        val start = state.movementStartNanos ?: return@produceState
        while (true) {
            value = (SystemClock.elapsedRealtimeNanos() - start) / 1e9
            delay(250)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text("DRIVING TO", style = MaterialTheme.typography.labelMedium, color = DimText)
            Text(state.destination.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            val seconds = elapsed
            if (seconds == null) {
                Text("Waiting for you to move…", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("The clock starts when you do.", color = DimText, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text(
                    Format.duration(seconds),
                    color = Color.White,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Light,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                )
                Text(
                    "Drive from memory. Arrival is detected automatically.",
                    color = DimText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onFinish,
                enabled = state.canFinish,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF303134),
                    disabledContentColor = DimText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Finish")
            }
            Text(
                if (state.canFinish) "Tap if your arrival wasn't detected" else "Finish unlocks within 250 m of the destination",
                style = MaterialTheme.typography.bodySmall,
                color = DimText,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onGiveUp, modifier = Modifier.padding(top = 8.dp)) { Text("Give up", color = DimText) }
        }
    }
}

/** Turn-by-turn directions in Google Maps (or the browser when the Maps app is missing). */
private fun Context.openNavigation(destination: Destination) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, destination.navigationUrl().toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Install Google Maps to get directions", Toast.LENGTH_LONG).show()
    }
}

/** White status/navigation bar icons while the black drive screen is showing. */
@Composable
private fun LightSystemBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
}
