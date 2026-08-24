package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptsFeatureRulesTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 20)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val budget = Budget(amountMinor = 5_000_00)

    @Test
    fun dropCatalogContainsEverySpecifiedRule() {
        assertEquals(
            setOf(
                "1am-tax", "small-stuff", "pace-check", "category-spike",
                "merchant-obsession", "concentration", "expensive-personality",
                "before-you-touch-it", "quiet-run", "new-places", "flexible-half", "refund-landed",
            ),
            DropEngine.rules.mapTo(mutableSetOf()) { it.ruleKey },
        )
    }

    @Test
    fun lateNightMerchantCanProduceMultipleIndependentDrops() {
        val transactions = (1..6).map { index ->
            transaction("night-$index", 100_00, today.minusDays(index.toLong() % 3), 1, "Blinkit", "groceries")
        }
        val drops = DropEngine.evaluate(transactions, spendingAnalytics(transactions, budget, now))
        val keys = drops.mapTo(mutableSetOf()) { it.ruleKey }

        assertTrue("1am-tax" in keys)
        assertTrue("merchant-obsession" in keys)
        assertTrue("concentration" in keys)
    }

    @Test
    fun smallReceiptsNeedFifteenRows() {
        val fourteen = (1..14).map { index -> transaction("small-$index", 99_00, today, 12, "Place $index", "misc") }
        val fifteen = fourteen + transaction("small-15", 99_00, today, 12, "Place 15", "misc")

        assertFalse(DropEngine.evaluate(fourteen, spendingAnalytics(fourteen, budget, now)).any { it.ruleKey == "small-stuff" })
        assertTrue(DropEngine.evaluate(fifteen, spendingAnalytics(fifteen, budget, now)).any { it.ruleKey == "small-stuff" })
    }

    @Test
    fun categorySpikeAndRefundUseTheirSpecifiedFloors() {
        val transactions = listOf(
            transaction("last", 1_000_00, LocalDate.of(2026, 7, 20), 12, "Shop", "shopping"),
            transaction("now", 1_500_00, today, 12, "Shop", "shopping"),
            transaction("refund", 200_00, today, 13, "Shop", "refund", Direction.CREDIT),
        )
        val keys = DropEngine.evaluate(transactions, spendingAnalytics(transactions, budget, now)).mapTo(mutableSetOf()) { it.ruleKey }

        assertTrue("category-spike" in keys)
        assertTrue("refund-landed" in keys)
    }

    @Test
    fun longTermAndSocialStampsRequireTheirExplicitSignals() {
        val installed = now - 31L * 86_400_000L
        val withoutSignals = StampEngine.evaluate(StampEvaluationInput(emptyList(), budget, installedAt = installed, now = now))
        assertFalse(withoutSignals.any { it.id == "thirty" || it.id == "receipts" || it.id == "clean-slate" })

        val withSignals = StampEngine.evaluate(
            StampEvaluationInput(
                transactions = listOf(transaction("one", 100_00, today, 12, "Cafe", "food")),
                budget = budget,
                installedAt = installed,
                hasSynced = true,
                sharedReceipt = true,
                reviewQueueCleared = true,
                now = now,
            ),
        ).mapTo(mutableSetOf()) { it.id }
        assertTrue("thirty" in withSignals)
        assertTrue("receipts" in withSignals)
        assertTrue("clean-slate" in withSignals)
    }

    @Test
    fun rollingBaselineIsNeutralWithoutEnoughHistory() {
        val start = LocalDate.of(2026, 8, 1)
        assertEquals(null, rollingDailyBaseline(emptyList(), start, zone))
        val thin = listOf(
            transaction("thin-1", 700_00, start.minusDays(10), 12, "One", "food"),
            transaction("thin-2", 700_00, start.minusDays(5), 12, "Two", "food"),
            transaction("thin-3", 700_00, start.minusDays(1), 12, "Three", "food"),
        )
        assertEquals(null, rollingDailyBaseline(thin, start, zone))
    }

    @Test
    fun rollingBaselineHandlesSparseIrregularSpendingWithoutCollapsingToZero() {
        val start = LocalDate.of(2026, 8, 1)
        val history = listOf(
            transaction("week-1", 700_00, start.minusDays(55), 12, "One", "food"),
            transaction("week-3", 1_400_00, start.minusDays(38), 12, "Two", "food"),
            transaction("week-7", 700_00, start.minusDays(10), 12, "Three", "food"),
        )
        assertEquals(3_750L, rollingDailyBaseline(history, start, zone))
    }

    @Test
    fun refundMatchingRequiresAnExactAmountOrTheSameMerchant() {
        val credit = transaction("refund", 500_00, today, 12, "Myntra", "refund", Direction.CREDIT)
        val exact = transaction("exact", 500_00, today.minusDays(10), 12, "Different shop", "shopping")
        val sameMerchantPartial = transaction("partial", 900_00, today.minusDays(2), 12, "Myntra", "shopping")
        val unrelated = transaction("unrelated", 510_00, today.minusDays(1), 12, "Cafe", "food")

        assertEquals("exact", findRefundCandidate(listOf(unrelated, sameMerchantPartial, exact), credit)?.id)
        assertEquals("partial", findRefundCandidate(listOf(unrelated, sameMerchantPartial), credit)?.id)
        assertEquals(null, findRefundCandidate(listOf(unrelated), credit))
    }

    @Test
    fun wrappedCollectionStampsUseViewedRecapsNotSnapshotCount() {
        val snapshots = (1..12).map { index -> PeriodSnapshot("2025-${index.toString().padStart(2, '0')}", 1_000_00, 800_00, now) }
        val noneViewed = StampEngine.evaluate(StampEvaluationInput(emptyList(), budget, periodSnapshots = snapshots, wrappedCount = 0, now = now))
        val twelveViewed = StampEngine.evaluate(StampEvaluationInput(emptyList(), budget, periodSnapshots = snapshots, wrappedCount = 12, now = now))

        assertFalse(noneViewed.any { it.id == "the-archive" || it.id == "historian" })
        assertTrue(twelveViewed.any { it.id == "the-archive" })
        assertTrue(twelveViewed.any { it.id == "historian" })
    }

    private fun transaction(
        id: String,
        amount: Long,
        date: LocalDate,
        hour: Int,
        merchant: String,
        categoryId: String,
        direction: Direction = Direction.DEBIT,
    ) = Transaction(
        id = id,
        amountMinor = amount,
        direction = direction,
        occurredAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        merchant = merchant,
        categoryId = categoryId,
        status = TransactionStatus.CONFIRMED,
        source = TransactionSource.SMS,
    )
}
