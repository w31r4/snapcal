package com.snapcal.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Days {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val display = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun today(): String = LocalDate.now(zone).toString()
    fun dayOf(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()
    fun parse(day: String): LocalDate = LocalDate.parse(day)

    fun label(day: String): String {
        val d = parse(day)
        val today = LocalDate.now(zone)
        return when (d) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> d.format(display)
        }
    }

    fun timeOf(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(time)

    /** Guess the meal name from the hour when the model doesn't give a useful title. */
    fun mealSlot(epochMillis: Long): String = when (Instant.ofEpochMilli(epochMillis).atZone(zone).hour) {
        in 5..9 -> "早餐"
        in 10..14 -> "午餐"
        in 17..21 -> "晚餐"
        else -> "加餐"
    }
}
