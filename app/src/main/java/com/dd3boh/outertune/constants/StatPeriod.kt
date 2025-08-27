package com.dd3boh.outertune.constants

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class StatPeriod {
    `1_WEEK`, `1_MONTH`, `3_MONTH`, `6_MONTH`, `1_YEAR`, ALL;

    fun toTimeMillis(): Long =
        when (this) {
            `1_WEEK` -> LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            `1_MONTH` -> LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
            `3_MONTH` -> LocalDateTime.now().minusMonths(3).toInstant(ZoneOffset.UTC).toEpochMilli()
            `6_MONTH` -> LocalDateTime.now().minusMonths(6).toInstant(ZoneOffset.UTC).toEpochMilli()
            `1_YEAR` -> LocalDateTime.now().minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
            ALL -> 0
        }
    fun toLocalDateTime(): LocalDateTime =
        when (this) {
            `1_WEEK` -> LocalDateTime.now().minusWeeks(1)
            `1_MONTH` -> LocalDateTime.now().minusMonths(1)
            `3_MONTH` -> LocalDateTime.now().minusMonths(3)
            `6_MONTH` -> LocalDateTime.now().minusMonths(6)
            `1_YEAR` -> LocalDateTime.now().minusMonths(12)
            ALL -> LocalDateTime.of(0, 0, 1, 0, 0, 0)
        }
}