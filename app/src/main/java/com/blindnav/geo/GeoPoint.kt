package com.blindnav.geo

import com.google.android.gms.maps.model.LatLng

/** Plain lat/lng so scoring and tracking stay free of Play-services types (pure JVM tests). */
data class GeoPoint(val lat: Double, val lng: Double)

fun GeoPoint.toLatLng() = LatLng(lat, lng)

fun LatLng.toGeo() = GeoPoint(latitude, longitude)
