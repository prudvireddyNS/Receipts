package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptsFeatureRulesTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun refundMatchingOnlyLinksTheSameMerchantWhenTheRefundNamesOne() {
        val credit = transaction("refund", 500_00, today, 12, "Myntra", "refund", Direction.CREDIT)
        val sameMerchantExact = transaction("exact", 500_00, today.minusDays(10), 12, "Myntra", "shopping")
        val sameMerchantPartial = transaction("partial", 900_00, today.minusDays(2), 12, "Myntra", "shopping")
        val otherShopSameAmount = transaction("coincidence", 500_00, today.minusDays(1), 12, "Cafe", "food")

        assertEquals("exact", findRefundCandidate(listOf(otherShopSameAmount, sameMerchantPartial, sameMerchantExact), credit)?.id)
        assertEquals("partial", findRefundCandidate(listOf(otherShopSameAmount, sameMerchantPartial), credit)?.id)
        // An equal amount at a different shop is a coincidence, not a match.
        assertEquals(null, findRefundCandidate(listOf(otherShopSameAmount), credit))
    }

    @Test
    fun anUnnamedRefundFallsBackToAnExactAmount() {
        val credit = transaction("refund", 500_00, today, 12, "Money received", "refund", Direction.CREDIT)
        val exact = transaction("exact", 500_00, today.minusDays(3), 12, "Myntra", "shopping")

        assertEquals("exact", findRefundCandidate(listOf(exact), credit)?.id)
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
