package com.blindnav.ui.results

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindnav.AppContainer
import com.blindnav.data.Comparisons
import com.blindnav.data.TripEntity
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.PolylineCodec
import com.blindnav.scoring.ScoreCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultsUi(
    val trip: TripEntity,
    val comparisons: Comparisons,
    /** Null once the 30-day cache window has passed. */
    val optimal: List<GeoPoint>?,
    val track: List<GeoPoint>,
    val detours: List<List<GeoPoint>>,
)

class ResultsViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    var ui by mutableStateOf<ResultsUi?>(null)
        private set

    init {
        container.session.acknowledge()
        val tripId = checkNotNull(handle.get<Long>("tripId"))
        viewModelScope.launch {
            val trip = container.trips.get(tripId) ?: return@launch
            val comparisons = container.trips.comparisons(trip)
            ui = withContext(Dispatchers.Default) {
                val track = PolylineCodec.decode(trip.trackPolyline)
                val optimal = trip.optimalPolyline?.let(PolylineCodec::decode)
                ResultsUi(trip, comparisons, optimal, track, optimal?.let { ScoreCalculator.detours(it, track) }.orEmpty())
            }
        }
    }
}
