package com.blindnav.ui.common

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Road distances in miles/feet where people drive by miles, otherwise km/m. */
object Format {
    private val imperialCountries = setOf("US", "GB", "LR", "MM")

    fun distance(meters: Double, locale: Locale = Locale.getDefault()): String =
        if (locale.country in imperialCountries) {
            val miles = meters / 1609.344
            if (miles < 0.1) "${roundTo10(meters * 3.28084)} ft" else String.format(locale, "%.1f mi", miles)
        } else {
            if (meters < 1000) "${roundTo10(meters)} m" else String.format(locale, "%.1f km", meters / 1000)
        }

    /** m:ss, or h:mm:ss from an hour up. */
    fun duration(seconds: Double): String {
        val total = seconds.roundToLong().coerceAtLeast(0)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun durationDelta(seconds: Double): String = sign(seconds) + duration(abs(seconds))

    fun distanceDelta(meters: Double, locale: Locale = Locale.getDefault()): String =
        sign(meters) + distance(abs(meters), locale)

    fun points(points: Int, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", points)

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    private fun sign(value: Double) = if (value < 0) "−" else "+"

    private fun roundTo10(value: Double) = ((value / 10).roundToInt() * 10)
}
