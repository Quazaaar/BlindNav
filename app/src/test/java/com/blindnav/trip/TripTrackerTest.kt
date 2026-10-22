package com.blindnav.trip

import com.blindnav.ORIGIN
import com.blindnav.geo.GeoPoint
import com.blindnav.offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.hypot

class TripTrackerTest {
    private val destination = offset(eastM = 2000.0)
    private var clock = 0.0
    private var east = 0.0
    private var north = 0.0

    private fun tracker(routeEnd: GeoPoint = destination) = TripTracker(ORIGIN, destination, routeEnd)

    private fun fix(eastM: Double, t: Double, northM: Double = 0.0, accuracy: Float? = 5f, speed: Float? = null) =
        offset(northM = northM, eastM = eastM).let { Fix(it.lat, it.lng, accuracy, speed, (t * 1e9).toLong()) }

    /** Clock started at t=1 s with fixes 30 m and 50 m east of the origin. */
    private fun TripTracker.started() = apply {
        onFix(fix(30.0, 1.0))
        onFix(fix(50.0, 2.0))
        clock = 2.0
        east = 50.0
    }

    /** One fix per second in a straight line at [speedMps]; [reported] is the fix's own speed field. */
    private fun TripTracker.driveTo(
        toEast: Double,
        toNorth: Double = 0.0,
        speedMps: Double = 12.0,
        reported: Float? = speedMps.toFloat(),
    ): List<TrackerEvent> {
        val (e0, n0) = east to north
        val steps = ceil(hypot(toEast - e0, toNorth - n0) / speedMps).toInt()
        return (1..steps).map { k ->
            val f = k.toDouble() / steps
            east = e0 + (toEast - e0) * f
            north = n0 + (toNorth - n0) * f
            clock += 1.0
            onFix(fix(east, clock, northM = north, speed = reported))
        }
    }

    @Test
    fun rejectsInaccurateFixes() {
        val t = tracker()
        t.onFix(fix(100.0, 1.0, accuracy = 40f))
        t.onFix(fix(110.0, 2.0, accuracy = null))
        t.onFix(fix(120.0, 3.0, accuracy = 40f))
        assertNull(t.movementStartNanos)
    }

    @Test
    fun clockStartsAtFirstOfTwoFixesBeyond25m() {
        val t = tracker()
        assertEquals(TrackerEvent.None, t.onFix(fix(30.0, 1.0)))
        assertEquals(TrackerEvent.None, t.onFix(fix(10.0, 2.0))) // back inside: resets
        assertEquals(TrackerEvent.None, t.onFix(fix(30.0, 3.0)))
        assertEquals(TrackerEvent.MovementStarted(3_000_000_000), t.onFix(fix(45.0, 4.0)))
    }

    @Test
    fun stationaryJitterNeverStartsClock() {
        val t = tracker()
        repeat(60) { i ->
            t.onFix(fix(if (i % 2 == 0) 18.0 else -15.0, i + 1.0, northM = if (i % 3 == 0) 10.0 else -8.0))
        }
        assertNull(t.movementStartNanos)
        assertEquals(0.0, t.distanceM, 0.0)
    }

    @Test
    fun rejectsSpikes() {
        val t = tracker().started()
        t.onFix(fix(550.0, 3.0)) // 500 m in 1 s
        assertEquals(50.0, t.distanceM, 0.5)
        t.onFix(fix(65.0, 4.0))
        assertEquals(65.0, t.distanceM, 0.5)
    }

    @Test
    fun smallStepsAreNotAppended() {
        val t = tracker().started()
        t.onFix(fix(55.0, 3.0))
        assertEquals(50.0, t.distanceM, 0.5)
        t.onFix(fix(62.0, 4.0))
        assertEquals(62.0, t.distanceM, 0.5)
    }

    @Test
    fun arrivesOnlyWhenSlow() {
        val t = tracker().started()
        assertTrue(t.driveTo(1940.0, speedMps = 15.0).none { it is TrackerEvent.Arrived })
        assertTrue(t.driveTo(1943.0, speedMps = 3.0).last() is TrackerEvent.Arrived)
    }

    @Test
    fun arrivesNearRouteEndWhenDestinationIsOffRoad() {
        val t = tracker(routeEnd = offset(eastM = 1850.0)).started()
        assertTrue(t.driveTo(1790.0, speedMps = 5.0).any { it is TrackerEvent.Arrived })
    }

    @Test
    fun derivesSpeedWhenFixHasNone() {
        val t = tracker().started()
        assertTrue(t.driveTo(1960.0, speedMps = 20.0, reported = null).none { it is TrackerEvent.Arrived })
        assertTrue(t.driveTo(1990.0, speedMps = 2.0, reported = 0f).any { it is TrackerEvent.Arrived })
    }

    @Test
    fun noArrivalBeforeMovement() {
        val t = tracker()
        assertEquals(TrackerEvent.None, t.onFix(fix(1990.0, 1.0, speed = 0f)))
        assertNull(t.arrivedAtNanos)
    }

    @Test
    fun manualFinishTruncatesAtClosestApproach() {
        val t = tracker().started()
        t.driveTo(1920.0) // 80 m short at 12 m/s: never auto-arrives
        val closestAt = clock
        t.driveTo(1920.0, toNorth = 200.0) // ~215 m away: still inside the finish zone
        assertTrue(t.canFinishManually)

        val outcome = requireNotNull(t.outcome())
        assertEquals(offset(eastM = 1920.0), outcome.track.last())
        assertEquals(closestAt - 1.0, outcome.durationS, 1e-6)
        assertEquals(1920.0, outcome.distanceM, 1.0)

        t.driveTo(1920.0, toNorth = 300.0)
        assertFalse(t.canFinishManually)
    }
}
