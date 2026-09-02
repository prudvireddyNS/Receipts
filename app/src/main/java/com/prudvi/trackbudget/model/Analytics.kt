package com.prudvi.trackbudget.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CategoryShift(val categoryId: String, val amountMinor: Long)
data class MerchantTotal(val merchant: String, val amountMinor: Long)

data class SpendingAnalytics(
    val snapshot: DashboardSnapshot,
    val projectedMinor: Long,
    val committedMinor: Long,
    val flexibleMinor: Long,
    val categoryShifts: List<CategoryShift>,
    val topMerchants: List<MerchantTotal>,
    val topThreeShare: Float,
    val recurring: List<Transaction>,
)

fun spendingAnalytics(
    transactions: List<Transaction>,
    budget: Budget,
    now: Long = System.currentTimeMillis(),
): SpendingAnalytics {
    val snapshot = dashboard(transactions, budget, now)
    val zone = ZoneId.systemDefault()
    val periodStart = snapshot.range.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val periodEnd = snapshot.range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val activeDebits = transactions.filter {
        it.direction == Direction.DEBIT &&
            it.status in setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW) &&
            it.occurredAt in periodStart until periodEnd &&
            (budget.countInvestmentsAsSpending || it.categoryId != "investment")
    }
    val projected = if (snapshot.dayOfPeriod <= 0) 0 else snapshot.spentMinor * snapshot.daysInPeriod / snapshot.dayOfPeriod
    val committed = snapshot.committedMinor
    val flexible = (snapshot.spentMinor - committed).coerceAtLeast(0)

    val previousStart = snapshot.range.start.minusDays(snapshot.range.days.toLong())
    val previousEnd = snapshot.range.start
    val previousByCategory = transactions.filter {
        val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
        it.direction == Direction.DEBIT &&
            it.status in setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW) &&
            date >= previousStart && date < previousEnd &&
            (budget.countInvestmentsAsSpending || it.categoryId != "investment")
    }.groupBy { it.categoryId.orEmpty() }.mapValues { (_, values) -> values.sumOf { it.amountMinor } }
    val categoryShifts = (snapshot.categoryTotals.keys + previousByCategory.keys).map { id ->
        CategoryShift(id, (snapshot.categoryTotals[id] ?: 0) - (previousByCategory[id] ?: 0))
    }.filter { it.amountMinor != 0L }.sortedByDescending { kotlin.math.abs(it.amountMinor) }.take(5)

    val topMerchants = activeDebits.groupBy { it.merchant }.map { (merchant, values) ->
        MerchantTotal(merchant, values.sumOf { it.amountMinor })
    }.sortedByDescending { it.amountMinor }.take(5)
    val topThree = topMerchants.take(3).sumOf { it.amountMinor }

    val recurringByPattern = transactions.filter {
        it.direction == Direction.DEBIT && it.status in setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)
    }.groupBy { it.merchant.trim().lowercase() }.filterValues { values ->
        values.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().withDayOfMonth(1) }.distinct().size > 1
    }.keys
    val recurring = transactions.filter {
        it.recurring || it.merchant.trim().lowercase() in recurringByPattern
    }.distinctBy { it.merchant.trim().lowercase() }.sortedByDescending { it.amountMinor }

    return SpendingAnalytics(
        snapshot = snapshot,
        projectedMinor = projected,
        committedMinor = committed,
        flexibleMinor = flexible,
        categoryShifts = categoryShifts,
        topMerchants = topMerchants,
        topThreeShare = if (snapshot.spentMinor == 0L) 0f else topThree.toFloat() / snapshot.spentMinor,
        recurring = recurring,
    )
}

fun periodLabel(range: BudgetRange): String = if (range.start.month == range.endInclusive.month) {
    range.start.month.name.lowercase().replaceFirstChar(Char::titlecase)
} else {
    "${range.start.dayOfMonth} ${range.start.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase)} – " +
        "${range.endInclusive.dayOfMonth} ${range.endInclusive.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase)}"
}
