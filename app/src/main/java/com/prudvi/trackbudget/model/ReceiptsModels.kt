package com.prudvi.trackbudget.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MoneyRhythm { MONTHLY, WEEKLY, ROLLING }
enum class AppThemePreference { COLORFUL, SUBTLE, LIGHT, DARK }

data class ReceiptsPreferences(
    val rhythm: MoneyRhythm = MoneyRhythm.MONTHLY,
    val resetDay: Int = 1,
    val theme: AppThemePreference = AppThemePreference.COLORFUL,
    val smsTrackingEnabled: Boolean = false,
    val countInvestmentsAsSpending: Boolean = false,
    val biometricLockEnabled: Boolean = false,
)

fun Budget.withRhythm(rhythm: MoneyRhythm, resetDay: Int = this.resetDay): Budget = copy(
    period = when (rhythm) {
        MoneyRhythm.WEEKLY -> "Week"
        MoneyRhythm.MONTHLY, MoneyRhythm.ROLLING -> "Month"
    },
    resetDay = resetDay.coerceIn(if (rhythm == MoneyRhythm.WEEKLY) 1..7 else 1..28),
)

fun periodKey(range: BudgetRange): String {
    val fullCalendarMonth = range.start.dayOfMonth == 1 &&
        range.endInclusive == range.start.withDayOfMonth(range.start.lengthOfMonth())
    return if (fullCalendarMonth) {
        DateTimeFormatter.ofPattern("yyyy-MM", Locale.US).format(range.start)
    } else {
        "${range.start.toEpochDay()}:${range.endInclusive.toEpochDay()}"
    }
}

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
