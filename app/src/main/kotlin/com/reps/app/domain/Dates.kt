package com.reps.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun toLocalDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun relativeDay(epochMillis: Long): String {
        val d = toLocalDate(epochMillis)
        val today = LocalDate.now()
        return when {
            d == today -> "Today"
            d == today.minusDays(1) -> "Yesterday"
            d.year == today.year -> d.format(weekdayDayMonth)
            else -> d.format(dayMonthYear)
        }
    }

    fun shortDate(epochMillis: Long): String {
        val d = toLocalDate(epochMillis)
        return if (d.year == LocalDate.now().year) d.format(dayMonth) else d.format(dayMonthYear)
    }

    fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(time)

    fun monthHeader(epochMillis: Long): String = toLocalDate(epochMillis).format(monthYear)

    fun startOfWeekMillis(weeksAgo: Int = 0): Long {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo.toLong())
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun weekLabel(startMillis: Long): String = toLocalDate(startMillis).format(dayMonth)

    fun dayOfWeekShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    fun greeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 5 -> "Late night session"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
