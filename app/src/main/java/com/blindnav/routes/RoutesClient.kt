package com.blindnav.routes

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.PolylineCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class OptimalRoute(
    val encodedPolyline: String,
    val path: List<GeoPoint>,
    val durationS: Double,
    val distanceM: Int,
    /** Road-snapped end of the route; arrival also counts near it (destination pins can sit off-road). */
    val end: GeoPoint,
)

class RoutesException(message: String) : Exception(message)

/** Google Routes API `computeRoutes`, traffic-aware. One call per trip (billed as Compute Routes Pro). */
class RoutesClient(
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String,
) {
    suspend fun computeOptimalRoute(origin: GeoPoint, destination: GeoPoint, placeId: String?): OptimalRoute =
        withContext(Dispatchers.IO) {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", apiKey)
                setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
                // Required when the key is restricted to this Android app.
                setRequestProperty("X-Android-Package", packageName)
                setRequestProperty("X-Android-Cert", certSha1)
            }
            try {
                connection.outputStream.use { it.write(requestJson(origin, destination, placeId).toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw RoutesException(errorMessage(body) ?: "Routes API error $code")
                parseResponse(body)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.endLocation"

        fun requestJson(origin: GeoPoint, destination: GeoPoint, placeId: String?): String = JSONObject()
            .put("origin", waypoint(origin))
            // A place ID lets Google route to the right entrance.
            .put("destination", placeId?.let { JSONObject().put("placeId", it) } ?: waypoint(destination))
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_AWARE_OPTIMAL")
            .put("polylineQuality", "HIGH_QUALITY")
            .put("computeAlternativeRoutes", false)
            .toString()

        private fun waypoint(p: GeoPoint) = JSONObject().put(
            "location",
            JSONObject().put("latLng", JSONObject().put("latitude", p.lat).put("longitude", p.lng)),
        )

        fun parseResponse(body: String): OptimalRoute {
            val route = JSONObject(body).optJSONArray("routes")?.optJSONObject(0)
                ?: throw RoutesException("No driving route found")
            val encoded = route.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            val path = PolylineCodec.decode(encoded)
            if (path.size < 2) throw RoutesException("No driving route found")
            val legs = route.optJSONArray("legs")
            val end = legs?.optJSONObject(legs.length() - 1)
                ?.optJSONObject("endLocation")?.optJSONObject("latLng")
                ?.let { GeoPoint(it.getDouble("latitude"), it.getDouble("longitude")) }
                ?: path.last()
            return OptimalRoute(
                encodedPolyline = encoded,
                path = path,
                durationS = parseDurationS(route.getString("duration")),
                distanceM = route.getInt("distanceMeters"),
                end = end,
            )
        }

        /** Durations come back as strings like "165s". */
        fun parseDurationS(duration: String): Double = duration.removeSuffix("s").toDouble()

        fun errorMessage(body: String): String? =
            runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()

        /** Signing-certificate SHA-1 as uppercase hex without separators, for the X-Android-Cert header. */
        @Suppress("DEPRECATION")
        fun signingCertSha1(context: Context): String {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert = signatures?.firstOrNull() ?: return ""
            return MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
        }
    }
}
