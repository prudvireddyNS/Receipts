package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DashboardTest {
    private val nowDateTime = ZonedDateTime.now().withDayOfMonth(10).withHour(12)
    private val now = nowDateTime.toInstant().toEpochMilli()

    @Test
    fun countsIncludedDebitsAndCategoryReviewButNotGeneralReview() {
        val items = listOf(
            transaction("food", 1_000_00, Direction.DEBIT, "food"),
            transaction("transfer", 5_000_00, Direction.DEBIT, "transfers"),
            transaction("category-review", 900_00, Direction.DEBIT, "misc", TransactionStatus.CATEGORY_REVIEW),
            transaction("general-review", 700_00, Direction.DEBIT, null, TransactionStatus.NEEDS_REVIEW),
        )

        assertEquals(6_900_00, dashboard(items, Budget(10_000_00), now).spentMinor)
    }

    @Test
    fun subtractsAllIncludedCreditsWithoutClamping() {
        val credit = transaction("salary", 1_500_00, Direction.CREDIT, "income")

        val result = dashboard(listOf(credit), Budget(10_000_00), now)

        assertEquals(-1_500_00, result.spentMinor)
        assertEquals(-1_500_00L, result.categoryTotals["income"])
        assertEquals(-1_500_00L, result.dailyTotals[nowDateTime.toLocalDate()])
    }

    @Test
    fun linkedRefundReducesOriginalCategoryAndUnlinkedCreditReducesOwnCategory() {
        val debit = transaction("debit", 1_000_00, Direction.DEBIT, "food")
        val linked = transaction("refund", 250_00, Direction.CREDIT, "refund").copy(refundOfId = debit.id)
        val unmatched = transaction("unmatched", 300_00, Direction.CREDIT, "refund")

        val result = dashboard(listOf(debit, linked, unmatched), Budget(10_000_00), now)

        assertEquals(450_00, result.spentMinor)
        assertEquals(750_00L, result.categoryTotals["food"])
        assertEquals(-300_00L, result.categoryTotals["refund"])
    }

    @Test
    fun investmentsAreExcludedByDefaultAndCanBeIncluded() {
        val sip = transaction("sip", 2_000_00, Direction.DEBIT, "investment")

        assertEquals(0, dashboard(listOf(sip), Budget(10_000_00), now).spentMinor)
        assertEquals(2_000_00, dashboard(listOf(sip), Budget(10_000_00, countInvestmentsAsSpending = true), now).spentMinor)
    }

    @Test
    fun categoryLimitChangesAtEightyAndOneHundredPercent() {
        val budget = Budget(amountMinor = 10_000_00, categoryLimits = mapOf("food" to 1_000_00))

        assertEquals(
            CategoryLimitLevel.NORMAL,
            categoryLimitStatuses(listOf(transaction("normal", 799_00, Direction.DEBIT, "food")), budget, now).single().level,
        )
        assertEquals(
            CategoryLimitLevel.WARNING,
            categoryLimitStatuses(listOf(transaction("warning", 800_00, Direction.DEBIT, "food")), budget, now).single().level,
        )
        assertEquals(
            CategoryLimitLevel.EXCEEDED,
            categoryLimitStatuses(listOf(transaction("exceeded", 1_000_00, Direction.DEBIT, "food")), budget, now).single().level,
        )
    }

    @Test
    fun customPeriodCompatibilityStillUsesInclusiveDatesAndSafeDailyAmount() {
        val today = nowDateTime.toLocalDate()
        val start = today.minusDays(2)
        val end = today.plusDays(2)
        val budget = Budget(
            amountMinor = 5_000_00,
            period = "Custom",
            startEpochDay = start.toEpochDay(),
            endEpochDay = end.toEpochDay(),
        )
        val inside = transaction("inside", 1_000_00, Direction.DEBIT, "food")
        val outside = transaction("outside", 2_000_00, Direction.DEBIT, "food").copy(
            occurredAt = start.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )

        val result = dashboard(listOf(inside, outside), budget, now)

        assertEquals(5, result.daysInPeriod)
        assertEquals(3, result.dayOfPeriod)
        assertEquals(1_000_00, result.spentMinor)
        assertEquals(5_000_00 / 3 - 1_000_00, result.safeTodayMinor)
    }

    @Test
    fun todaysSpendingComesDirectlyOutOfTodaysAllowance() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 900_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(2).toEpochDay(),
        )

        val result = dashboard(
            listOf(transaction("today", 500_00, Direction.DEBIT, "food")),
            budget,
            now,
        )

        assertEquals(-200_00, result.safeTodayMinor)
    }

    @Test
    fun committedSpendCountsTowardTotalsButNotTodaysPacing() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 30_000_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(2).toEpochDay(),
        )
        val rent = transaction("rent", 12_000_00, Direction.DEBIT, "rent").copy(committed = true)
        val lunch = transaction("lunch", 300_00, Direction.DEBIT, "food")

        val result = dashboard(listOf(rent, lunch), budget, now)

        // Still tracked as spending against the budget total.
        assertEquals(12_300_00, result.spentMinor)
        assertEquals(12_000_00L, result.categoryTotals["rent"])
        assertEquals(12_000_00L, result.committedMinor)
        // But the rent payment doesn't crater today's safe-to-spend.
        assertEquals(30_000_00 / 3 - 300_00, result.safeTodayMinor)
    }

    @Test
    fun weekUsesConfiguredStartDay() {
        val range = budgetRange(Budget(period = "Week", resetDay = 3), LocalDate.of(2026, 8, 21))

        assertEquals(LocalDate.of(2026, 8, 19), range.start)
        assertEquals(LocalDate.of(2026, 8, 25), range.endInclusive)
        assertEquals(7, range.days)
    }

    private fun transaction(
        id: String,
        amount: Long,
        direction: Direction,
        category: String?,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        id = id,
        amountMinor = amount,
        direction = direction,
        occurredAt = now,
        merchant = id,
        categoryId = category,
        status = status,
        source = TransactionSource.MANUAL,
    )
}
