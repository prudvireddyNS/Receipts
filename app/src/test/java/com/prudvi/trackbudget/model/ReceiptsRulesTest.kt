package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReceiptsRulesTest {
    private val zone = ZoneId.systemDefault()
    private val nowDate = LocalDate.of(2026, 8, 20)
    private val now = nowDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun monthlyBudgetHonoursResetDayWithoutChangingDefault() {
        val defaultRange = budgetRange(Budget(), LocalDate.of(2026, 8, 19))
        assertEquals(LocalDate.of(2026, 8, 1), defaultRange.start)
        assertEquals(LocalDate.of(2026, 8, 31), defaultRange.endInclusive)

        val shiftedBeforeReset = budgetRange(Budget(resetDay = 20), LocalDate.of(2026, 8, 19))
        assertEquals(LocalDate.of(2026, 7, 20), shiftedBeforeReset.start)
        assertEquals(LocalDate.of(2026, 8, 19), shiftedBeforeReset.endInclusive)

        val shiftedOnReset = budgetRange(Budget(resetDay = 20), LocalDate.of(2026, 8, 20))
        assertEquals(LocalDate.of(2026, 8, 20), shiftedOnReset.start)
        assertEquals(LocalDate.of(2026, 9, 19), shiftedOnReset.endInclusive)
    }

    @Test
    fun rollingBudgetUsesTrailingThirtyDays() {
        val range = budgetRange(Budget(period = "Rolling"), LocalDate.of(2026, 8, 20))

        assertEquals(LocalDate.of(2026, 7, 22), range.start)
        assertEquals(LocalDate.of(2026, 8, 20), range.endInclusive)
        assertEquals(30, range.days)
    }

    @Test
    fun dropFloorsAndDismissalsAreStableByRule() {
        val transactions = listOf(
            tx("n1", 120_00, hour = 1, merchant = "Blinkit"),
            tx("n2", 110_00, hour = 2, merchant = "Blinkit"),
            tx("n3", 100_00, hour = 3, merchant = "Zepto"),
        )
        val analytics = spendingAnalytics(transactions, Budget(amountMinor = 5_000_00), now)
        val drops = DropEngine.evaluate(transactions, analytics)

        val nightDrop = drops.single { it.ruleKey == "1am-tax" }
        assertEquals("1am-tax-2026-08", nightDrop.key)
        assertFalse(DropEngine.evaluate(transactions, analytics, dismissedRuleKeys = setOf("1am-tax")).any { it.ruleKey == "1am-tax" })
    }

    @Test
    fun oneAmDropDoesNotAppearBelowFloor() {
        val transactions = listOf(
            tx("n1", 120_00, hour = 1),
            tx("n2", 110_00, hour = 2),
        )
        val analytics = spendingAnalytics(transactions, Budget(amountMinor = 5_000_00), now)

        assertFalse(DropEngine.evaluate(transactions, analytics).any { it.ruleKey == "1am-tax" })
    }

    @Test
    fun stampDefinitionsAreCompleteAndEvaluationIsAdditive() {
        assertEquals(24, StampEngine.definitions.size)
        assertEquals(24, StampEngine.definitions.map { it.id }.distinct().size)

        val transactions = listOf(
            tx("manual", 500_00, hour = 1, source = TransactionSource.MANUAL),
            tx("another", 100_00, hour = 2),
        )
        val earned = StampEngine.evaluate(
            StampEvaluationInput(
                transactions = transactions,
                budget = Budget(amountMinor = 5_000_00),
                now = now,
            ),
        ).map { it.id }.toSet()

        assertTrue("first-blood" in earned)
        assertTrue("night-owl" in earned)
    }

    private fun tx(
        id: String,
        amount: Long,
        day: Int = 20,
        hour: Int = 12,
        merchant: String = id,
        category: String? = "food",
        direction: Direction = Direction.DEBIT,
        source: TransactionSource = TransactionSource.SMS,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        id = id,
        amountMinor = amount,
        direction = direction,
        occurredAt = ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(),
        merchant = merchant,
        categoryId = category,
        status = status,
        source = source,
    )
}
