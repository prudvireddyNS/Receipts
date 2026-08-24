package com.prudvi.trackbudget.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun rollingDailyBaseline(
    transactions: List<Transaction>,
    currentStart: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    val historyStart = currentStart.minusDays(56)
    val dated = transactions.map { it to Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        .filter { (_, date) -> !date.isBefore(historyStart) && date.isBefore(currentStart) }
    val earliest = dated.minOfOrNull { it.second } ?: return null
    val observedDays = (currentStart.toEpochDay() - earliest.toEpochDay()).toInt()
    if (observedDays < 28) return null

    val debits = dated.filter { (transaction, _) ->
        transaction.direction == Direction.DEBIT &&
            transaction.status == TransactionStatus.CONFIRMED &&
            category(transaction.categoryId)?.notSpending != true
    }
    val debitDates = debits.associate { (transaction, date) -> transaction.id to date }
    val weeklyTotals = LongArray(8)
    debits.forEach { (transaction, date) ->
        val week = ((date.toEpochDay() - historyStart.toEpochDay()) / 7).toInt()
        weeklyTotals[week] += transaction.amountMinor
    }
    dated.asSequence()
        .filter { (transaction, _) ->
            transaction.direction == Direction.CREDIT &&
                transaction.status == TransactionStatus.CONFIRMED &&
                transaction.categoryId == "refund" &&
                transaction.refundOfId?.let(debitDates::containsKey) == true
        }
        .forEach { (refund, _) ->
            val debitDate = debitDates.getValue(refund.refundOfId ?: return@forEach)
            val week = ((debitDate.toEpochDay() - historyStart.toEpochDay()) / 7).toInt()
            weeklyTotals[week] = (weeklyTotals[week] - refund.amountMinor).coerceAtLeast(0)
        }

    val activeWeeks = weeklyTotals.filter { it > 0 }.sorted()
    if (activeWeeks.size < 3) return null
    val activeMedian = if (activeWeeks.size % 2 == 0) {
        (activeWeeks[activeWeeks.size / 2 - 1] + activeWeeks[activeWeeks.size / 2]) / 2
    } else {
        activeWeeks[activeWeeks.size / 2]
    }
    val frequencyAdjustedWeek = activeMedian * activeWeeks.size / weeklyTotals.size
    return (frequencyAdjustedWeek / 7).coerceAtLeast(1)
}
