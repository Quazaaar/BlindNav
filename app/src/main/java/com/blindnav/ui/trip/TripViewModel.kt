package com.blindnav.ui.trip

import androidx.lifecycle.ViewModel
import com.blindnav.AppContainer

class TripViewModel(container: AppContainer) : ViewModel() {
    private val session = container.session
    val state = session.state

    fun endMemorize() = session.endMemorize()
    fun finish() = session.finishManually()
    fun giveUp() = session.giveUp()
    fun acknowledge() = session.acknowledge()
}
