package com.example.muamaizingbot.bot.devilsquare

import java.time.ZoneId
import java.time.ZonedDateTime

object DevilSquareClock {

    val ZONE: ZoneId = ZoneId.of("America/Lima")

    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)

    /** DS access-day key flips at 06:00 Lima. */
    fun accessDayKey(at: ZonedDateTime = now()): String {
        val shifted = if (at.hour < 6) at.minusDays(1) else at
        return "%04d-%02d-%02d".format(shifted.year, shifted.monthValue, shifted.dayOfMonth)
    }

    fun evenHourKey(at: ZonedDateTime = now()): String {
        return "%s-%02d".format(accessDayKey(at), at.hour)
    }

    /**
     * Even hours Lima 00:00–22:15, window :00–:15.
     * C5 copy says 01:00–23:00 because that clock is server time (Lima+1).
     */
    fun isInJoinWindow(at: ZonedDateTime = now()): Boolean {
        if (at.hour % 2 != 0) return false
        return at.minute < 15
    }
}
