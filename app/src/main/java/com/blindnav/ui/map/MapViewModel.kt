package com.blindnav.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindnav.AppContainer
import com.blindnav.data.TripEntity
import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import com.blindnav.places.Prediction
import com.blindnav.scoring.MemorizePreset
import com.blindnav.trip.Destination
import com.blindnav.trip.TripSession
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapViewModel(private val container: AppContainer) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var predictions by mutableStateOf<List<Prediction>>(emptyList())
        private set
    var selected by mutableStateOf<Destination?>(null)
        private set
    var bestHere by mutableStateOf<TripEntity?>(null)
        private set
    var myLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var preset by mutableStateOf(MemorizePreset.S30)
    var message by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

    /** Straight-line metres to the selected destination, when both ends are known. */
    val distanceToSelected: Double?
        get() = selected?.let { dest -> myLocation?.let { GeoMath.distanceM(it, dest.point) } }

    val isTooClose: Boolean get() = (distanceToSelected ?: Double.MAX_VALUE) < TripSession.MIN_TRIP_M

    fun refreshLocation(onFound: (GeoPoint) -> Unit = {}) {
        viewModelScope.launch {
            container.location.position()?.let {
                myLocation = it
                onFound(it)
            }
        }
    }

    fun onQueryChange(text: String) {
        query = text
        searchJob?.cancel()
        if (text.trim().length < 2) {
            predictions = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            predictions = try {
                container.places.autocomplete(text.trim(), myLocation)
            } catch (e: ApiException) {
                message = "Search failed: ${e.statusCode}"
                emptyList()
            }
        }
    }

    fun choose(prediction: Prediction) {
        searchJob?.cancel()
        predictions = emptyList()
        query = prediction.name
        viewModelScope.launch {
            try {
                select(container.places.resolve(prediction))
            } catch (e: ApiException) {
                message = "Couldn't load that place"
            }
        }
    }

    fun choosePoi(placeId: String, name: String, point: GeoPoint) {
        query = name
        predictions = emptyList()
        select(Destination(name, null, placeId, point))
    }

    fun dropPin(point: GeoPoint) {
        query = ""
        predictions = emptyList()
        select(Destination("Dropped pin", null, null, point))
        viewModelScope.launch {
            val address = container.places.addressOf(point) ?: return@launch
            if (selected?.point == point) selected = selected?.copy(address = address)
        }
    }

    fun clear() {
        searchJob?.cancel()
        query = ""
        predictions = emptyList()
        selected = null
        bestHere = null
    }

    fun start(): Boolean {
        val destination = selected ?: return false
        container.session.start(destination, preset)
        return true
    }

    /** Re-read after returning from a trip, which may have set a new best. */
    fun refreshBest() {
        val destination = selected ?: return
        viewModelScope.launch { bestHere = container.trips.bestAt(destination.placeId, destination.point) }
    }

    private fun select(destination: Destination) {
        selected = destination
        bestHere = null
        refreshBest()
        refreshLocation() // keeps the distance and the 300 m rule current
    }
}
