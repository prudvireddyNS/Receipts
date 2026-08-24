package com.prudvi.trackbudget.data

import android.content.Context
import android.provider.Telephony
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.ParsedTransaction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.findRefundCandidate
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
    val receipts by lazy { ReceiptsRepository(context, database, preferences) }
    private val categoryLimitNotifier = CategoryLimitNotifier(context, preferences)
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _learnedRules = MutableStateFlow<List<LearnedRule>>(emptyList())
    private val _ignoredSources = MutableStateFlow<Set<String>>(emptySet())

    init {
        migrateLegacyData(context, database, preferences)
        _transactions.value = database.transactions()
        _learnedRules.value = loadLearnedRules()
        _ignoredSources.value = preferences.getStringSet("ignored_sources", emptySet()).orEmpty().toSet()
        categoryLimitNotifier.evaluate(budget, _transactions.value, onboardingComplete)
        receipts.refreshFeatures(_transactions.value, budget)
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
            resetDay = preferences.getInt("reset_day", 1).coerceIn(1, 28),
        )
        private set(value) {
            preferences.edit()
                .putLong("budget_minor", value.amountMinor)
                .putString("budget_period", value.period)
                .putString(
                    "rhythm",
                    when (value.period) {
                        "Week" -> "WEEKLY"
                        "Rolling" -> "ROLLING"
                        else -> "MONTHLY"
                    },
                )
                .putBoolean("budget_repeats", value.repeats)
                .putBoolean("budget_carry", value.carryOver)
                .putInt("reset_day", value.resetDay.coerceIn(1, 28))
                .apply {
                    if (value.startEpochDay == null) remove("budget_start_day") else putLong("budget_start_day", value.startEpochDay)
                    if (value.endEpochDay == null) remove("budget_end_day") else putLong("budget_end_day", value.endEpochDay)
                }
                .putStringSet("budget_limits", value.categoryLimits.mapTo(mutableSetOf()) { "${it.key}=${it.value}" })
                .apply()
            BudgetWidgetProvider.updateAll(context)
            categoryLimitNotifier.evaluate(value, _transactions.value, onboardingComplete)
            receipts.refreshFeatures(_transactions.value, value)
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
        categoryLimitNotifier.evaluate(budget, _transactions.value, onboardingComplete)
    }

    fun setSourceIgnored(key: String, ignored: Boolean) {
        val updated = _ignoredSources.value.toMutableSet().apply {
            if (ignored) add(key) else remove(key)
        }
        preferences.edit().putStringSet("ignored_sources", updated).apply()
        _ignoredSources.value = updated
    }

    fun addLearnedRule(merchant: String, categoryId: String, direction: Direction? = null) {
        val normalized = merchant.trim().uppercase()
        if (normalized.isBlank()) return
        val updated = _learnedRules.value.filterNot {
            it.merchant == normalized && (direction == null || it.direction == direction || it.direction == null && it.appliesTo(direction))
        } + LearnedRule(normalized, categoryId, direction = direction)
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
    fun importInbox(days: Int = 90, force: Boolean = false): Int {
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
            receipts.markSmsSynced()
            return 0
        }

        val since = System.currentTimeMillis() - days * 86_400_000L
        val lastId = preferences.getLong("last_sms_id", 0)
        var highestId = lastId
        var imported = 0
        val knownMessages = if (force) {
            database.transactions().mapNotNullTo(mutableSetOf()) { transaction ->
                transaction.rawMessage?.let { "${transaction.sender}|${transaction.occurredAt}|$it" }
            }
        } else mutableSetOf()
        val selection = if (force) "${Telephony.Sms.DATE} >= ?" else "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms._ID} > ?"
        val arguments = if (force) arrayOf(since.toString()) else arrayOf(since.toString(), lastId.toString())
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection,
            arguments,
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
                val receivedAt = cursor.getLong(dateIndex)
                val messageIdentity = "$sender|$receivedAt|$body"
                if (messageIdentity in knownMessages) continue
                val transaction = parseSms(sender, body, receivedAt) ?: continue
                if (database.insert(transaction)) {
                    imported++
                    knownMessages += messageIdentity
                }
            }
        }
        preferences.edit().putLong("last_sms_id", highestId).apply()
        receipts.markSmsSynced()
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
        receipts.clearTransactionHistoryState()
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
        val normalizedMerchant = merchant.trim().uppercase()
        val matchingRules = _learnedRules.value.filter { it.merchant == normalizedMerchant }
        val learned = (matchingRules.firstOrNull { it.direction == direction }
            ?: matchingRules.firstOrNull { it.direction == null && it.appliesTo(direction) })?.categoryId
        val inferred = if (direction == Direction.DEBIT) learned ?: inferCategory(merchant) else learned
        val status = when {
            learned != null -> TransactionStatus.CONFIRMED
            direction == Direction.CREDIT -> TransactionStatus.NEEDS_RESOLUTION
            inferred == null || confidence < 0.75f -> TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.CONFIRMED
        }
        val candidate = Transaction(
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
            rawMessage = body,
        )
        val refundMatch = if (inferred == "refund") findRefundCandidate(database.transactions(), candidate)?.id else null
        return candidate.copy(
            refundOfId = refundMatch,
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
            listOf("blinkit", "zepto", "bigbasket", "instamart", "grocery").any(value::contains) -> "groceries"
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
        if (parts.size !in 3..4) return@mapNotNull null
        parts[2].toLongOrNull()?.let {
            LearnedRule(parts[0], parts[1], it, parts.getOrNull(3)?.takeIf(String::isNotBlank)?.let { name -> runCatching { Direction.valueOf(name) }.getOrNull() })
        }
    }.sortedByDescending { it.learnedAtEpochDay }

    private fun saveLearnedRules(rules: List<LearnedRule>) {
        preferences.edit().putStringSet(
            "learned_rules",
            rules.mapTo(mutableSetOf()) { "${it.merchant}\t${it.categoryId}\t${it.learnedAtEpochDay}\t${it.direction?.name.orEmpty()}" },
        ).apply()
        _learnedRules.value = rules
    }

    private fun LearnedRule.appliesTo(transactionDirection: Direction): Boolean {
        direction?.let { return it == transactionDirection }
        return if (transactionDirection == Direction.CREDIT) {
            categoryId in setOf("income", "refund", "repayments", "transfers")
        } else {
            categoryId !in setOf("income", "refund", "repayments")
        }
    }

    private fun refresh() {
        _transactions.value = database.transactions()
        BudgetWidgetProvider.updateAll(context)
        categoryLimitNotifier.evaluate(budget, _transactions.value, onboardingComplete)
        receipts.refreshFeatures(_transactions.value, budget)
    }
}
