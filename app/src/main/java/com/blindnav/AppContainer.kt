package com.blindnav

import android.app.Application
import androidx.room.Room
import com.blindnav.data.BlindNavDatabase
import com.blindnav.data.TripRepository
import com.blindnav.location.LocationClient
import com.blindnav.places.PlacesRepository
import com.blindnav.routes.RoutesClient
import com.blindnav.trip.TripSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency wiring; one instance per process, owned by [BlindNavApp]. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val trips = TripRepository(
        Room.databaseBuilder(app, BlindNavDatabase::class.java, "blindnav.db").build().trips()
    )
    val location = LocationClient(app)
    val places = PlacesRepository(app)
    private val routes = RoutesClient(BuildConfig.MAPS_API_KEY, app.packageName, RoutesClient.signingCertSha1(app))
    val session = TripSession(app, routes, location, trips, appScope)
}
