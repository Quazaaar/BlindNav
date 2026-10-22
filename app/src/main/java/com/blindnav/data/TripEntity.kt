package com.blindnav.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.blindnav.geo.GeoPoint
import com.blindnav.scoring.Grade

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val destName: String,
    val destAddress: String?,
    val destPlaceId: String?,
    val destLat: Double,
    val destLng: Double,
    val memorizeSeconds: Int,
    val multiplier: Double,
    /** Encoded Routes polyline; cleared after 30 days (Maps Platform caching terms). */
    val optimalPolyline: String?,
    val optimalDistanceM: Int,
    val optimalDurationS: Double,
    val trackPolyline: String,
    val distanceM: Double,
    val durationS: Double,
    val timeScore: Double,
    val distanceScore: Double,
    val routeMatch: Double,
    val detourCount: Int,
    val base: Int,
    val points: Int,
    val grade: Grade,
) {
    val destination get() = GeoPoint(destLat, destLng)
}
