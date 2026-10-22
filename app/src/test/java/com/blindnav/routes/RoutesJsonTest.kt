package com.blindnav.routes

import com.blindnav.geo.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesJsonTest {
    private val origin = GeoPoint(37.0, -122.0)
    private val destination = GeoPoint(37.01, -122.02)

    private val response = """
        {"routes": [{
          "distanceMeters": 3021,
          "duration": "412s",
          "polyline": {"encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
          "legs": [{"endLocation": {"latLng": {"latitude": 43.2521, "longitude": -126.4529}}}]
        }]}
    """.trimIndent()

    @Test
    fun parsesDurations() {
        assertEquals(165.0, RoutesClient.parseDurationS("165s"), 0.0)
        assertEquals(165.5, RoutesClient.parseDurationS("165.5s"), 0.0)
    }

    @Test
    fun parsesRoute() {
        val route = RoutesClient.parseResponse(response)
        assertEquals(3021, route.distanceM)
        assertEquals(412.0, route.durationS, 0.0)
        assertEquals(3, route.path.size)
        assertEquals(GeoPoint(43.2521, -126.4529), route.end)
    }

    @Test
    fun routeEndFallsBackToLastPolylinePoint() {
        val body = JSONObject(response).apply { getJSONArray("routes").getJSONObject(0).remove("legs") }.toString()
        assertEquals(RoutesClient.parseResponse(body).path.last(), RoutesClient.parseResponse(body).end)
    }

    @Test(expected = RoutesException::class)
    fun emptyResponseMeansNoRoute() {
        RoutesClient.parseResponse("{}")
    }

    @Test
    fun readsErrorMessage() {
        val body = """{"error": {"code": 403, "message": "API key not valid.", "status": "PERMISSION_DENIED"}}"""
        assertEquals("API key not valid.", RoutesClient.errorMessage(body))
        assertNull(RoutesClient.errorMessage("<html>"))
    }

    @Test
    fun requestUsesPlaceIdWhenPresent() {
        val json = JSONObject(RoutesClient.requestJson(origin, destination, "ChIJabc"))
        assertEquals("ChIJabc", json.getJSONObject("destination").getString("placeId"))
        assertFalse(json.getJSONObject("destination").has("location"))
        assertEquals("TRAFFIC_AWARE_OPTIMAL", json.getString("routingPreference"))
        assertEquals("DRIVE", json.getString("travelMode"))
    }

    @Test
    fun requestUsesCoordinatesForPins() {
        val json = JSONObject(RoutesClient.requestJson(origin, destination, null))
        val latLng = json.getJSONObject("destination").getJSONObject("location").getJSONObject("latLng")
        assertEquals(destination.lat, latLng.getDouble("latitude"), 0.0)
        assertEquals(destination.lng, latLng.getDouble("longitude"), 0.0)
    }
}
