package com.blindnav.ui.map

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blindnav.BuildConfig
import com.blindnav.R
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.toGeo
import com.blindnav.geo.toLatLng
import com.blindnav.places.Prediction
import com.blindnav.scoring.MemorizePreset
import com.blindnav.trip.TripSession
import com.blindnav.ui.common.Format
import com.blindnav.ui.common.GoogleAttribution
import com.blindnav.ui.common.GradeBadge
import com.blindnav.ui.common.appViewModel
import com.blindnav.ui.common.hasAnyLocation
import com.blindnav.ui.common.hasPreciseLocation
import com.blindnav.ui.common.isLocationOn
import com.blindnav.ui.common.openAppSettings
import com.blindnav.ui.common.openLocationSettings
import com.blindnav.ui.theme.Blue
import com.blindnav.ui.theme.SecondaryText
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

private val locationPermissions = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun MapScreen(onTripStarted: () -> Unit, onHistory: () -> Unit) {
    val vm = appViewModel { container, _ -> MapViewModel(container) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(39.5, -98.35), 3f)
    }
    var precise by remember { mutableStateOf(context.hasPreciseLocation()) }
    var anyLocation by remember { mutableStateOf(context.hasAnyLocation()) }
    var askedPermissions by rememberSaveable { mutableStateOf(false) }
    var bottomHeightPx by remember { mutableIntStateOf(0) }

    fun centerOn(point: GeoPoint) {
        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 15f)) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        precise = context.hasPreciseLocation()
        anyLocation = context.hasAnyLocation()
        if (anyLocation) vm.refreshLocation(::centerOn)
    }
    LaunchedEffect(Unit) {
        if (!precise && !askedPermissions) {
            askedPermissions = true
            permissionLauncher.launch(locationPermissions)
        } else if (anyLocation && vm.myLocation == null) {
            vm.refreshLocation { camera.move(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 15f)) }
        }
    }
    // Permissions may have been granted in Settings; after a trip we've moved and may have a new best.
    LifecycleResumeEffect(Unit) {
        precise = context.hasPreciseLocation()
        anyLocation = context.hasAnyLocation()
        vm.refreshLocation()
        vm.refreshBest()
        onPauseOrDispose { }
    }
    vm.message?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            vm.message = null
        }
    }
    // Bring a searched place into view (and zoom in if we're looking at the whole continent).
    LaunchedEffect(vm.selected?.point) {
        val target = vm.selected?.point?.toLatLng() ?: return@LaunchedEffect
        val visible = camera.projection?.visibleRegion?.latLngBounds?.contains(target) == true
        if (!visible || camera.position.zoom < 12f) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(target, 15f))
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = anyLocation),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false, // its "Directions" button would give the route away
                zoomControlsEnabled = false,
            ),
            mapColorScheme = ComposeMapColorScheme.LIGHT,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                bottom = with(density) { bottomHeightPx.toDp() },
            ),
            onMapClick = { focus.clearFocus() },
            onMapLongClick = {
                focus.clearFocus()
                vm.dropPin(it.toGeo())
            },
            onPOIClick = {
                focus.clearFocus()
                vm.choosePoi(it.placeId, it.name, it.latLng.toGeo())
            },
        ) {
            vm.selected?.let { Marker(state = rememberUpdatedMarkerState(it.point.toLatLng()), title = it.name) }
        }

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SearchBar(
                query = vm.query,
                onQueryChange = vm::onQueryChange,
                onClear = {
                    focus.clearFocus()
                    vm.clear()
                },
                onHistory = onHistory,
                onSearch = { focus.clearFocus() },
            )
            if (vm.predictions.isNotEmpty()) {
                PredictionList(vm.predictions) {
                    focus.clearFocus()
                    vm.choose(it)
                }
            }
            when {
                BuildConfig.MAPS_API_KEY.isBlank() ->
                    Banner("Add MAPS_API_KEY to local.properties and rebuild.")
                !anyLocation ->
                    Banner("Location permission is needed to play.", "Settings", context::openAppSettings)
                !precise ->
                    Banner("Precise location is needed for scoring.", "Settings", context::openAppSettings)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomHeightPx = it.height }
        ) {
            FloatingActionButton(
                onClick = {
                    if (anyLocation) vm.refreshLocation(::centerOn) else permissionLauncher.launch(locationPermissions)
                },
                shape = CircleShape,
                containerColor = Color.White,
                contentColor = Blue,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
                    .then(if (vm.selected == null) Modifier.navigationBarsPadding() else Modifier),
            ) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = "My location")
            }
            if (vm.selected != null) {
                DestinationCard(
                    vm = vm,
                    canStart = precise,
                    onStart = {
                        if (!context.isLocationOn()) {
                            vm.message = "Turn on location to play"
                            context.openLocationSettings()
                        } else if (vm.start()) {
                            onTripStarted()
                        }
                    },
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onHistory: () -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = SecondaryText)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search here", style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
                        }
                        field()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear") }
            } else {
                IconButton(onClick = onHistory) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = "Trip history", tint = Blue)
                }
            }
        }
    }
}

@Composable
private fun PredictionList(predictions: List<Prediction>, onPick: (Prediction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column {
            predictions.forEach { prediction ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(prediction) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SecondaryText)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prediction.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            prediction.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    prediction.distanceM?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(Format.distance(it.toDouble()), style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    }
                }
            }
            GoogleAttribution(
                Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun Banner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF7E0),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF29900))
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            )
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationCard(vm: MapViewModel, canStart: Boolean, onStart: () -> Unit) {
    val destination = vm.selected ?: return
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    Text(destination.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    destination.address?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    vm.distanceToSelected?.let {
                        Text(
                            "${Format.distance(it)} away in a straight line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText,
                        )
                    }
                }
                IconButton(onClick = vm::clear) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Column(Modifier.padding(end = 8.dp)) {
                vm.bestHere?.let { best ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        GradeBadge(best.grade, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Your best here: ${Format.points(best.points)} pts", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Memorize time",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MemorizePreset.entries.forEachIndexed { index, preset ->
                        SegmentedButton(
                            selected = vm.preset == preset,
                            onClick = { vm.preset = preset },
                            shape = SegmentedButtonDefaults.itemShape(index, MemorizePreset.entries.size),
                            label = { Text("${preset.seconds} s ×${preset.multiplier}") },
                        )
                    }
                }
                Button(
                    onClick = onStart,
                    enabled = canStart && !vm.isTooClose,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp),
                ) {
                    Text("Start")
                }
                if (vm.isTooClose) {
                    Text(
                        "Pick somewhere at least ${Format.distance(TripSession.MIN_TRIP_M)} away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
