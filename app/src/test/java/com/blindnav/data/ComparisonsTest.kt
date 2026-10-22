package com.blindnav.data

import com.blindnav.ORIGIN
import com.blindnav.geo.GeoPoint
import com.blindnav.offset
import com.blindnav.scoring.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonsTest {

    private fun trip(id: Long, points: Int, placeId: String? = "P1", dest: GeoPoint = ORIGIN) = TripEntity(
        id = id, startedAtMs = id * 1000, destName = "Somewhere", destAddress = null, destPlaceId = placeId,
        destLat = dest.lat, destLng = dest.lng, memorizeSeconds = 30, multiplier = 1.0, optimalPolyline = null,
        optimalDistanceM = 1000, optimalDurationS = 100.0, trackPolyline = "", distanceM = 1000.0, durationS = 100.0,
        timeScore = 1.0, distanceScore = 1.0, routeMatch = 1.0, detourCount = 0,
        base = points, points = points, grade = Grade.fromBase(points),
    )

    @Test
    fun firstTripHasNothingToCompare() {
        val first = trip(1, 900)
        val result = TripRepository.compare(first, listOf(first))
        assertNull(result.previousBest)
        assertNull(result.averagePoints)
        assertFalse(result.isNewBest)
    }

    @Test
    fun comparesOnlyAgainstEarlierTrips() {
        val all = listOf(trip(1, 800), trip(2, 900, placeId = "P2"), trip(3, 850), trip(4, 870), trip(5, 990))
        val result = TripRepository.compare(all[3], all)
        assertEquals(3L, result.previousBest?.id) // best earlier trip to P1; trip 5 is later
        assertEquals(850, result.averagePoints) // (800 + 900 + 850) / 3
        assertTrue(result.isNewBest)
    }

    @Test
    fun lowerScoreIsNotANewBest() {
        val all = listOf(trip(1, 950), trip(2, 700))
        assertFalse(TripRepository.compare(all[1], all).isNewBest)
    }

    @Test
    fun pinsMatchWithin150m() {
        val near = trip(1, 800, placeId = null, dest = offset(eastM = 100.0))
        val far = trip(2, 950, placeId = null, dest = offset(eastM = 200.0))
        val current = trip(3, 820, placeId = null)
        assertEquals(1L, TripRepository.compare(current, listOf(near, far, current)).previousBest?.id)
    }

    @Test
    fun differentPlaceIdsNeverMatchEvenIfClose() {
        val neighbour = trip(1, 800, placeId = "P2", dest = offset(eastM = 20.0))
        val current = trip(2, 820, placeId = "P1")
        assertNull(TripRepository.compare(current, listOf(neighbour, current)).previousBest)
    }
}
