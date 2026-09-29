package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DashboardTest {
    private val nowDateTime = ZonedDateTime.now().withDayOfMonth(10).withHour(12)
    private val now = nowDateTime.toInstant().toEpochMilli()

    private fun dayOfMonth(day: Int): Long =
        nowDateTime.withDayOfMonth(day).toInstant().toEpochMilli()

    private fun monthsAgo(months: Long): Long =
        nowDateTime.minusMonths(months).toInstant().toEpochMilli()

    @Test
    fun countsIncludedDebitsAndCategoryReviewButNotGeneralReviewOrTransfers() {
        val items = listOf(
            transaction("food", 1_000_00, Direction.DEBIT, "food"),
            transaction("transfer", 5_000_00, Direction.DEBIT, "transfers"),
            transaction("category-review", 900_00, Direction.DEBIT, "misc", TransactionStatus.CATEGORY_REVIEW),
            transaction("general-review", 700_00, Direction.DEBIT, null, TransactionStatus.NEEDS_REVIEW),
        )

        // The ₹5,000 "transfers" debit is not spending: only food and the category-review item count.
        assertEquals(1_900_00, dashboard(items, Budget(10_000_00), now).spentMinor)
    }

    @Test
    fun incomeDoesNotReduceSpending() {
        val salary = transaction("salary", 80_000_00, Direction.CREDIT, "income")
        val lunch = transaction("lunch", 300_00, Direction.DEBIT, "food")

        val result = dashboard(listOf(salary, lunch), Budget(10_000_00), now)

        assertEquals(300_00, result.spentMinor)
        assertEquals(9_700_00, result.remainingMinor)
        assertEquals(null, result.categoryTotals["income"])
    }

    @Test
    fun aRefundGivesMoneyBackToTheBudget() {
        val refund = transaction("refund", 1_500_00, Direction.CREDIT, "refund")

        val result = dashboard(listOf(refund), Budget(10_000_00), now)

        assertEquals(-1_500_00, result.spentMinor)
        assertEquals(-1_500_00L, result.categoryTotals["refund"])
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
    fun everyPaymentComesOutOfTodaysAllowance() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 30_000_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(2).toEpochDay(),
        )
        val rent = transaction("rent", 12_000_00, Direction.DEBIT, "rent")
        val lunch = transaction("lunch", 300_00, Direction.DEBIT, "food")

        val result = dashboard(listOf(rent, lunch), budget, now)

        assertEquals(12_300_00, result.spentMinor)
        assertEquals(12_000_00L, result.categoryTotals["rent"])
        // A large payment is not held out of pacing: today's share is 30,000/3 and it all went today.
        assertEquals(30_000_00 / 3 - 12_300_00, result.safeTodayMinor)
    }

    @Test
    fun declaredObligationsComeOffTheBudgetRatherThanOntoSpending() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 30_000_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(29).toEpochDay(),
            commitments = listOf(Commitment("rent", "Rent", 12_000_00)),
        )
        val lunch = transaction("lunch", 300_00, Direction.DEBIT, "food")

        val result = dashboard(listOf(lunch), budget, now)

        // The obligation never poses as spending...
        assertEquals(300_00, result.spentMinor)
        // ...it comes off the ceiling instead.
        assertEquals(12_000_00L, result.obligationsMinor)
        assertEquals(18_000_00L, result.spendableMinor)
        assertEquals(18_000_00L - 300_00, result.remainingMinor)
        // Category totals now reconcile with the headline spend, which they never did before.
        assertEquals(300_00, result.categoryTotals.values.sum())
        // Pacing still runs off what's actually left.
        assertEquals(18_000_00 / 30 - 300_00, result.safeTodayMinor)
    }

    @Test
    fun obligationsLargerThanTheBudgetLeaveNothingSpendableRatherThanGoingNegative() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 10_000_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(29).toEpochDay(),
            commitments = listOf(Commitment("rent", "Rent", 25_000_00)),
        )

        val result = dashboard(emptyList(), budget, now)

        assertEquals(0L, result.spendableMinor)
        assertEquals(0L, result.remainingMinor)
    }

    @Test
    fun disabledObligationsAreIgnored() {
        val today = nowDateTime.toLocalDate()
        val budget = Budget(
            amountMinor = 30_000_00,
            period = "Custom",
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(29).toEpochDay(),
            commitments = listOf(Commitment("rent", "Rent", 12_000_00, enabled = false)),
        )

        val result = dashboard(emptyList(), budget, now)

        assertEquals(0L, result.obligationsMinor)
        assertEquals(30_000_00L, result.spendableMinor)
    }

    @Test
    fun aMonthlyPeriodChargesTheDeclaredObligationExactly() {
        val commitments = listOf(Commitment("rent", "Rent", 20_000_00))
        // August has 31 days; the old days/30 proration billed ₹20,666.66 for a ₹20,000 rent.
        val august = Budget(amountMinor = 50_000_00, period = "Month", resetDay = 1, commitments = commitments)
        assertEquals(20_000_00L, august.obligationsMinor(budgetRange(august, LocalDate.of(2026, 8, 10))))
        // February is short; it must not be discounted either.
        val february = Budget(amountMinor = 50_000_00, period = "Month", resetDay = 1, commitments = commitments)
        assertEquals(20_000_00L, february.obligationsMinor(budgetRange(february, LocalDate.of(2026, 2, 10))))
        // A mid-month reset day is still a whole month.
        val shifted = Budget(amountMinor = 50_000_00, period = "Month", resetDay = 28, commitments = commitments)
        assertEquals(20_000_00L, shifted.obligationsMinor(budgetRange(shifted, LocalDate.of(2026, 8, 10))))
    }

    @Test
    fun aWeeklyPeriodOnlyChargesItsShareOfTheObligation() {
        val weekly = Budget(
            amountMinor = 12_000_00,
            period = "Week",
            resetDay = 1,
            commitments = listOf(Commitment("rent", "Rent", 30_000_00)),
        )
        val range = budgetRange(weekly, LocalDate.of(2026, 8, 12))

        assertEquals(7, range.days)
        assertEquals(30_000_00L * 7 / 30, weekly.obligationsMinor(range))
    }

    @Test
    fun switchingPeriodScalesTheBudgetInsteadOfKeepingTheNumber() {
        val monthly = Budget(amountMinor = 30_000_00, period = "Month")

        assertEquals(6_000_00L, monthly.amountForPeriod("Week"))
        assertEquals(30_000_00L, monthly.amountForPeriod("Month"))
        assertEquals(0L, Budget(period = "Month").amountForPeriod("Week"))
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
