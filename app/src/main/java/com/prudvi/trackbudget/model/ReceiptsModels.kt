package com.prudvi.trackbudget.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AppMode { CHILL, PACE, STACK }
enum class MoneyRhythm { MONTHLY, WEEKLY, ROLLING }
enum class AppAmplitude { LOUD, QUIET }
enum class AppThemePreference { COLORFUL, SUBTLE, LIGHT, DARK }

data class ReceiptsPreferences(
    val mode: AppMode = AppMode.CHILL,
    val rhythm: MoneyRhythm = MoneyRhythm.MONTHLY,
    val amplitude: AppAmplitude = AppAmplitude.LOUD,
    val resetDay: Int = 1,
    val theme: AppThemePreference = AppThemePreference.COLORFUL,
    val smsTrackingEnabled: Boolean = false,
    val countInvestmentsAsSpending: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val committedCategoryIds: Set<String> = DefaultCommittedCategoryIds,
)

val DefaultCommittedCategoryIds: Set<String> = setOf("rent", "subscription", "bills", "insurance")

data class Goal(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val targetEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    init {
        require(targetMinor > 0) { "Goal target must be positive" }
    }

    val progress: Float = (savedMinor.toFloat() / targetMinor).coerceIn(0f, 1f)
    val isComplete: Boolean = completedAt != null || savedMinor >= targetMinor
}

data class EarnedStamp(
    val id: String,
    val earnedAt: Long,
    val periodKey: String? = null,
    val seen: Boolean = false,
)

data class DismissedDropRule(
    val ruleKey: String,
    val dismissedAt: Long,
)

data class PeriodSnapshot(
    val periodKey: String,
    val budgetMinor: Long,
    val spentMinor: Long,
    val closedAt: Long,
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

fun previousMonthlyRange(today: LocalDate = LocalDate.now()): BudgetRange {
    val start = today.withDayOfMonth(1).minusMonths(1)
    return BudgetRange(start, start.withDayOfMonth(start.lengthOfMonth()))
}

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
