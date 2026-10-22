package com.blindnav.trip

import com.blindnav.geo.GeoPoint
import java.net.URLEncoder
import java.util.Locale

/** Where a trip goes: a Places result ([placeId] set) or a dropped pin. */
data class Destination(
    val name: String,
    val address: String?,
    val placeId: String?,
    val point: GeoPoint,
) {
    /**
     * Google Maps URL that starts turn-by-turn driving directions here (Maps app, or the browser).
     * Used after giving up: live in-app navigation on Routes data would breach Maps Platform ToS 3.2.3(d)(iv).
     * Places go by name + place ID (given coordinates, the Maps app snaps to the nearest business instead).
     */
    fun navigationUrl(): String {
        val target = if (placeId != null) name else String.format(Locale.ROOT, "%.6f,%.6f", point.lat, point.lng)
        return buildString {
            append("https://www.google.com/maps/dir/?api=1&travelmode=driving&dir_action=navigate")
            append("&destination=").append(URLEncoder.encode(target, "UTF-8"))
            placeId?.let { append("&destination_place_id=").append(URLEncoder.encode(it, "UTF-8")) }
        }
    }
}
