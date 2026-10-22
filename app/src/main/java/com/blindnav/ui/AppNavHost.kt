package com.blindnav.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blindnav.BlindNavApp
import com.blindnav.trip.TripState
import com.blindnav.ui.history.HistoryScreen
import com.blindnav.ui.map.MapScreen
import com.blindnav.ui.results.ResultsScreen
import com.blindnav.ui.trip.TripScreen

private const val MAP = "map"
private const val TRIP = "trip"
private const val HISTORY = "history"

private fun NavController.openResults(tripId: Long) = navigate("results/$tripId") {
    popUpTo(MAP)
    launchSingleTop = true
}

private fun NavController.backToMap() = popBackStack(MAP, inclusive = false)

/** [openTripId] comes from tapping the "arrived" notification. */
@Composable
fun AppNavHost(openTripId: Long?, onTripOpened: () -> Unit) {
    val nav = rememberNavController()
    val session = (LocalContext.current.applicationContext as BlindNavApp).container.session

    NavHost(nav, startDestination = MAP) {
        composable(MAP) {
            MapScreen(onTripStarted = { nav.navigate(TRIP) }, onHistory = { nav.navigate(HISTORY) })
        }
        composable(TRIP) {
            TripScreen(onFinished = nav::openResults, onExit = { nav.backToMap() })
        }
        composable("results/{tripId}", arguments = listOf(navArgument("tripId") { type = NavType.LongType })) {
            ResultsScreen(onDone = { nav.backToMap() }, onHistory = { nav.navigate(HISTORY) { popUpTo(MAP) } })
        }
        composable(HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("results/$it") })
        }
    }

    // Activity relaunched (e.g. swiped from recents) while the tracking service kept the trip alive.
    LaunchedEffect(Unit) {
        if (session.state.value != TripState.Idle && openTripId == null && nav.currentDestination?.route == MAP) {
            nav.navigate(TRIP)
        }
    }
    LaunchedEffect(openTripId) {
        if (openTripId != null) {
            nav.openResults(openTripId)
            onTripOpened()
        }
    }
}
