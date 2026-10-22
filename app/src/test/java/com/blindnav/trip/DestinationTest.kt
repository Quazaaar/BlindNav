package com.blindnav.trip

import com.blindnav.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DestinationTest {
    private val point = GeoPoint(37.7764, -122.4346)
    private val base = "https://www.google.com/maps/dir/?api=1&travelmode=driving&dir_action=navigate"

    @Test
    fun placesNavigateByNameAndPlaceId() {
        val url = Destination("Alamo Square Park", null, "ChIJ5RdoUKWAhYARFfoSJGdFS5c", point).navigationUrl()
        assertEquals("$base&destination=Alamo+Square+Park&destination_place_id=ChIJ5RdoUKWAhYARFfoSJGdFS5c", url)
    }

    @Test
    fun pinsNavigateByCoordinates() {
        val url = Destination("Dropped pin", null, null, point).navigationUrl()
        assertEquals("$base&destination=37.776400%2C-122.434600", url)
    }

    @Test
    fun coordinatesIgnoreDeviceLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY) // decimal comma
        try {
            assertTrue(Destination("Pin", null, null, point).navigationUrl().endsWith("destination=37.776400%2C-122.434600"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
