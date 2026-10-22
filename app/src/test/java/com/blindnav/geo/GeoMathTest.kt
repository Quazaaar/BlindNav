package com.blindnav.geo

import com.blindnav.ORIGIN
import com.blindnav.eastLine
import com.blindnav.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun oneDegreeOfLatitudeIsAbout111km() {
        assertEquals(111_195.0, GeoMath.distanceM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1.0)
    }

    @Test
    fun segmentDistanceToPerpendicularFoot() {
        assertEquals(5.0, segmentDistance(5.0, 5.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
    }

    @Test
    fun segmentDistanceBeyondEndpoint() {
        assertEquals(5.0, segmentDistance(13.0, 4.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
    }

    @Test
    fun densifyKeepsVerticesAndSpacing() {
        val path = listOf(ORIGIN, offset(eastM = 100.0), offset(eastM = 100.0, northM = 60.0))
        val dense = GeoMath.densify(path, 25.0)
        assertTrue(dense.containsAll(path))
        assertEquals(path.last(), dense.last())
        dense.zipWithNext().forEach { (a, b) -> assertTrue(GeoMath.distanceM(a, b) <= 25.0 + 1e-6) }
        assertEquals(GeoMath.lengthM(path), GeoMath.lengthM(dense), 0.01)
    }

    @Test
    fun polylineIndexRespectsTolerance() {
        val index = PolylineIndex(eastLine(1000.0, stepM = 500.0), LocalFrame(ORIGIN))
        assertTrue(index.isWithin(offset(northM = 39.0, eastM = 250.0), 40.0))
        assertFalse(index.isWithin(offset(northM = 41.0, eastM = 250.0), 40.0))
        assertFalse(index.isWithin(offset(eastM = 1045.0), 40.0))
    }
}
