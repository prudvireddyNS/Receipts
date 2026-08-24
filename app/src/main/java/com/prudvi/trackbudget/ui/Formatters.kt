package com.prudvi.trackbudget.ui

import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val IndianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()

fun receiptMoney(minor: Long): String {
    val value = abs(minor)
    val formatter = NumberFormat.getNumberInstance(IndianLocale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = if (value % 100 == 0L) 0 else 2
    }
    return "₹${formatter.format(value / 100.0)}"
}

fun receiptWholeMoney(minor: Long): String = "₹${NumberFormat.getIntegerInstance(IndianLocale).format(kotlin.math.round(abs(minor) / 100.0).toLong())}"

fun receiptSignedMoney(transaction: Transaction): String =
    (if (transaction.direction == Direction.CREDIT) "+" else "−") + receiptMoney(transaction.amountMinor)

fun receiptMerchant(transaction: Transaction): String = when (transaction.merchant.trim().lowercase()) {
    "uncategorised payment", "unknown payment", "manual expense" -> "${categoryName(transaction.categoryId)} receipt"
    else -> transaction.merchant
}

fun canLearnMerchant(merchant: String): Boolean = merchant.trim().lowercase() !in setOf(
    "uncategorised payment",
    "unknown payment",
    "manual expense",
    "money received",
)

fun receiptShortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
fun receiptLongDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
fun receiptDayDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
fun receiptMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
fun receiptTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
fun receiptDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

fun receiptMeta(transaction: Transaction): String = buildString {
    append(receiptDayDate(receiptDate(transaction.occurredAt)))
    append(" · ")
    append(receiptTime(transaction.occurredAt))
    transaction.accountTail?.let { append(" · ••$it") }
    append(" · ")
    append(transaction.source.name.lowercase().replaceFirstChar(Char::titlecase))
}

fun decimalToMinor(value: String): Long = value.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0
fun integerRupeesToMinor(value: String): Long = (value.toLongOrNull() ?: 0L) * 100L
fun receiptPercent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100).toInt()}%"

fun spendLabel(period: String): String = when (period) {
    "Week" -> "Spent this week"
    "Rolling" -> "Spent in 30 days"
    "Custom" -> "Spent this period"
    else -> "Spent this month"
}
