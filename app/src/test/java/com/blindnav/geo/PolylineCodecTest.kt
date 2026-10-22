package com.blindnav.geo

import com.blindnav.eastLine
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineCodecTest {
    // Example from Google's "Encoded Polyline Algorithm Format" documentation.
    private val reference = listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453))
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun decodesReference() {
        val decoded = PolylineCodec.decode(encoded)
        assertEquals(reference.size, decoded.size)
        decoded.zip(reference).forEach { (a, b) ->
            assertEquals(b.lat, a.lat, 1e-9)
            assertEquals(b.lng, a.lng, 1e-9)
        }
    }

    @Test
    fun encodesReference() {
        assertEquals(encoded, PolylineCodec.encode(reference))
    }

    @Test
    fun roundTripKeepsFivePlaces() {
        val path = eastLine(2000.0, stepM = 37.0, northM = 12.0)
        PolylineCodec.decode(PolylineCodec.encode(path)).zip(path).forEach { (a, b) ->
            assertEquals(b.lat, a.lat, 0.6e-5)
            assertEquals(b.lng, a.lng, 0.6e-5)
        }
    }
}
