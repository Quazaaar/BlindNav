package com.blindnav.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test
    fun milesAndFeetInUsAndUk() {
        assertEquals("2.0 mi", Format.distance(3218.7, Locale.US))
        assertEquals("450 ft", Format.distance(137.0, Locale.US))
        assertEquals("1.0 mi", Format.distance(1609.344, Locale.UK))
    }

    @Test
    fun kilometresAndMetresElsewhere() {
        assertEquals("2,5 km", Format.distance(2500.0, Locale.GERMANY))
        assertEquals("450 m", Format.distance(449.0, Locale.GERMANY))
    }

    @Test
    fun durationsAndDeltas() {
        assertEquals("0:59", Format.duration(59.4))
        assertEquals("16:40", Format.duration(1000.0))
        assertEquals("1:02:05", Format.duration(3725.0))
        assertEquals("+1:22", Format.durationDelta(82.0))
        assertEquals("−0:15", Format.durationDelta(-15.0))
    }
}
