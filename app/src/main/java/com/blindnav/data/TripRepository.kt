package com.blindnav.data

import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/** How a trip stacks up against earlier trips (local history only). */
data class Comparisons(
    /** Best earlier trip to the same destination, or null on a first run there. */
    val previousBest: TripEntity?,
    /** Average points over all earlier trips, or null if this is the first trip. */
    val averagePoints: Int?,
    val isNewBest: Boolean,
)

class TripRepository(private val dao: TripDao) {

    suspend fun save(trip: TripEntity): Long = dao.insert(trip)

    suspend fun get(id: Long): TripEntity? = dao.get(id)

    fun observeHistory(): Flow<List<TripEntity>> = dao.observeAll()

    suspend fun comparisons(trip: TripEntity): Comparisons = compare(trip, dao.all())

    suspend fun bestAt(placeId: String?, point: GeoPoint): TripEntity? =
        dao.all().filter { sameDestination(it, placeId, point) }.maxByOrNull { it.points }

    /** Maps Platform terms allow caching route coordinates for at most 30 days. */
    suspend fun purgeExpiredRoutes(nowMs: Long) = dao.clearOptimalRoutesBefore(nowMs - 30L * 24 * 60 * 60 * 1000)

    companion object {
        private const val SAME_PLACE_RADIUS_M = 150.0

        fun compare(trip: TripEntity, all: List<TripEntity>): Comparisons {
            val earlier = all.filter { it.id != trip.id && it.startedAtMs < trip.startedAtMs }
            val previousBest = earlier
                .filter { sameDestination(it, trip.destPlaceId, trip.destination) }
                .maxByOrNull { it.points }
            return Comparisons(
                previousBest = previousBest,
                averagePoints = earlier.takeIf { it.isNotEmpty() }?.map { it.points }?.average()?.roundToInt(),
                isNewBest = previousBest != null && trip.points > previousBest.points,
            )
        }

        /** Same Places ID when both have one; otherwise destinations within 150 m. */
        fun sameDestination(trip: TripEntity, placeId: String?, point: GeoPoint): Boolean =
            if (trip.destPlaceId != null && placeId != null) trip.destPlaceId == placeId
            else GeoMath.distanceM(trip.destination, point) <= SAME_PLACE_RADIUS_M
    }
}
