package com.prudvi.trackbudget.data

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.CategoryLimitLevel
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.ParsedTransaction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.categoryLimitStatuses
import com.prudvi.trackbudget.sms.SmsParser
import com.prudvi.trackbudget.widget.BudgetWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.UUID

class TrackRepository(private val context: Context) {
    private val database = TrackDatabase(context)
    private val preferences = context.getSharedPreferences("track_budget", Context.MODE_PRIVATE)
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _learnedRules = MutableStateFlow<List<LearnedRule>>(emptyList())
    private val _ignoredSources = MutableStateFlow<Set<String>>(emptySet())

    init {
        migrateLegacyData(context, database, preferences)
        database.transactions().filter {
            it.status == TransactionStatus.UNPARSEABLE &&
                it.sender != null &&
                it.rawMessage != null &&
                !SmsParser.isPaymentCandidate(it.sender, it.rawMessage)
        }.forEach { database.delete(it.id) }
        _transactions.value = database.transactions()
        _learnedRules.value = loadLearnedRules()
        _ignoredSources.value = preferences.getStringSet("ignored_sources", emptySet()).orEmpty().toSet()
        evaluateCategoryLimits(budget, _transactions.value)
    }

    val transactions: StateFlow<List<Transaction>> = _transactions
    val learnedRules: StateFlow<List<LearnedRule>> = _learnedRules
    val ignoredSources: StateFlow<Set<String>> = _ignoredSources

    val onboardingComplete: Boolean
        get() = preferences.getBoolean("onboarding_complete", false)

    var budget: Budget
        get() = Budget(
            amountMinor = preferences.getLong("budget_minor", 30_000_00),
            period = preferences.getString("budget_period", "Month") ?: "Month",
            repeats = preferences.getBoolean("budget_repeats", true),
            carryOver = preferences.getBoolean("budget_carry", false),
            startEpochDay = preferences.getLong("budget_start_day", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
            endEpochDay = preferences.getLong("budget_end_day", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
            categoryLimits = preferences.getStringSet("budget_limits", emptySet()).orEmpty().mapNotNull { value ->
                val parts = value.split('=')
                if (parts.size == 2) parts[1].toLongOrNull()?.let { parts[0] to it } else null
            }.toMap(),
        )
        private set(value) {
            preferences.edit()
                .putLong("budget_minor", value.amountMinor)
                .putString("budget_period", value.period)
                .putBoolean("budget_repeats", value.repeats)
                .putBoolean("budget_carry", value.carryOver)
                .apply {
                    if (value.startEpochDay == null) remove("budget_start_day") else putLong("budget_start_day", value.startEpochDay)
                    if (value.endEpochDay == null) remove("budget_end_day") else putLong("budget_end_day", value.endEpochDay)
                }
                .putStringSet("budget_limits", value.categoryLimits.mapTo(mutableSetOf()) { "${it.key}=${it.value}" })
                .apply()
            BudgetWidgetProvider.updateAll(context)
            evaluateCategoryLimits(value, _transactions.value)
        }

    fun finishOnboarding(value: Budget) {
        budget = value
        preferences.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun finishOnboarding(budgetMinor: Long) = finishOnboarding(budget.copy(amountMinor = budgetMinor))

    fun updateBudget(value: Budget) {
        budget = value
    }

    fun refreshCategoryLimitAlerts() {
        evaluateCategoryLimits(budget, _transactions.value)
    }

    fun setSourceIgnored(key: String, ignored: Boolean) {
        val updated = _ignoredSources.value.toMutableSet().apply {
            if (ignored) add(key) else remove(key)
        }
        preferences.edit().putStringSet("ignored_sources", updated).apply()
        _ignoredSources.value = updated
    }

    fun addLearnedRule(merchant: String, categoryId: String) {
        val normalized = merchant.trim().uppercase()
        if (normalized.isBlank()) return
        val updated = _learnedRules.value.filterNot { it.merchant == normalized } + LearnedRule(normalized, categoryId)
        saveLearnedRules(updated)
    }

    fun removeLearnedRule(merchant: String) {
        saveLearnedRules(_learnedRules.value.filterNot { it.merchant == merchant })
    }

    @Synchronized
    fun addManual(amountMinor: Long, merchant: String, categoryId: String, direction: Direction, occurredAt: Long = System.currentTimeMillis()) {
        require(amountMinor > 0) { "Amount must be positive" }
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = occurredAt,
            merchant = merchant.ifBlank { if (direction == Direction.DEBIT) "Manual expense" else "Money received" },
            categoryId = categoryId,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
        )
        database.insert(transaction)
        refresh()
    }

    @Synchronized
    fun addSms(sender: String, body: String, receivedAt: Long): Transaction? {
        val transaction = parseSms(sender, body, receivedAt) ?: return null
        if (!database.insert(transaction)) {
            val existing = transaction.refId?.let(database::findByRef) ?: return null
            database.update(
                existing.copy(
                    merchant = if (existing.merchant == "Uncategorised payment") transaction.merchant else existing.merchant,
                    accountTail = existing.accountTail ?: transaction.accountTail,
                    sender = existing.sender ?: transaction.sender,
                    rawMessage = existing.rawMessage ?: transaction.rawMessage,
                    categoryId = existing.categoryId ?: transaction.categoryId,
                ),
            )
            refresh()
            return existing
        }
        refresh()
        return transaction
    }

    @Synchronized
    fun importInbox(days: Int = 90): Int {
        if (preferences.getBoolean("legacy_sms_highwater_pending", false)) {
            val newestId = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                "${Telephony.Sms._ID} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
            preferences.edit()
                .putLong("last_sms_id", newestId)
                .putBoolean("legacy_sms_highwater_pending", false)
                .apply()
            return 0
        }

        val since = System.currentTimeMillis() - days * 86_400_000L
        val lastId = preferences.getLong("last_sms_id", 0)
        var highestId = lastId
        var imported = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms._ID} > ?",
            arrayOf(since.toString(), lastId.toString()),
            "${Telephony.Sms._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val senderIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                highestId = maxOf(highestId, id)
                val sender = cursor.getString(senderIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: continue
                val transaction = parseSms(sender, body, cursor.getLong(dateIndex)) ?: continue
                if (database.insert(transaction)) imported++
            }
        }
        preferences.edit().putLong("last_sms_id", highestId).apply()
        refresh()
        return imported
    }

    @Synchronized
    fun save(transaction: Transaction) {
        require(transaction.amountMinor > 0 || transaction.status == TransactionStatus.EXCLUDED) { "Amount must be positive" }
        database.update(transaction)
        refresh()
    }

    @Synchronized
    fun delete(id: String) {
        database.delete(id)
        refresh()
    }

    @Synchronized
    fun clearAll() {
        database.clear()
        preferences.edit().remove("last_sms_id").apply()
        refresh()
    }

    @Synchronized
    fun addSampleData() {
        if (_transactions.value.any { it.source == TransactionSource.SAMPLE }) return
        val now = ZonedDateTime.now()
        val samples = listOf(
            sample(now.minusHours(2), 420_00, "Swiggy", "food"),
            sample(now.minusHours(5), 100_00, "Metro card", "transport"),
            sample(now.minusDays(1).minusHours(1), 2_199_00, "Amazon", "shopping"),
            sample(now.minusDays(2), 640_00, "Zomato", null, TransactionStatus.NEEDS_REVIEW),
            sample(now.minusDays(4), 12_000_00, "Rent", "rent"),
            sample(now.minusDays(7), 649_00, "Netflix", "subscription").copy(recurring = true),
        )
        samples.forEach(database::insert)
        refresh()
    }

    private fun parseSms(sender: String, body: String, receivedAt: Long): Transaction? {
        if ("sender:$sender" in _ignoredSources.value) return null
        val parsed = SmsParser.parse(sender, body, receivedAt)
        if (parsed == null) {
            if (!SmsParser.isPaymentCandidate(sender, body)) return null
            return Transaction(
                id = UUID.randomUUID().toString(),
                amountMinor = 0,
                direction = Direction.DEBIT,
                occurredAt = receivedAt,
                merchant = "Unknown payment",
                categoryId = null,
                status = TransactionStatus.UNPARSEABLE,
                source = TransactionSource.SMS,
                sender = sender,
                sourceKey = sourceKey(sender, receivedAt, body),
                rawMessage = body,
            )
        }
        if (parsed.accountTail?.let { "account:$it" in _ignoredSources.value } == true) return null
        return parsed.toTransaction(sender, body)
    }

    private fun ParsedTransaction.toTransaction(sender: String, body: String): Transaction {
        val learned = _learnedRules.value.firstOrNull { it.merchant == merchant.trim().uppercase() }?.categoryId
        val inferred = if (direction == Direction.DEBIT) learned ?: inferCategory(merchant) else learned
        val status = when {
            learned != null -> TransactionStatus.CONFIRMED
            direction == Direction.CREDIT -> TransactionStatus.NEEDS_RESOLUTION
            inferred == null || confidence < 0.75f -> TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.CONFIRMED
        }
        val refundMatch = if (direction == Direction.CREDIT && inferred == "refund") {
            database.transactions().filter { it.direction == Direction.DEBIT && it.status == TransactionStatus.CONFIRMED }
                .minByOrNull { kotlin.math.abs(it.amountMinor - amountMinor) }?.id
        } else null
        return Transaction(
            id = UUID.randomUUID().toString(),
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = occurredAt,
            merchant = merchant,
            categoryId = inferred,
            status = status,
            source = TransactionSource.SMS,
            accountTail = accountTail,
            sender = sender,
            refId = refId,
            sourceKey = sourceKey(sender, occurredAt, body),
            refundOfId = refundMatch,
            rawMessage = body,
        )
    }

    private fun sourceKey(sender: String, occurredAt: Long, body: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$sender|$occurredAt|$body".toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }

    private fun inferCategory(merchant: String): String? {
        val value = merchant.lowercase()
        return when {
            listOf("swiggy", "zomato", "restaurant", "cafe").any(value::contains) -> "food"
            listOf("zepto", "bigbasket", "instamart", "grocery").any(value::contains) -> "groceries"
            listOf("amazon", "flipkart", "myntra").any(value::contains) -> "shopping"
            listOf("uber", "ola", "rapido", "metro", "fuel").any(value::contains) -> "transport"
            listOf("netflix", "spotify", "bookmyshow", "jiosaavn").any(value::contains) -> "entertainment"
            listOf("hospital", "pharmacy", "medical").any(value::contains) -> "medical"
            else -> null
        }
    }

    private fun sample(
        time: ZonedDateTime,
        amountMinor: Long,
        merchant: String,
        categoryId: String?,
        status: TransactionStatus = TransactionStatus.CONFIRMED,
    ) = Transaction(
        id = UUID.randomUUID().toString(),
        amountMinor = amountMinor,
        direction = Direction.DEBIT,
        occurredAt = time.toInstant().toEpochMilli(),
        merchant = merchant,
        categoryId = categoryId,
        status = status,
        source = TransactionSource.SAMPLE,
    )

    private fun loadLearnedRules(): List<LearnedRule> = preferences.getStringSet("learned_rules", emptySet()).orEmpty().mapNotNull { value ->
        val parts = value.split('\t')
        if (parts.size != 3) return@mapNotNull null
        parts[2].toLongOrNull()?.let { LearnedRule(parts[0], parts[1], it) }
    }.sortedByDescending { it.learnedAtEpochDay }

    private fun saveLearnedRules(rules: List<LearnedRule>) {
        preferences.edit().putStringSet(
            "learned_rules",
            rules.mapTo(mutableSetOf()) { "${it.merchant}\t${it.categoryId}\t${it.learnedAtEpochDay}" },
        ).apply()
        _learnedRules.value = rules
    }

    private fun evaluateCategoryLimits(value: Budget, transactions: List<Transaction>) {
        val range = budgetRange(value)
        val periodPrefix = "${range.start.toEpochDay()}:${range.endInclusive.toEpochDay()}:"
        val sent = preferences.getStringSet("category_limit_alerts", emptySet()).orEmpty()
            .filterTo(mutableSetOf()) { it.startsWith(periodPrefix) }
        var changed = sent.size != preferences.getStringSet("category_limit_alerts", emptySet()).orEmpty().size
        val canNotify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        categoryLimitStatuses(transactions, value).forEach { status ->
            val warningKey = "$periodPrefix${status.categoryId}:80"
            val exceededKey = "$periodPrefix${status.categoryId}:100"
            when {
                status.level == CategoryLimitLevel.EXCEEDED && exceededKey !in sent && canNotify -> {
                    showCategoryLimitNotification(status.categoryId, status.spentMinor, status.limitMinor, exceeded = true, exceededKey.hashCode())
                    sent += warningKey
                    sent += exceededKey
                    changed = true
                }
                status.level == CategoryLimitLevel.WARNING && warningKey !in sent && canNotify -> {
                    showCategoryLimitNotification(status.categoryId, status.spentMinor, status.limitMinor, exceeded = false, warningKey.hashCode())
                    sent += warningKey
                    changed = true
                }
            }
        }
        if (changed) preferences.edit().putStringSet("category_limit_alerts", sent).apply()
    }

    private fun showCategoryLimitNotification(categoryId: String, spent: Long, limit: Long, exceeded: Boolean, notificationId: Int) {
        val categoryName = category(categoryId)?.name ?: "Category"
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (exceeded) {
            "${rupees(spent - limit)} over your ${rupees(limit)} limit"
        } else {
            "${rupees(spent)} of ${rupees(limit)} used · ${rupees(limit - spent)} left"
        }
        val notification = Notification.Builder(context, "category_limits")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (exceeded) "$categoryName limit exceeded" else "$categoryName limit is at 80%")
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun rupees(minor: Long): String = "₹" + java.text.NumberFormat.getIntegerInstance(
        java.util.Locale.Builder().setLanguage("en").setRegion("IN").build(),
    ).format(kotlin.math.abs(minor) / 100)

    private fun refresh() {
        _transactions.value = database.transactions()
        BudgetWidgetProvider.updateAll(context)
        evaluateCategoryLimits(budget, _transactions.value)
    }
}
