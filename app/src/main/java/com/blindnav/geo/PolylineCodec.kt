package com.blindnav.geo

import kotlin.math.roundToLong

/** Google encoded polyline format, 1e5 precision (what the Routes API returns). */
object PolylineCodec {

    fun decode(encoded: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var i = 0
        var lat = 0
        var lng = 0
        fun next(): Int {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        while (i < encoded.length) {
            lat += next()
            lng += next()
            out += GeoPoint(lat / 1e5, lng / 1e5)
        }
        return out
    }

    fun encode(path: List<GeoPoint>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for (p in path) {
            val lat = (p.lat * 1e5).roundToLong()
            val lng = (p.lng * 1e5).roundToLong()
            encodeValue(lat - prevLat, sb)
            encodeValue(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }
}
