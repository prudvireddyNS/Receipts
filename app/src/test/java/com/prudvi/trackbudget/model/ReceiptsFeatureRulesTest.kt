package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptsFeatureRulesTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 20)

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
