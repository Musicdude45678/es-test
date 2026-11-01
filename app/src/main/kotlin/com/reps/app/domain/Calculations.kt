package com.reps.app.domain

import com.reps.app.data.model.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Calc {
    /** Epley estimated one-rep max. */
    fun estimate1Rm(weightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)

    val kgPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val lbPlates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    /**
     * Plates needed on each side of the bar to reach [totalKg] with a bar of [barKg].
     * Returns an empty list when the total is at or below the bar weight.
     */
    fun platesPerSide(totalKg: Double, barKg: Double, unit: WeightUnit): List<Double> {
        val perSide = unit.fromKg((totalKg - barKg) / 2.0)
        if (perSide <= 0.0001) return emptyList()
        val plates = if (unit == WeightUnit.KG) kgPlates else lbPlates
        val result = mutableListOf<Double>()
        var remaining = perSide
        for (p in plates) {
            while (remaining + 0.0001 >= p) {
                result += p
                remaining -= p
            }
        }
        return result
    }

    /** Snap a weight to the nearest multiple of [stepKg]. */
    fun snap(kg: Double, stepKg: Double): Double = if (stepKg <= 0) kg else (kg / stepKg).roundToInt() * stepKg

    fun formatWeight(kg: Double, unit: WeightUnit, withUnit: Boolean = true): String {
        val v = unit.fromKg(kg)
        val rounded = (v * 100).roundToInt() / 100.0
        val text = if (abs(rounded - rounded.roundToInt()) < 0.005) rounded.roundToInt().toString()
        else String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        return if (withUnit) "$text ${unit.label}" else text
    }

    fun formatNumber(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (abs(rounded - rounded.roundToInt()) < 0.005) rounded.roundToInt().toString()
        else String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }

    fun formatVolume(kg: Double, unit: WeightUnit, withUnit: Boolean = true): String {
        val v = unit.fromKg(kg)
        val number = if (v >= 10_000) String.format(Locale.US, "%.1fk", v / 1000.0) else v.roundToInt().toString()
        return if (withUnit) "$number ${unit.label}" else number
    }

    fun formatDuration(millis: Long): String {
        val totalSec = (millis / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatDurationShort(millis: Long): String {
        val totalMin = (millis / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m} min"
    }
}
