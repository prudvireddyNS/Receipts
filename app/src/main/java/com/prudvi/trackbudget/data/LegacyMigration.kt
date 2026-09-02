package com.prudvi.trackbudget.data

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.sms.SmsParser
import kotlin.math.abs

internal fun migrateLegacyData(
    context: Context,
    database: TrackDatabase,
    preferences: SharedPreferences,
) {
    if (preferences.getBoolean("legacy_migration_complete", false)) return

    val legacyDatabaseFile = context.getDatabasePath("transactions.db")
    if (!legacyDatabaseFile.exists()) return

    val legacyPreferences = context.getSharedPreferences("expense_tracker_preferences", Context.MODE_PRIVATE)
    val legacyDatabase = SQLiteDatabase.openDatabase(
        legacyDatabaseFile.path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    var migratedTransactions = 0
    var migratedRawEvents = 0
    try {
        legacyDatabase.query("transactions", null, null, null, null, null, "posted_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val amount = abs(cursor.getLong("amount_paise"))
                if (amount == 0L) continue
                val direction = if (cursor.getString("type") == "CREDIT") Direction.CREDIT else Direction.DEBIT
                val categoryId = legacyCategory(cursor.getString("category"), direction)
                val legacyId = cursor.getLong("id")
                if (
                    database.insert(
                        Transaction(
                            id = "legacy-transaction-$legacyId",
                            amountMinor = amount,
                            direction = direction,
                            occurredAt = cursor.getLong("posted_at"),
                            merchant = cursor.getString("title").ifBlank { "Imported transaction" },
                            categoryId = categoryId,
                            status = TransactionStatus.CONFIRMED,
                            source = TransactionSource.LEGACY,
                            sender = cursor.getString("source_package"),
                            refId = cursor.getNullableString("reference_id"),
                            sourceKey = "legacy:${cursor.getString("notification_key")}",
                            rawMessage = cursor.getNullableString("raw_text"),
                        ),
                    )
                ) migratedTransactions++
            }
        }

        if (legacyDatabase.hasTable("pending_review_events")) {
            legacyDatabase.query("pending_review_events", null, null, null, null, null, "occurred_at ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    val amount = abs(cursor.getLong("amount_paise"))
                    if (amount == 0L) continue
                    val legacyId = cursor.getLong("id")
                    if (
                        database.insert(
                            Transaction(
                                id = "legacy-review-$legacyId",
                                amountMinor = amount,
                                direction = Direction.DEBIT,
                                occurredAt = cursor.getLong("occurred_at"),
                                merchant = cursor.getString("title").ifBlank { "Imported transaction" },
                                categoryId = legacyCategory(cursor.getString("category"), Direction.DEBIT),
                                status = TransactionStatus.NEEDS_REVIEW,
                                source = TransactionSource.LEGACY,
                                sender = cursor.getString("source_package"),
                                sourceKey = "legacy-review:${cursor.getString("event_key")}",
                                rawMessage = cursor.getNullableString("raw_text"),
                            ),
                        )
                    ) migratedTransactions++
                }
            }
        }

        if (legacyDatabase.hasTable("raw_events")) {
            legacyDatabase.query("raw_events", null, null, null, null, null, "occurred_at ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    val sender = cursor.getString("source_identifier")
                    val body = cursor.getString("raw_content")
                    val parsed = SmsParser.parse(sender, body, cursor.getLong("occurred_at")) ?: continue
                    val inferred = if (parsed.direction == Direction.DEBIT) parsed.suggestedCategoryId ?: legacySeedCategory(parsed.merchant) else null
                    val needsCategoryReview = parsed.direction == Direction.DEBIT && (inferred == null || parsed.confidence < 0.75f)
                    val categoryId = when {
                        parsed.isExplicitRefund -> "refund"
                        needsCategoryReview -> "misc"
                        parsed.direction == Direction.DEBIT -> inferred ?: "misc"
                        else -> null
                    }
                    val status = when {
                        parsed.excludeByDefault -> TransactionStatus.EXCLUDED
                        parsed.isExplicitRefund -> TransactionStatus.CONFIRMED
                        parsed.direction == Direction.CREDIT -> TransactionStatus.NEEDS_RESOLUTION
                        needsCategoryReview -> TransactionStatus.CATEGORY_REVIEW
                        else -> TransactionStatus.CONFIRMED
                    }
                    if (
                        database.insert(
                            Transaction(
                                id = "legacy-raw-${cursor.getLong("id")}",
                                amountMinor = parsed.amountMinor,
                                direction = parsed.direction,
                                occurredAt = parsed.occurredAt,
                                merchant = parsed.merchant,
                                categoryId = categoryId,
                                status = status,
                                source = TransactionSource.LEGACY,
                                accountTail = parsed.accountTail,
                                sender = sender,
                                refId = parsed.refId,
                                sourceKey = "legacy-raw:${cursor.getString("event_key")}",
                                rawMessage = body,
                            ),
                        )
                    ) migratedRawEvents++
                }
            }
        }

        preferences.edit()
            .putLong(
                "budget_minor",
                legacyPreferences.getLong("overall_monthly_budget_paise", 30_000_00),
            )
            .putString("budget_period", "Month")
            .putBoolean("budget_repeats", true)
            .putBoolean("onboarding_complete", legacyPreferences.getBoolean("onboarding_complete", true))
            .putBoolean("legacy_sms_highwater_pending", true)
            .putInt("legacy_transactions_migrated", migratedTransactions)
            .putInt("legacy_raw_events_migrated", migratedRawEvents)
            .putBoolean("legacy_migration_complete", true)
            .commit()
    } finally {
        legacyDatabase.close()
    }
}

private fun legacyCategory(name: String?, direction: Direction): String {
    if (direction == Direction.CREDIT) {
        return when (name?.trim()?.lowercase()) {
            "transfers" -> "transfers"
            "income" -> "income"
            else -> "repayments"
        }
    }
    return when (name?.trim()?.lowercase()) {
        "food & dining", "food" -> "food"
        "groceries" -> "groceries"
        "shopping" -> "shopping"
        "transport" -> "transport"
        "bills & recharges", "bills" -> "bills"
        "rent" -> "rent"
        "medical" -> "medical"
        "entertainment" -> "entertainment"
        "subscriptions", "subscription" -> "subscription"
        "cash" -> "cash"
        "transfers" -> "transfers"
        "money back", "repayments" -> "repayments"
        "investments", "investment" -> "investment"
        else -> "misc"
    }
}

private fun legacySeedCategory(merchant: String): String? {
    val value = merchant.lowercase()
    return when {
        listOf("swiggy", "zomato", "restaurant", "cafe").any(value::contains) -> "food"
        listOf("blinkit", "zepto", "bigbasket", "instamart", "grocery").any(value::contains) -> "groceries"
        listOf("amazon", "flipkart", "myntra").any(value::contains) -> "shopping"
        listOf("uber", "ola", "rapido", "metro", "fuel").any(value::contains) -> "transport"
        listOf("netflix", "spotify", "bookmyshow", "jiosaavn").any(value::contains) -> "entertainment"
        listOf("hospital", "pharmacy", "medical").any(value::contains) -> "medical"
        else -> null
    }
}

private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
    arrayOf(name),
).use(Cursor::moveToFirst)

private fun Cursor.getLong(column: String): Long = getLong(getColumnIndexOrThrow(column))
private fun Cursor.getString(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()
private fun Cursor.getNullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index).takeIf(String::isNotBlank)
}
