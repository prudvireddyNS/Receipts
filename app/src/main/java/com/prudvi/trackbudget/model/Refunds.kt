package com.prudvi.trackbudget.model


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
    if (!merchant.isSpecificMerchant()) {
        // Nothing to tell shops apart by: an exact amount is the only signal, so take the closest in time.
        return exact.minByOrNull { credit.occurredAt - it.occurredAt }
    }
    // A named refund only links to that merchant's own purchase. An equal amount elsewhere is a
    // coincidence, and linking it would take the money off the wrong category.
    exact.filter { it.merchant.normalizedMerchant() == merchant }
        .minByOrNull { credit.occurredAt - it.occurredAt }
        ?.let { return it }
    return candidates.asSequence()
        .filter { it.merchant.normalizedMerchant() == merchant && it.amountMinor >= credit.amountMinor }
        .minWithOrNull(compareBy<Transaction> { it.amountMinor - credit.amountMinor }.thenBy { credit.occurredAt - it.occurredAt })
}

private fun String.normalizedMerchant(): String = trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
private fun String.isSpecificMerchant(): Boolean = isNotBlank() && this !in setOf("money received", "unknown payment", "uncategorised payment")
