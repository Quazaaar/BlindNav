package com.blindnav.places

import android.content.Context
import android.location.Geocoder
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.toGeo
import com.blindnav.geo.toLatLng
import com.blindnav.trip.Destination
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class Prediction(val placeId: String, val name: String, val address: String, val distanceM: Int?)

/**
 * Places (New) autocomplete. One session token per search, closed by [resolve]. The name comes from the
 * prediction and the details request asks only for ID/LOCATION/FORMATTED_ADDRESS (Essentials billing tier).
 */
class PlacesRepository(private val context: Context) {
    private val client by lazy { Places.createClient(context) }
    private var token: AutocompleteSessionToken? = null

    suspend fun autocomplete(query: String, near: GeoPoint?): List<Prediction> {
        if (!Places.isInitialized()) return emptyList() // no API key configured
        val session = token ?: AutocompleteSessionToken.newInstance().also { token = it }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(session)
            .apply {
                if (near != null) {
                    setLocationBias(CircularBounds.newInstance(near.toLatLng(), 50_000.0))
                    setOrigin(near.toLatLng())
                }
            }
            .build()
        return client.findAutocompletePredictions(request).await().autocompletePredictions.map {
            Prediction(
                placeId = it.placeId,
                name = it.getPrimaryText(null).toString(),
                address = it.getSecondaryText(null).toString(),
                distanceM = it.distanceMeters,
            )
        }
    }

    suspend fun resolve(prediction: Prediction): Destination {
        val fields = listOf(Place.Field.ID, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
        val request = FetchPlaceRequest.builder(prediction.placeId, fields).setSessionToken(token).build()
        token = null
        val place = client.fetchPlace(request).await().place
        val location = checkNotNull(place.location) { "Place has no location" }
        return Destination(prediction.name, place.formattedAddress ?: prediction.address, prediction.placeId, location.toGeo())
    }

    /** Street address for a dropped pin, or null (no geocoder / offline). */
    suspend fun addressOf(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocation(point.lat, point.lng, 1)?.firstOrNull()?.getAddressLine(0)
        }.getOrNull()
    }
}
