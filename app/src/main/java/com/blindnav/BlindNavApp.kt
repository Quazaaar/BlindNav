package com.blindnav

import android.app.Application
import com.blindnav.trip.TripNotifications
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.launch

class BlindNavApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.MAPS_API_KEY)
        }
        TripNotifications.createChannel(this)
        container = AppContainer(this)
        container.appScope.launch { container.trips.purgeExpiredRoutes(System.currentTimeMillis()) }
    }
}
