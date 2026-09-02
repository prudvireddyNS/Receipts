package com.prudvi.trackbudget.model

import kotlin.math.abs

fun findRefundCandidate(transactions: List<Transaction>, credit: Transaction): Transaction? {
    if (credit.direction != Direction.CREDIT || credit.amountMinor <= 0) return null
    val oldest = credit.occurredAt - 90L * 86_400_000L
    val candidates = transactions.filter {
        it.direction == Direction.DEBIT &&
            it.status in setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW) &&
            it.occurredAt in oldest..credit.occurredAt
    }
    val merchant = credit.merchant.normalizedMerchant()
    val exact = candidates.filter { it.amountMinor == credit.amountMinor }
    if (exact.isNotEmpty()) {
        return exact.minWithOrNull(
            compareBy<Transaction> { it.merchant.normalizedMerchant() != merchant }
                .thenBy { credit.occurredAt - it.occurredAt },
        )
    }
    if (!merchant.isSpecificMerchant()) return null
    return candidates.asSequence()
        .filter { it.merchant.normalizedMerchant() == merchant && it.amountMinor >= credit.amountMinor }
        .minWithOrNull(compareBy<Transaction> { it.amountMinor - credit.amountMinor }.thenBy { credit.occurredAt - it.occurredAt })
}

private fun String.normalizedMerchant(): String = trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
private fun String.isSpecificMerchant(): Boolean = isNotBlank() && this !in setOf("money received", "unknown payment", "uncategorised payment")
