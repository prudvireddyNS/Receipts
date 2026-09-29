package com.prudvi.trackbudget.ui

import com.prudvi.trackbudget.model.BudgetRange
import com.prudvi.trackbudget.model.DashboardSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BurnUpSeriesTest {
    private val start = LocalDate.of(2026, 8, 28)

    private fun snapshot(
        spentMinor: Long,
        dailyTotals: Map<LocalDate, Long> = emptyMap(),
        dayOfPeriod: Int = 6,
        daysInPeriod: Int = 31,
    ) = DashboardSnapshot(
        spentMinor = spentMinor,
        refundedMinor = 0,
        remainingMinor = 0,
        safeTodayMinor = 0,
        dayOfPeriod = dayOfPeriod,
        daysInPeriod = daysInPeriod,
        categoryTotals = emptyMap(),
        range = BudgetRange(start, start.plusDays((daysInPeriod - 1).toLong())),
        dailyTotals = dailyTotals,
    )

    private fun day(offset: Long) = start.plusDays(offset)

    @Test
    fun aLargePaymentLandsOnTheDayItWasPaidNotOnDayOne() {
        // Rent paid on day 5 must not appear as money already gone on day 1.
        val snapshot = snapshot(
            spentMinor = 12_200_00,
            dailyTotals = mapOf(day(0) to 200_00, day(4) to 12_000_00),
        )

        val series = burnUpSeries(snapshot)

        assertEquals(200_00, series[0].cumulativeMinor)
        assertEquals(200_00, series[3].cumulativeMinor)
        assertEquals(12_200_00, series[4].cumulativeMinor)
        assertEquals(12_000_00, series[4].dayMinor)
    }

    @Test
    fun theHeadOfTheCurveAlwaysEqualsTheHeroAmount() {
        val snapshot = snapshot(
            spentMinor = 9_140_00,
            dailyTotals = mapOf(day(0) to 200_00, day(2) to 1_800_00, day(4) to 1_840_00, day(5) to 5_300_00),
        )

        assertEquals(snapshot.spentMinor, burnUpSeries(snapshot).last().cumulativeMinor)
    }

    @Test
    fun aPostDatedReceiptRidesOnTodayRatherThanVanishing() {
        // The editor's date stepper is unbounded, so a receipt can be dated later in the period.
        // It counts in spentMinor, so the curve's head must still match it.
        val snapshot = snapshot(
            spentMinor = 6_000_00,
            dailyTotals = mapOf(day(0) to 1_000_00, day(13) to 5_000_00),
        )

        val series = burnUpSeries(snapshot)

        assertEquals(6, series.size)
        assertEquals(6_000_00, series.last().cumulativeMinor)
        assertEquals(5_000_00, series.last().dayMinor)
    }

    @Test
    fun oneUnusualDayDoesNotDragTheProjectionWithIt() {
        // The real case: ₹5,300 of ₹9,140 spent on day 6 of 31. A mean projects ₹47.2k.
        val snapshot = snapshot(
            spentMinor = 9_140_00,
            dailyTotals = mapOf(
                day(0) to 200_00, day(1) to 450_00, day(2) to 1_800_00,
                day(3) to 290_00, day(4) to 1_100_00, day(5) to 5_300_00,
            ),
        )

        // median day = (450 + 1100) / 2 = 775; 9,140 + 775 x 25 remaining days
        assertEquals(9_140_00 + 775_00 * 25, burnUpProjection(snapshot))
    }

    @Test
    fun aSparseSpenderStillGetsAProjectionThatMoves() {
        // Two spending days out of six. The median of *all* days is zero, which used to freeze the
        // projection at what was already spent; instead the typical spending day (₹2,000) is scaled
        // by how often a day has spending at all (2 of 6).
        val snapshot = snapshot(
            spentMinor = 4_000_00,
            dailyTotals = mapOf(day(4) to 1_000_00, day(5) to 3_000_00),
        )

        val perDay = 2_000_00L * 2 / 6
        assertEquals(4_000_00 + perDay * 25, burnUpProjection(snapshot))
    }

    @Test
    fun noSpendingProjectsNothingMore() {
        assertEquals(0, burnUpProjection(snapshot(spentMinor = 0)))
    }

    @Test
    fun theProjectionNeverFallsBelowWhatIsAlreadySpent() {
        val snapshot = snapshot(
            spentMinor = 9_140_00,
            dailyTotals = mapOf(day(5) to 9_140_00),
            dayOfPeriod = 31,
        )

        assertEquals(9_140_00, burnUpProjection(snapshot))
    }
}
