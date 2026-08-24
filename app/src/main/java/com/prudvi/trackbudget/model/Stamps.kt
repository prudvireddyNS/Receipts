package com.prudvi.trackbudget.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StampKind { ONBOARDING, HABIT, STREAK, RESTRAINT, DISCOVERY, VOLUME, FUNNY, GOALS, LONGEVITY, SOCIAL }

data class StampDefinition(
    val id: String,
    val title: String,
    val description: String,
    val kind: StampKind,
)

data class StampEvaluationInput(
    val transactions: List<Transaction>,
    val budget: Budget,
    val goals: List<Goal> = emptyList(),
    val periodSnapshots: List<PeriodSnapshot> = emptyList(),
    val installedAt: Long = System.currentTimeMillis(),
    val hasSynced: Boolean = false,
    val sharedReceipt: Boolean = false,
    val reviewQueueCleared: Boolean = false,
    val reviewQueuePeakByPeriod: Map<String, Int> = emptyMap(),
    val wrappedCount: Int = 0,
    val now: Long = System.currentTimeMillis(),
)

object StampEngine {
    val definitions: List<StampDefinition> = listOf(
        stamp("first-blood", "FIRST BLOOD", "First receipt added by hand", StampKind.ONBOARDING),
        stamp("clean-slate", "CLEAN SLATE", "Review queue reaches zero", StampKind.HABIT),
        stamp("seven", "SEVEN", "7 consecutive days under pace", StampKind.STREAK),
        stamp("thirty", "THIRTY", "30 consecutive days with the app installed and syncing", StampKind.STREAK),
        stamp("under", "UNDER", "Finish a full period under budget", StampKind.RESTRAINT),
        stamp("night-owl", "NIGHT OWL", "₹500+ spent between midnight and 4am in one period", StampKind.DISCOVERY),
        stamp("dawn-patrol", "DAWN PATROL", "A receipt between 4am and 6am", StampKind.DISCOVERY),
        stamp("one-trick", "ONE TRICK", "One merchant takes >25% of a period", StampKind.DISCOVERY),
        stamp("the-spread", "THE SPREAD", "Receipts in 10+ distinct categories in one period", StampKind.DISCOVERY),
        stamp("centurion", "CENTURION", "100 receipts collected", StampKind.VOLUME),
        stamp("five-hundred", "FIVE HUNDRED", "500 receipts collected", StampKind.VOLUME),
        stamp("one-lakh", "₹1 LAKH", "₹1,00,000 tracked in total", StampKind.VOLUME),
        stamp("biryani-index", "BIRYANI INDEX", "10+ food receipts in one period", StampKind.FUNNY),
        stamp("sunday-scaries", "SUNDAY SCARIES", "Sunday is the top spend day, 3 periods running", StampKind.FUNNY),
        stamp("round-number", "ROUND NUMBER", "A receipt for exactly ₹1,000, ₹2,000 or ₹5,000", StampKind.FUNNY),
        stamp("the-double", "THE DOUBLE", "Same merchant twice within one hour", StampKind.FUNNY),
        stamp("flat-week", "FLAT WEEK", "A week where no category moves more than 10%", StampKind.RESTRAINT),
        stamp("no-notes", "NO NOTES", "A full period with the review queue never above 3", StampKind.HABIT),
        stamp("first-stack", "FIRST STACK", "First goal created", StampKind.GOALS),
        stamp("halfway", "HALFWAY", "Any goal reaches 50%", StampKind.GOALS),
        stamp("cashed-out", "CASHED OUT", "Any goal completed", StampKind.GOALS),
        stamp("the-archive", "THE ARCHIVE", "Three Wrapped recaps collected", StampKind.LONGEVITY),
        stamp("historian", "HISTORIAN", "Twelve Wrapped recaps collected", StampKind.LONGEVITY),
        stamp("receipts", "RECEIPTS", "Share any Drop or Wrapped card", StampKind.SOCIAL),
    )

    fun evaluate(input: StampEvaluationInput): List<EarnedStamp> {
        val zone = ZoneId.systemDefault()
        val currentRange = budgetRange(input.budget, input.now.toLocalDate(zone))
        val currentKey = periodKey(currentRange)
        val spending = input.transactions.filter { it.isConfirmedSpending() }
        val periodTx = spending.filter { it.localDate(zone) in currentRange.start..currentRange.endInclusive }
        val earned = mutableListOf<EarnedStamp>()
        fun earn(id: String, key: String? = currentKey) = earned.add(EarnedStamp(id, input.now, key))

        if (input.transactions.any { it.source == TransactionSource.MANUAL }) earn("first-blood", null)
        if (input.reviewQueueCleared) earn("clean-slate", currentKey)
        if (underPaceStreak(input.transactions, input.budget, input.now) >= 7) earn("seven", currentKey)
        if (input.hasSynced && input.now - input.installedAt >= 30L * 86_400_000L) earn("thirty", null)
        input.periodSnapshots.firstOrNull { it.budgetMinor > 0 && it.spentMinor < it.budgetMinor }?.let { earn("under", it.periodKey) }
        if (periodTx.filter { it.localHour(zone) in 0..4 }.sumOf { it.amountMinor } >= 500_00) earn("night-owl", currentKey)
        if (spending.any { it.localHour(zone) in 4..5 }) earn("dawn-patrol", null)
        if (oneMerchantShare(periodTx) > 0.25f) earn("one-trick", currentKey)
        if (periodTx.mapNotNull { it.categoryId }.distinct().size >= 10) earn("the-spread", currentKey)
        if (spending.size >= 100) earn("centurion", null)
        if (spending.size >= 500) earn("five-hundred", null)
        if (spending.sumOf { it.amountMinor } >= 1_00_000_00) earn("one-lakh", null)
        if (periodTx.count { it.categoryId == "food" } >= 10) earn("biryani-index", currentKey)
        if (sundayTopThreePeriods(spending, zone)) earn("sunday-scaries", null)
        if (spending.any { it.amountMinor in roundNumbers }) earn("round-number", null)
        if (sameMerchantWithinHour(periodTx)) earn("the-double", currentKey)
        if (flatWeek(spending, zone)) earn("flat-week", null)
        val closedKeys = input.periodSnapshots.mapTo(mutableSetOf()) { it.periodKey }
        input.reviewQueuePeakByPeriod.entries.firstOrNull { it.key in closedKeys && it.value <= 3 }?.let { earn("no-notes", it.key) }
        if (input.goals.isNotEmpty()) earn("first-stack", null)
        if (input.goals.any { it.savedMinor * 2 >= it.targetMinor }) earn("halfway", null)
        if (input.goals.any(Goal::isComplete)) earn("cashed-out", null)
        if (input.wrappedCount >= 3) earn("the-archive", null)
        if (input.wrappedCount >= 12) earn("historian", null)
        if (input.sharedReceipt) earn("receipts", null)

        return earned.distinctBy { it.id }.filter { id -> definitions.any { it.id == id.id } }
    }

    private fun stamp(id: String, title: String, description: String, kind: StampKind) = StampDefinition(id, title, description, kind)
}

private val reviewStatuses = setOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE)
private val roundNumbers = setOf(1_000_00L, 2_000_00L, 5_000_00L)

private fun Transaction.isConfirmedSpending(): Boolean = direction == Direction.DEBIT &&
    status == TransactionStatus.CONFIRMED &&
    category(categoryId)?.notSpending != true

private fun Transaction.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
private fun Transaction.localHour(zone: ZoneId): Int = Instant.ofEpochMilli(occurredAt).atZone(zone).hour

private fun oneMerchantShare(transactions: List<Transaction>): Float {
    val total = transactions.sumOf { it.amountMinor }
    if (total <= 0) return 0f
    val top = transactions.groupBy { it.merchant.trim().lowercase() }.values.maxOfOrNull { items -> items.sumOf { it.amountMinor } } ?: 0L
    return top.toFloat() / total
}

private fun sameMerchantWithinHour(transactions: List<Transaction>): Boolean = transactions.groupBy { it.merchant.trim().lowercase() }.values.any { items ->
    val times = items.map { it.occurredAt }.sorted()
    times.zipWithNext().any { (a, b) -> b - a <= 3_600_000L }
}

private fun sundayTopThreePeriods(transactions: List<Transaction>, zone: ZoneId): Boolean {
    val periods = transactions.groupBy { it.localDate(zone).withDayOfMonth(1) }.toSortedMap(compareByDescending { it })
    return periods.values.take(3).size == 3 && periods.values.take(3).all { items ->
        val byDay = items.groupBy { it.localDate(zone).dayOfWeek }.mapValues { it.value.sumOf(Transaction::amountMinor) }
        byDay.maxByOrNull { it.value }?.key == DayOfWeek.SUNDAY
    }
}

private fun flatWeek(transactions: List<Transaction>, zone: ZoneId): Boolean {
    val weeks = transactions.groupBy { it.localDate(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap()
    val entries = weeks.entries.toList()
    return entries.zipWithNext().any { (previous, current) ->
        val prev = previous.value.byCategory()
        val next = current.value.byCategory()
        val categories = prev.keys intersect next.keys
        categories.isNotEmpty() && categories.all { id ->
            val before = prev[id] ?: 0L
            val after = next[id] ?: 0L
            before > 0 && kotlin.math.abs(after - before) * 100 <= before * 10
        }
    }
}

private fun List<Transaction>.byCategory(): Map<String, Long> = groupBy { it.categoryId.orEmpty() }.mapValues { it.value.sumOf(Transaction::amountMinor) }

private fun underPaceStreak(transactions: List<Transaction>, budget: Budget, now: Long): Int {
    val snapshot = dashboard(transactions, budget, now)
    if (budget.amountMinor <= 0) return 0
    var running = 0L
    var streak = 0
    repeat(snapshot.dayOfPeriod) { index ->
        val date = snapshot.range.start.plusDays(index.toLong())
        running += snapshot.dailyTotals[date]?.coerceAtLeast(0) ?: 0L
        val evenPace = budget.amountMinor * (index + 1) / snapshot.daysInPeriod
        streak = if (running < evenPace) streak + 1 else 0
    }
    return streak
}
