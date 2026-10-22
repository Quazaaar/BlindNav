package com.blindnav.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blindnav.geo.toLatLng
import com.blindnav.trip.TripNotifications
import com.blindnav.ui.common.Format
import com.blindnav.ui.common.GradeBadge
import com.blindnav.ui.common.RouteLine
import com.blindnav.ui.common.appViewModel
import com.blindnav.ui.common.boundsOf
import com.blindnav.ui.theme.Blue
import com.blindnav.ui.theme.DetourRed
import com.blindnav.ui.theme.SecondaryText
import com.blindnav.ui.theme.TrackGray
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.text.DateFormat
import java.util.Date

@Composable
fun ResultsScreen(onDone: () -> Unit, onHistory: () -> Unit) {
    val vm = appViewModel { container, handle -> ResultsViewModel(container, handle) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { TripNotifications.cancelArrived(context) }

    val ui = vm.ui
    if (ui == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.45f)
        ) {
            OverlayMap(ui)
            SmallFloatingActionButton(
                onClick = onDone,
                shape = CircleShape,
                containerColor = Color.White,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map")
            }
            Legend(
                showOptimal = ui.optimal != null,
                showDetours = ui.detours.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
        ) {
            ScoreSheet(ui, onDone, onHistory)
        }
    }
}

@Composable
private fun OverlayMap(ui: ResultsUi) {
    val camera = rememberCameraPositionState()
    val paddingPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, mapToolbarEnabled = false, zoomControlsEnabled = false),
        mapColorScheme = ComposeMapColorScheme.LIGHT,
        contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp),
        onMapLoaded = {
            val points = ui.track + ui.optimal.orEmpty() + ui.trip.destination
            camera.move(CameraUpdateFactory.newLatLngBounds(boundsOf(points), paddingPx))
        },
    ) {
        ui.optimal?.let { RouteLine(it) }
        Polyline(points = ui.track.map { it.toLatLng() }, color = TrackGray, width = 7f, zIndex = 2f)
        ui.detours.forEach { detour ->
            Polyline(points = detour.map { it.toLatLng() }, color = DetourRed, width = 7f, zIndex = 3f)
        }
        MarkerComposable(state = rememberUpdatedMarkerState(ui.track.first().toLatLng()), anchor = Offset(0.5f, 0.5f)) {
            Box(
                Modifier
                    .size(14.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(TrackGray, CircleShape)
            )
        }
        Marker(state = rememberUpdatedMarkerState(ui.trip.destination.toLatLng()), title = ui.trip.destName)
    }
}

@Composable
private fun Legend(showOptimal: Boolean, showDetours: Boolean, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (showOptimal) LegendRow(Blue, "Optimal")
            LegendRow(TrackGray, "You")
            if (showDetours) LegendRow(DetourRed, "Detours")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier
                .size(width = 16.dp, height = 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScoreSheet(ui: ResultsUi, onDone: () -> Unit, onHistory: () -> Unit) {
    val trip = ui.trip
    val comparisons = ui.comparisons
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(trip.grade, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "${Format.points(trip.points)} pts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${trip.memorizeSeconds} s memorize ×${trip.multiplier} · base ${trip.base}/1000",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                )
                when {
                    comparisons.isNewBest -> Pill("New personal best!")
                    comparisons.previousBest == null -> Pill("First run here")
                }
            }
        }
        Text(trip.destName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startedAtMs)),
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryText,
        )

        SectionTitle("Optimal vs you")
        TableRow("", "Optimal", "You", "Δ", header = true)
        TableRow(
            "Time",
            Format.duration(trip.optimalDurationS),
            Format.duration(trip.durationS),
            Format.durationDelta(trip.durationS - trip.optimalDurationS),
        )
        TableRow(
            "Distance",
            Format.distance(trip.optimalDistanceM.toDouble()),
            Format.distance(trip.distanceM),
            Format.distanceDelta(trip.distanceM - trip.optimalDistanceM),
        )

        SectionTitle("Score breakdown")
        ScoreBar("Time (capped at the ETA)", trip.timeScore, "35%")
        ScoreBar("Distance", trip.distanceScore, "35%")
        ScoreBar("Route match", trip.routeMatch, "30%")
        Text(
            when (trip.detourCount) {
                0 -> "No detours"
                1 -> "1 detour off the optimal route"
                else -> "${trip.detourCount} detours off the optimal route"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionTitle("Compared with you")
        val best = comparisons.previousBest
        CompareRow(
            "Your best here",
            best?.let { "${Format.points(it.points)} pts (${it.grade})" } ?: "—",
            best?.let { signed(trip.points - it.points) },
        )
        CompareRow(
            "Your average",
            comparisons.averagePoints?.let { "${Format.points(it)} pts" } ?: "—",
            comparisons.averagePoints?.let { signed(trip.points - it) },
        )

        if (ui.optimal == null) {
            Text(
                "The optimal route line is removed after 30 days (Google Maps terms). Scores are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(Modifier.padding(top = 24.dp)) {
            OutlinedButton(
                onClick = onHistory,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { Text("History") }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { Text("Done") }
        }
    }
}

private fun signed(points: Int) = if (points >= 0) "+$points" else "−${-points}"

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = SecondaryText, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun TableRow(label: String, optimal: String, you: String, delta: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) SecondaryText else Color.Unspecified
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = style, color = color, modifier = Modifier.weight(1.2f))
        Text(optimal, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(you, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(delta, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScoreBar(label: String, fraction: Double, weight: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${Format.percent(fraction)} · weight $weight", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun CompareRow(label: String, value: String, delta: String?) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
        if (delta != null) {
            Text(
                delta,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (delta.startsWith("+")) Color(0xFF1E8E3E) else DetourRed,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
