package com.blindnav.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindnav.data.TripEntity
import com.blindnav.ui.common.Format
import com.blindnav.ui.common.GoogleAttribution
import com.blindnav.ui.common.GradeBadge
import com.blindnav.ui.common.appViewModel
import com.blindnav.ui.theme.SecondaryText
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { container, _ -> HistoryViewModel(container) }
    val trips by vm.trips.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your trips") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = trips ?: return@Scaffold
        if (list.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp)
            ) {
                Text(
                    "No trips yet. Pick a destination on the map, memorize the route, and drive it blind.",
                    textAlign = TextAlign.Center,
                    color = SecondaryText,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            item { Summary(list) }
            items(list, key = { it.id }) { trip ->
                TripRow(trip) { onOpen(trip.id) }
                HorizontalDivider()
            }
            item { GoogleAttribution(Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun Summary(trips: List<TripEntity>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Stat("Trips", trips.size.toString(), Modifier.weight(1f))
        Stat("Avg points", Format.points(trips.map { it.points }.average().roundToInt()), Modifier.weight(1f))
        Stat("Best grade", trips.minOf { it.grade }.name, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
    }
}

@Composable
private fun TripRow(trip: TripEntity, onClick: () -> Unit) {
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(trip.startedAtMs))
    ListItem(
        leadingContent = { GradeBadge(trip.grade) },
        headlineContent = { Text(trip.destName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("$date · ${Format.duration(trip.durationS)} · ${Format.distance(trip.distanceM)}", color = SecondaryText)
        },
        trailingContent = { Text("${Format.points(trip.points)} pts", fontWeight = FontWeight.Bold) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
