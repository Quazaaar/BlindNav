package com.blindnav.scoring

import com.blindnav.eastLine
import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import com.blindnav.line
import com.blindnav.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ScoreCalculatorTest {
    /** Optimal route: 3 km due east, 300 s. */
    private val route = eastLine(3000.0, stepM = 100.0)

    private fun input(track: List<GeoPoint>, multiplier: Double = 1.0) =
        ScoreInput(route, 300.0, 3000.0, track, durationS = 300.0, distanceM = 3000.0, multiplier = multiplier)

    @Test
    fun perfectRunIsS() {
        val score = ScoreCalculator.score(input(eastLine(3000.0), MemorizePreset.S10.multiplier))
        assertEquals(1000, score.base)
        assertEquals(Grade.S, score.grade)
        assertEquals(1500, score.points)
        assertEquals(0, score.detourCount)
    }

    @Test
    fun timeIsCappedAtEta() {
        assertEquals(1.0, ScoreCalculator.timeScore(300.0, 200.0), 0.0)
        assertEquals(0.5, ScoreCalculator.timeScore(300.0, 600.0), 1e-9)
    }

    @Test
    fun extraDistanceScalesDistanceScore() {
        assertEquals(1.0, ScoreCalculator.distanceScore(3000.0, 2900.0), 0.0)
        assertEquals(0.75, ScoreCalculator.distanceScore(3000.0, 4000.0), 1e-9)
    }

    @Test
    fun baseUsesWeights() {
        // On-route at 1.1x ETA: the plan's sanity example.
        assertEquals(956, ScoreCalculator.base(1 / 1.1, 0.99, 0.97))
    }

    @Test
    fun thirtyMetreOffsetStillMatches() {
        val track = eastLine(3000.0, northM = 30.0)
        assertEquals(1.0, ScoreCalculator.routeMatch(route, track), 1e-9)
        assertTrue(ScoreCalculator.detours(route, track).isEmpty())
    }

    @Test
    fun sixtyMetreOffsetIsOneLongDetour() {
        val track = eastLine(3000.0, northM = 60.0)
        assertTrue(ScoreCalculator.routeMatch(route, track) < 0.05)
        assertEquals(1, ScoreCalculator.detours(route, track).size)
    }

    @Test
    fun shortExcursionIsNotADetour() {
        // 60 m of track 80 m off the route.
        val track = eastLine(1490.0) + line(1500.0, 80.0, 1560.0, 80.0) + line(1570.0, 0.0, 3000.0, 0.0)
        assertTrue(ScoreCalculator.detours(route, track).isEmpty())
    }

    @Test
    fun loopOffRouteIsADetour() {
        val track = eastLine(1000.0) + line(1000.0, 0.0, 1000.0, 100.0) + line(1000.0, 100.0, 1100.0, 100.0) +
            line(1100.0, 100.0, 1100.0, 0.0) + line(1100.0, 0.0, 3000.0, 0.0)
        val detours = ScoreCalculator.detours(route, track)
        assertEquals(1, detours.size)
        // Drawn path joins the route on both sides.
        assertTrue(GeoMath.distanceM(detours[0].first(), offset(eastM = 1000.0)) <= 50.0 + 1e-6)
        assertTrue(GeoMath.distanceM(detours[0].last(), offset(eastM = 1100.0)) <= 50.0 + 1e-6)
    }

    @Test
    fun differentRoadForMiddleThird() {
        val track = eastLine(1000.0) + line(1000.0, 0.0, 1000.0, 200.0) + line(1000.0, 200.0, 2000.0, 200.0) +
            line(2000.0, 200.0, 2000.0, 0.0) + line(2000.0, 0.0, 3000.0, 0.0)
        val match = ScoreCalculator.routeMatch(route, track)
        assertTrue("match=$match", match in 0.66..0.72)
        assertEquals(1, ScoreCalculator.detours(route, track).size)
    }

    @Test
    fun gpsNoiseStillMatches() {
        val random = Random(42)
        val track = eastLine(3000.0).map { offset(it, northM = random.nextDouble() * 16 - 8) }
        assertEquals(1.0, ScoreCalculator.routeMatch(route, track), 1e-9)
        assertTrue(ScoreCalculator.detours(route, track).isEmpty())
    }

    @Test
    fun gradeBoundaries() {
        mapOf(
            950 to Grade.S, 949 to Grade.A, 880 to Grade.A, 879 to Grade.B, 780 to Grade.B, 779 to Grade.C,
            650 to Grade.C, 649 to Grade.D, 500 to Grade.D, 499 to Grade.F, 0 to Grade.F,
        ).forEach { (base, grade) -> assertEquals("base=$base", grade, Grade.fromBase(base)) }
    }
}
