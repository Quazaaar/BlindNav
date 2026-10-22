package com.blindnav.scoring

import com.blindnav.geo.GeoMath
import com.blindnav.geo.GeoPoint
import com.blindnav.geo.LocalFrame
import com.blindnav.geo.PolylineIndex
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How long the route is shown before the map hides. Shorter = bigger points multiplier. */
enum class MemorizePreset(val seconds: Int, val multiplier: Double) {
    S10(10, 1.5),
    S30(30, 1.2),
    S60(60, 1.0),
}

enum class Grade(val minBase: Int) {
    S(950), A(880), B(780), C(650), D(500), F(0);

    companion object {
        fun fromBase(base: Int): Grade = entries.first { base >= it.minBase }
    }
}

data class ScoreInput(
    val optimalPath: List<GeoPoint>,
    val optimalDurationS: Double,
    val optimalDistanceM: Double,
    val track: List<GeoPoint>,
    val durationS: Double,
    val distanceM: Double,
    val multiplier: Double,
)

data class Score(
    val timeScore: Double,
    val distanceScore: Double,
    val routeMatch: Double,
    val base: Int,
    val grade: Grade,
    val points: Int,
    val detourCount: Int,
)

object ScoreCalculator {
    private const val MATCH_SPACING_M = 25.0
    private const val MATCH_TOLERANCE_M = 40.0
    private const val ARRIVAL_CREDIT_M = 75.0
    private const val OFF_ROUTE_M = 50.0
    private const val MIN_DETOUR_M = 100.0
    private const val ENDPOINT_GRACE_M = 150.0

    fun score(input: ScoreInput): Score {
        val t = timeScore(input.optimalDurationS, input.durationS)
        val d = distanceScore(input.optimalDistanceM, input.distanceM)
        val m = routeMatch(input.optimalPath, input.track)
        val base = base(t, d, m)
        return Score(
            timeScore = t,
            distanceScore = d,
            routeMatch = m,
            base = base,
            grade = Grade.fromBase(base),
            points = (base * input.multiplier).roundToInt(),
            detourCount = detours(input.optimalPath, input.track).size,
        )
    }

    /** Capped at 1: beating the traffic-aware ETA earns nothing extra. */
    fun timeScore(optimalS: Double, actualS: Double): Double = min(1.0, optimalS / actualS)

    fun distanceScore(optimalM: Double, actualM: Double): Double = min(1.0, optimalM / actualM)

    fun base(timeScore: Double, distanceScore: Double, routeMatch: Double): Int =
        (1000 * (0.35 * timeScore + 0.35 * distanceScore + 0.30 * routeMatch)).roundToInt()

    /**
     * Length-weighted fraction of the optimal route (resampled every 25 m) lying within 40 m of the track.
     * The route's last 75 m counts as matched: arrival triggers within that radius.
     */
    fun routeMatch(optimal: List<GeoPoint>, track: List<GeoPoint>): Double {
        val trackIndex = PolylineIndex(track, LocalFrame(optimal.first()))
        val end = optimal.last()
        val dense = GeoMath.densify(optimal, MATCH_SPACING_M)
        val matched = BooleanArray(dense.size) {
            trackIndex.isWithin(dense[it], MATCH_TOLERANCE_M) || GeoMath.distanceM(dense[it], end) <= ARRIVAL_CREDIT_M
        }
        var total = 0.0
        var hit = 0.0
        for (i in 0 until dense.size - 1) {
            val len = GeoMath.distanceM(dense[i], dense[i + 1])
            total += len
            hit += len * ((if (matched[i]) 1 else 0) + (if (matched[i + 1]) 1 else 0)) / 2.0
        }
        return if (total > 0) hit / total else 0.0
    }

    /**
     * Stretches of the track more than 50 m off the optimal route whose off-route run is at least 100 m long.
     * Points within 150 m of the start or the route end never count (parking lots, road snapping).
     * Each returned path includes the on-route points either side so it joins the route when drawn.
     */
    fun detours(optimal: List<GeoPoint>, track: List<GeoPoint>): List<List<GeoPoint>> {
        val routeIndex = PolylineIndex(optimal, LocalFrame(optimal.first()))
        val start = track.first()
        val end = optimal.last()
        val off = BooleanArray(track.size) {
            val p = track[it]
            !routeIndex.isWithin(p, OFF_ROUTE_M) &&
                GeoMath.distanceM(p, start) > ENDPOINT_GRACE_M &&
                GeoMath.distanceM(p, end) > ENDPOINT_GRACE_M
        }
        val result = mutableListOf<List<GeoPoint>>()
        var i = 0
        while (i < track.size) {
            if (!off[i]) {
                i++
                continue
            }
            var j = i
            while (j + 1 < track.size && off[j + 1]) j++
            if (GeoMath.lengthM(track.subList(i, j + 1)) >= MIN_DETOUR_M) {
                result += track.subList(max(i - 1, 0), min(j + 2, track.size)).toList()
            }
            i = j + 1
        }
        return result
    }
}
