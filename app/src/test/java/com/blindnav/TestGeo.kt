package com.blindnav

import com.blindnav.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

val ORIGIN = GeoPoint(37.0, -122.0)

private const val M_PER_DEG_LAT = 111_194.93

/** The point [northM]/[eastM] metres from [base] (flat-earth, fine at test scales). */
fun offset(base: GeoPoint = ORIGIN, northM: Double = 0.0, eastM: Double = 0.0) = GeoPoint(
    base.lat + northM / M_PER_DEG_LAT,
    base.lng + eastM / (M_PER_DEG_LAT * cos(Math.toRadians(base.lat))),
)

/** Points every ~[stepM] from (e1, n1) to (e2, n2), in metres east/north of [ORIGIN], both ends included. */
fun line(e1: Double, n1: Double, e2: Double, n2: Double, stepM: Double = 10.0): List<GeoPoint> {
    val steps = max(1, Math.round(hypot(e2 - e1, n2 - n1) / stepM).toInt())
    return (0..steps).map { k ->
        val f = k.toDouble() / steps
        offset(northM = n1 + (n2 - n1) * f, eastM = e1 + (e2 - e1) * f)
    }
}

/** Straight path due east of [ORIGIN]. */
fun eastLine(lengthM: Double, stepM: Double = 10.0, northM: Double = 0.0) = line(0.0, northM, lengthM, northM, stepM)
