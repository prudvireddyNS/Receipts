package com.prudvi.trackbudget.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun rememberCurrentDate(): LocalDate {
    val today by produceState(initialValue = LocalDate.now()) {
        while (true) {
            val now = ZonedDateTime.now()
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000L))
            value = LocalDate.now()
        }
    }
    return today
}
