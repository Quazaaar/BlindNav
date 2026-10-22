package com.blindnav.geo

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Haversine great-circle distance. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))
    }

    fun lengthM(path: List<GeoPoint>): Double = path.zipWithNext(::distanceM).sum()

    /** Inserts evenly spaced points so no gap exceeds [maxSpacingM]; original vertices are kept. */
    fun densify(path: List<GeoPoint>, maxSpacingM: Double): List<GeoPoint> {
        if (path.size < 2) return path
        val out = ArrayList<GeoPoint>(path.size)
        out += path[0]
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val n = ceil(distanceM(a, b) / maxSpacingM).toInt()
            for (k in 1 until n) {
                val f = k.toDouble() / n
                out += GeoPoint(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f)
            }
            out += b
        }
        return out
    }
}

/** Equirectangular projection to metres around [origin]; error < 0.5% within ~100 km. */
class LocalFrame(origin: GeoPoint) {
    private val lat0 = origin.lat
    private val lng0 = origin.lng
    private val mPerDegLat = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_M
    private val mPerDegLng = mPerDegLat * cos(Math.toRadians(origin.lat))

    fun x(p: GeoPoint) = (p.lng - lng0) * mPerDegLng
    fun y(p: GeoPoint) = (p.lat - lat0) * mPerDegLat
}

/** Answers "is this point within t metres of the polyline?" with planar math and a bounding-box early-out. */
class PolylineIndex(path: List<GeoPoint>, private val frame: LocalFrame) {
    private val xs = DoubleArray(path.size) { frame.x(path[it]) }
    private val ys = DoubleArray(path.size) { frame.y(path[it]) }

    fun isWithin(p: GeoPoint, toleranceM: Double): Boolean {
        val px = frame.x(p)
        val py = frame.y(p)
        if (xs.size == 1) return hypot(px - xs[0], py - ys[0]) <= toleranceM
        for (i in 0 until xs.size - 1) {
            val ax = xs[i]
            val ay = ys[i]
            val bx = xs[i + 1]
            val by = ys[i + 1]
            if (px < min(ax, bx) - toleranceM || px > max(ax, bx) + toleranceM ||
                py < min(ay, by) - toleranceM || py > max(ay, by) + toleranceM
            ) continue
            if (segmentDistance(px, py, ax, ay, bx, by) <= toleranceM) return true
        }
        return false
    }
}

/** Distance from point p to segment ab, all in planar metres. */
internal fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
