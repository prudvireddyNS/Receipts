package com.prudvi.trackbudget.data

import android.content.Context
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Commitment
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.autoDetectCommitted
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.ParsedTransaction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.findRefundCandidate
import com.prudvi.trackbudget.model.periodKey
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
    val receipts by lazy { ReceiptsRepository(context, preferences) }
    private val categoryLimitNotifier = CategoryLimitNotifier(context, preferences)
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _learnedRules = MutableStateFlow<List<LearnedRule>>(emptyList())

    init {
        migrateLegacyData(context, database, preferences)
        backfillMerchantTitles()
        _transactions.value = database.transactions()
        _learnedRules.value = loadLearnedRules()
        categoryLimitNotifier.evaluate(activeBudget(), _transactions.value, onboardingComplete)
    }

    val transactions: StateFlow<List<Transaction>> = _transactions
    val learnedRules: StateFlow<List<LearnedRule>> = _learnedRules

    var smsTrackingEnabled: Boolean
        get() = preferences.getBoolean("sms_tracking_enabled", false)
        set(value) {
            preferences.edit().putBoolean("sms_tracking_enabled", value).apply()
        }

    var countInvestmentsAsSpending: Boolean
        get() = preferences.getBoolean("count_investments_as_spending", false)
        set(value) {
            updateBudget(budget.copy(countInvestmentsAsSpending = value))
        }

    var biometricLockEnabled: Boolean
        get() = preferences.getBoolean("biometric_lock_enabled", false)
        set(value) {
            preferences.edit().putBoolean("biometric_lock_enabled", value).apply()
        }

    val previousBudgetAmountMinor: Long
        get() = preferences.getLong("previous_budget_minor", 0)

    val currentPeriodBudgetConfirmed: Boolean
        get() = preferences.getString("budget_confirmed_key", null) == budgetConfirmationKey(budget)

    val onboardingComplete: Boolean
        get() = preferences.getBoolean("onboarding_complete", false)

    var budget: Budget
        get() {
            val period = when (preferences.getString("budget_period", "Month")) {
                "Week" -> "Week"
                else -> "Month"
            }
            return Budget(
                amountMinor = preferences.getLong("budget_minor", 0),
                period = period,
                repeats = preferences.getBoolean("budget_repeats", true),
                carryOver = preferences.getBoolean("budget_carry", false),
                startEpochDay = preferences.getLong("budget_start_day", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
                endEpochDay = preferences.getLong("budget_end_day", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
                categoryLimits = preferences.getStringSet("budget_limits", emptySet()).orEmpty().mapNotNull { value ->
                    val parts = value.split('=')
                    if (parts.size == 2) parts[1].toLongOrNull()?.let { parts[0] to it } else null
                }.toMap(),
                resetDay = preferences.getInt("reset_day", 1).coerceIn(if (period == "Week") 1..7 else 1..28),
                countInvestmentsAsSpending = preferences.getBoolean("count_investments_as_spending", false),
                commitments = preferences.getStringSet("budget_commitments", emptySet()).orEmpty().mapNotNull { encoded ->
                    val parts = encoded.split('|')
                    if (parts.size != 4) return@mapNotNull null
                    val amount = parts[2].toLongOrNull() ?: return@mapNotNull null
                    Commitment(id = parts[0], name = parts[1], monthlyAmountMinor = amount, enabled = parts[3] == "1")
                },
            )
        }
        private set(value) {
            val period = if (value.period == "Week") "Week" else "Month"
            val resetDay = value.resetDay.coerceIn(if (period == "Week") 1..7 else 1..28)
            val previousBudget = budget
            val rhythmOrResetChanged = previousBudget.period != period || previousBudget.resetDay != resetDay
            val editor = preferences.edit()
                .putLong("budget_minor", value.amountMinor.coerceAtLeast(0))
                .putString("budget_period", period)
                .putString("rhythm", if (period == "Week") "WEEKLY" else "MONTHLY")
                .putBoolean("budget_repeats", value.repeats)
                .putBoolean("budget_carry", value.carryOver)
                .putInt("reset_day", resetDay)
                .putBoolean("count_investments_as_spending", value.countInvestmentsAsSpending)
                .apply {
                    remove("budget_start_day")
                    remove("budget_end_day")
                }
                .putStringSet("budget_limits", value.categoryLimits.mapTo(mutableSetOf()) { "${it.key}=${it.value}" })
                .putStringSet(
                    "budget_commitments",
                    value.commitments.mapTo(mutableSetOf()) { "${it.id}|${it.name.replace('|', ' ')}|${it.monthlyAmountMinor}|${if (it.enabled) "1" else "0"}" },
                )
            if (value.amountMinor > 0) editor.putLong("previous_budget_minor", value.amountMinor)
            if (rhythmOrResetChanged) editor.remove("budget_confirmed_key")
            editor.apply()
            BudgetWidgetProvider.updateAll(context)
            categoryLimitNotifier.evaluate(activeBudget(), _transactions.value, onboardingComplete)
        }

    fun finishOnboarding(value: Budget) {
        budget = value
        preferences.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun finishOnboarding(budgetMinor: Long) = finishOnboarding(budget.copy(amountMinor = budgetMinor))

    fun updateBudget(value: Budget) {
        budget = value
    }

    fun confirmCurrentPeriodBudget() {
        val current = budget
        preferences.edit().putString("budget_confirmed_key", budgetConfirmationKey(current)).apply()
        categoryLimitNotifier.evaluate(current, _transactions.value, onboardingComplete)
        BudgetWidgetProvider.updateAll(context)
    }

    fun invalidateCurrentPeriodBudgetConfirmation() {
        preferences.edit().remove("budget_confirmed_key").apply()
        BudgetWidgetProvider.updateAll(context)
    }

    fun rerunOnboarding() {
        preferences.edit().putBoolean("onboarding_complete", false).apply()
    }

    fun refreshCategoryLimitAlerts() {
        categoryLimitNotifier.evaluate(activeBudget(), _transactions.value, onboardingComplete)
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
    fun addManual(amountMinor: Long, merchant: String, categoryId: String, direction: Direction, occurredAt: Long = System.currentTimeMillis(), committed: Boolean = false) {
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
            committed = committed,
        )
        database.insert(transaction)
        refresh()
    }

    @Synchronized
    fun addSms(sender: String, body: String, receivedAt: Long): Transaction? {
        val transaction = parseSms(sender, body, receivedAt) ?: return null
        if (!database.insert(transaction)) {
            val existing = transaction.refId?.let(database::findByRef) ?: return null
            if (existing.note == USER_EDITED_MARKER && (
                    existing.amountMinor != transaction.amountMinor ||
                        existing.direction != transaction.direction ||
                        existing.merchant != transaction.merchant ||
                        existing.categoryId != transaction.categoryId
                    )
            ) {
                val conflict = existing.copy(status = TransactionStatus.NEEDS_REVIEW)
                database.update(conflict)
                refresh()
                return conflict
            }
            val enriched = existing.copy(
                merchant = if (existing.merchant == "Uncategorised payment") transaction.merchant else existing.merchant,
                accountTail = existing.accountTail ?: transaction.accountTail,
                sender = existing.sender ?: transaction.sender,
                rawMessage = existing.rawMessage ?: transaction.rawMessage,
                categoryId = existing.categoryId ?: transaction.categoryId,
            )
            if (enriched != existing) {
                database.update(enriched)
                refresh()
            }
            return null
        }
        refresh()
        return transaction
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

    private fun backfillMerchantTitles() {
        if (preferences.getInt("merchant_title_backfill", 0) >= 2) return
        database.transactions().forEach { transaction ->
            if (transaction.merchant !in setOf("Uncategorised payment", "Unknown payment")) return@forEach
            val body = transaction.rawMessage ?: return@forEach
            val merchant = SmsParser.merchantFromBody(body, transaction.direction)?.let(SmsParser::normalizeMerchant).orEmpty()
            if (merchant.isNotBlank()) database.update(transaction.copy(merchant = merchant))
        }
        preferences.edit().putInt("merchant_title_backfill", 2).apply()
    }

    private fun parseSms(sender: String, body: String, receivedAt: Long): Transaction? {
        if (!smsTrackingEnabled) return null
        val parsed = SmsParser.parse(sender, body, receivedAt) ?: return null
        return parsed.toTransaction(sender, body)
    }

    private fun ParsedTransaction.toTransaction(sender: String, body: String): Transaction {
        val knownSender = SmsParser.senderKey(sender) != null && sender.any(Char::isLetter)
        val normalizedMerchant = merchant.trim().uppercase()
        val matchingRules = if (direction == Direction.DEBIT) _learnedRules.value.filter { it.merchant == normalizedMerchant } else emptyList()
        val learned = (matchingRules.firstOrNull { it.direction == Direction.DEBIT }
            ?: matchingRules.firstOrNull { it.direction == null && it.appliesTo(Direction.DEBIT) })?.categoryId
        val inferred = when {
            direction == Direction.CREDIT && isExplicitRefund -> "refund"
            direction == Direction.DEBIT -> learned ?: suggestedCategoryId ?: inferCategory(merchant)
            else -> null
        }
        val needsCategoryReview = direction == Direction.DEBIT && learned == null && (inferred == null || confidence < 0.75f)
        val categoryId = when {
            direction == Direction.DEBIT -> inferred ?: "misc"
            else -> inferred
        }
        val status = when {
            excludeByDefault -> TransactionStatus.EXCLUDED
            !knownSender -> TransactionStatus.NEEDS_REVIEW
            direction == Direction.CREDIT && isExplicitRefund -> TransactionStatus.CONFIRMED
            direction == Direction.CREDIT -> TransactionStatus.NEEDS_RESOLUTION
            learned != null -> TransactionStatus.CONFIRMED
            needsCategoryReview -> TransactionStatus.CATEGORY_REVIEW
            else -> TransactionStatus.CONFIRMED
        }
        val candidate = Transaction(
            id = UUID.randomUUID().toString(),
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = occurredAt,
            merchant = merchant,
            categoryId = categoryId,
            status = status,
            source = TransactionSource.SMS,
            accountTail = accountTail,
            sender = sender,
            refId = refId,
            sourceKey = sourceKey(sender, body),
            rawMessage = body,
            committed = autoDetectCommitted(categoryId, merchant, amountMinor, direction, database.transactions(), receipts.preferencesFlow.value.committedCategoryIds),
        )
        val refundMatch = if (categoryId == "refund") findRefundCandidate(database.transactions(), candidate)?.id else null
        return candidate.copy(refundOfId = refundMatch)
    }

    private fun budgetConfirmationKey(value: Budget): String {
        val range = periodKey(budgetRange(value))
        return "$range:${value.amountMinor}:${value.period}:${value.resetDay}"
    }

    private fun sourceKey(sender: String, body: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$sender|$body".toByteArray())
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
        categoryLimitNotifier.evaluate(activeBudget(), _transactions.value, onboardingComplete)
    }

    private fun activeBudget(): Budget = if (currentPeriodBudgetConfirmed) budget else budget.copy(amountMinor = 0L)

    companion object {
        const val USER_EDITED_MARKER = "__user_edited__"
    }
}
