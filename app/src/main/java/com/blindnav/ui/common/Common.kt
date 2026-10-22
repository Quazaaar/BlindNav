package com.blindnav.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blindnav.AppContainer
import com.blindnav.BlindNavApp
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.toLatLng
import com.blindnav.scoring.Grade
import com.blindnav.ui.theme.Blue
import com.blindnav.ui.theme.RouteOutline
import com.blindnav.ui.theme.SecondaryText
import com.blindnav.ui.theme.color
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Polyline

/** ViewModel built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM =
    viewModel(factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as BlindNavApp).container, createSavedStateHandle()) }
    })

fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasPreciseLocation() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.hasAnyLocation() = hasPreciseLocation() || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.isLocationOn() = LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

fun Context.openAppSettings() = startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
)

fun Context.openLocationSettings() = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

@Composable
fun GradeBadge(grade: Grade, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).background(grade.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(grade.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp)
    }
}

/** Required when Places names are shown away from a Google map. */
@Composable
fun GoogleAttribution(modifier: Modifier = Modifier) {
    Text("Google Maps", modifier, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
}

/** Google-style route line: blue with a darker outline. */
@Composable
fun RouteLine(points: List<GeoPoint>) {
    val latLngs = points.map { it.toLatLng() }
    Polyline(points = latLngs, color = RouteOutline, width = 18f, zIndex = 0f)
    Polyline(points = latLngs, color = Blue, width = 12f, zIndex = 1f)
}

fun boundsOf(points: List<GeoPoint>): LatLngBounds =
    LatLngBounds.builder().apply { points.forEach { include(it.toLatLng()) } }.build()
