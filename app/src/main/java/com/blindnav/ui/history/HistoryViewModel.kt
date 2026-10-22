package com.blindnav.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindnav.AppContainer
import com.blindnav.data.TripEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(container: AppContainer) : ViewModel() {
    /** Newest first; null while loading. */
    val trips: StateFlow<List<TripEntity>?> = container.trips.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
