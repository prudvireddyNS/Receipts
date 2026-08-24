package com.prudvi.trackbudget.data

import android.content.Context
import android.content.SharedPreferences
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.BudgetRange
import com.prudvi.trackbudget.model.DismissedDropRule
import com.prudvi.trackbudget.model.Drop
import com.prudvi.trackbudget.model.DropEngine
import com.prudvi.trackbudget.model.EarnedStamp
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.PeriodSnapshot
import com.prudvi.trackbudget.model.ReceiptsPreferences
import com.prudvi.trackbudget.model.StampEngine
import com.prudvi.trackbudget.model.StampEvaluationInput
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodKey
import com.prudvi.trackbudget.model.spendingAnalytics
import com.prudvi.trackbudget.model.toLocalDate
import com.prudvi.trackbudget.model.withRhythm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class ReceiptsRepository(
    context: Context,
    private val database: TrackDatabase,
    private val preferences: SharedPreferences,
) {
    private val installedAt = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(System.currentTimeMillis())
    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    private val _earnedStamps = MutableStateFlow<List<EarnedStamp>>(emptyList())
    private val _periodSnapshots = MutableStateFlow<List<PeriodSnapshot>>(emptyList())
    private val _preferences = MutableStateFlow(loadPreferences())
    private val _drops = MutableStateFlow<List<Drop>>(emptyList())
    private var lastTransactions: List<Transaction> = emptyList()
    private var lastBudget = Budget()

    init {
        refreshStored()
    }

    val goals: StateFlow<List<Goal>> = _goals
    val earnedStamps: StateFlow<List<EarnedStamp>> = _earnedStamps
    val periodSnapshots: StateFlow<List<PeriodSnapshot>> = _periodSnapshots
    val preferencesFlow: StateFlow<ReceiptsPreferences> = _preferences
    val drops: StateFlow<List<Drop>> = _drops

    @Synchronized
    fun refreshFeatures(transactions: List<Transaction>, budget: Budget, now: Long = System.currentTimeMillis()) {
        lastTransactions = transactions
        lastBudget = budget.copy(resetDay = _preferences.value.resetDay)
        createClosedMonthlySnapshots(transactions, lastBudget, now)
        val reviewQueueCleared = updateReviewState(transactions, lastBudget, now)
        refreshStored()
        val analytics = spendingAnalytics(transactions, lastBudget, now)
        _drops.value = selectDropBatch(DropEngine.evaluate(transactions, analytics, activeDismissedRuleKeys(now)), now)
        earnStamps(transactions, lastBudget, reviewQueueCleared, now)
    }

    @Synchronized
    fun addGoal(name: String, targetMinor: Long, targetEpochDay: Long? = null, now: Long = System.currentTimeMillis()): Goal {
        require(name.isNotBlank()) { "Goal name is required" }
        val goal = Goal(UUID.randomUUID().toString(), name.trim(), targetMinor, targetEpochDay = targetEpochDay, createdAt = now)
        database.upsertGoal(goal)
        refreshFeatures(lastTransactions, lastBudget, now)
        return goal
    }

    @Synchronized
    fun updateGoal(goal: Goal, now: Long = System.currentTimeMillis()) {
        val completed = if (goal.savedMinor >= goal.targetMinor && goal.completedAt == null) goal.copy(completedAt = now) else goal
        database.upsertGoal(completed)
        refreshFeatures(lastTransactions, lastBudget, now)
    }

    fun addGoalProgress(id: String, amountMinor: Long, now: Long = System.currentTimeMillis()) {
        val goal = database.goals().firstOrNull { it.id == id } ?: return
        updateGoal(goal.copy(savedMinor = (goal.savedMinor + amountMinor).coerceAtLeast(0)), now)
    }

    @Synchronized
    fun deleteGoal(id: String) {
        database.deleteGoal(id)
        refreshFeatures(lastTransactions, lastBudget)
    }

    @Synchronized
    fun markStampSeen(id: String) {
        database.markStampSeen(id)
        refreshStored()
    }

    @Synchronized
    fun markAllStampsSeen() {
        database.earnedStamps().filterNot { it.seen }.forEach { database.markStampSeen(it.id) }
        refreshStored()
    }

    @Synchronized
    fun dismissDrop(ruleKey: String, now: Long = System.currentTimeMillis()) {
        database.upsertDismissedDropRule(DismissedDropRule(ruleKey, now))
        refreshFeatures(lastTransactions, lastBudget, now)
    }

    @Synchronized
    fun updatePreferences(value: ReceiptsPreferences) {
        val clean = value.copy(resetDay = value.resetDay.coerceIn(1, 28))
        val rhythmChanged = clean.rhythm != _preferences.value.rhythm || clean.resetDay != _preferences.value.resetDay
        val editor = preferences.edit()
            .putString(MODE, clean.mode.name)
            .putString(RHYTHM, clean.rhythm.name)
            .putString(AMPLITUDE, clean.amplitude.name)
            .putInt(RESET_DAY, clean.resetDay)
            .putString(THEME, clean.theme.name)
        if (rhythmChanged) editor.putString("budget_period", clean.withBudgetPeriod())
        editor.apply()
        _preferences.value = clean
        if (rhythmChanged) lastBudget = lastBudget.withRhythm(clean.rhythm, clean.resetDay)
        refreshFeatures(lastTransactions, lastBudget)
    }

    fun markShared(now: Long = System.currentTimeMillis()) {
        database.insertEarnedStamp(EarnedStamp("receipts", now, null))
        refreshStored()
    }

    fun clearTransactionHistoryState() {
        val editor = preferences.edit()
            .remove(DROP_BATCH)
            .remove(DROP_BATCH_KEYS)
            .remove(DELIVERED_DROPS)
            .remove(REVIEW_PERIOD)
            .remove(REVIEW_COUNT)
        preferences.all.keys.filter { it.startsWith(REVIEW_PEAK_PREFIX) }.forEach(editor::remove)
        editor.apply()
        _drops.value = emptyList()
        refreshStored()
    }

    fun markSmsSynced(now: Long = System.currentTimeMillis()) {
        preferences.edit().putBoolean(HAS_SYNCED, true).apply()
        earnStamps(lastTransactions, lastBudget, reviewQueueCleared = false, now = now)
    }

    fun markWrappedSeen(periodKey: String) {
        val seen = preferences.getStringSet(WRAPPED_SEEN, emptySet()).orEmpty().toMutableSet().apply { add(periodKey) }
        preferences.edit().putStringSet(WRAPPED_SEEN, seen).apply()
        earnStamps(lastTransactions, lastBudget, reviewQueueCleared = false, now = System.currentTimeMillis())
    }

    fun wrappedWasSeen(periodKey: String): Boolean = periodKey in preferences.getStringSet(WRAPPED_SEEN, emptySet()).orEmpty()

    private fun createClosedMonthlySnapshots(transactions: List<Transaction>, budget: Budget, now: Long) {
        val zone = ZoneId.systemDefault()
        val currentMonth = now.toLocalDate(zone).withDayOfMonth(1)
        val previousMonth = currentMonth.minusMonths(1)
        transactions.asSequence()
            .map { it.occurredAt.toLocalDate(zone).withDayOfMonth(1) }
            .distinct()
            .filter { it.isBefore(currentMonth) }
            .forEach { start ->
                val range = BudgetRange(start, start.withDayOfMonth(start.lengthOfMonth()))
                val startMillis = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
                val endExclusive = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                if (transactions.none { it.occurredAt in startMillis until endExclusive }) return@forEach
                val knownBudget = if (start == previousMonth) budget.amountMinor else 0L
                val snapshotBudget = budget.copy(period = "Custom", startEpochDay = range.start.toEpochDay(), endEpochDay = range.endInclusive.toEpochDay())
                database.insertPeriodSnapshot(
                    PeriodSnapshot(periodKey(range), knownBudget, dashboard(transactions, snapshotBudget, endExclusive - 1).spentMinor, endExclusive - 1),
                )
            }
    }

    private fun updateReviewState(transactions: List<Transaction>, budget: Budget, now: Long): Boolean {
        val period = periodKey(com.prudvi.trackbudget.model.budgetRange(budget, now.toLocalDate()))
        val peakKey = REVIEW_PEAK_PREFIX + period
        val count = transactions.count { it.status in REVIEW_STATUSES }
        val previousPeriod = preferences.getString(REVIEW_PERIOD, null)
        val previousCount = preferences.getInt(REVIEW_COUNT, count)
        val cleared = previousPeriod == period && previousCount > 0 && count == 0
        val editor = preferences.edit().putString(REVIEW_PERIOD, period).putInt(REVIEW_COUNT, count)
        if (count > preferences.getInt(peakKey, 0)) editor.putInt(peakKey, count)
        editor.apply()
        return cleared
    }

    private fun earnStamps(transactions: List<Transaction>, budget: Budget, reviewQueueCleared: Boolean, now: Long) {
        val existing = database.earnedStamps().mapTo(mutableSetOf()) { it.id }
        val peaks = preferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(REVIEW_PEAK_PREFIX) && value is Int) key.removePrefix(REVIEW_PEAK_PREFIX) to value else null
        }.toMap()
        val firstRun = !preferences.getBoolean(INITIALIZED, false)
        StampEngine.evaluate(
            StampEvaluationInput(
                transactions = transactions,
                budget = budget,
                goals = database.goals(),
                periodSnapshots = database.periodSnapshots(),
                installedAt = installedAt,
                hasSynced = preferences.getBoolean(HAS_SYNCED, false),
                reviewQueueCleared = reviewQueueCleared,
                reviewQueuePeakByPeriod = peaks,
                wrappedCount = preferences.getStringSet(WRAPPED_SEEN, emptySet()).orEmpty().size,
                now = now,
            ),
        ).filterNot { it.id in existing }.forEach { database.insertEarnedStamp(it.copy(seen = firstRun)) }
        if (firstRun) preferences.edit().putBoolean(INITIALIZED, true).apply()
        _earnedStamps.value = database.earnedStamps()
    }

    private fun activeDismissedRuleKeys(now: Long): Set<String> {
        val cutoff = now - DROP_DISMISSAL_MS
        return database.dismissedDropRules().filter { it.dismissedAt >= cutoff }.mapTo(mutableSetOf()) { it.ruleKey }
    }

    private fun selectDropBatch(available: List<Drop>, now: Long): List<Drop> {
        val today = now.toLocalDate()
        val cadence = if (_preferences.value.amplitude == AppAmplitude.LOUD) {
            "day:$today"
        } else {
            "week:${today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))}"
        }
        val sameBatch = preferences.getString(DROP_BATCH, null) == cadence
        val selected = if (sameBatch) preferences.getStringSet(DROP_BATCH_KEYS, emptySet()).orEmpty().toMutableSet() else mutableSetOf()
        val delivered = preferences.getStringSet(DELIVERED_DROPS, emptySet()).orEmpty().toMutableSet()
        available.asSequence().filterNot { it.key in delivered }.take((3 - selected.size).coerceAtLeast(0)).forEach {
            selected += it.key
            delivered += it.key
        }
        preferences.edit().putString(DROP_BATCH, cadence).putStringSet(DROP_BATCH_KEYS, selected).putStringSet(DELIVERED_DROPS, delivered).apply()
        return available.filter { it.key in selected }.take(3)
    }

    private fun refreshStored() {
        _goals.value = database.goals()
        _earnedStamps.value = database.earnedStamps()
        _periodSnapshots.value = database.periodSnapshots()
        _preferences.value = loadPreferences()
    }

    private fun loadPreferences(): ReceiptsPreferences {
        val existingUser = preferences.getBoolean("onboarding_complete", false)
        return ReceiptsPreferences(
            mode = preferences.enum(MODE, if (existingUser) AppMode.PACE else AppMode.CHILL),
            rhythm = preferences.getString(RHYTHM, null)?.let { enumValueOrDefault(it, MoneyRhythm.MONTHLY) }
                ?: when (preferences.getString("budget_period", "Month")) {
                    "Week" -> MoneyRhythm.WEEKLY
                    "Rolling" -> MoneyRhythm.ROLLING
                    else -> MoneyRhythm.MONTHLY
                },
            amplitude = preferences.enum(AMPLITUDE, AppAmplitude.LOUD),
            resetDay = preferences.getInt(RESET_DAY, 1).coerceIn(1, 28),
            theme = preferences.enum(THEME, AppThemePreference.LIGHT),
        )
    }

    private fun ReceiptsPreferences.withBudgetPeriod(): String = when (rhythm) {
        MoneyRhythm.MONTHLY -> "Month"
        MoneyRhythm.WEEKLY -> "Week"
        MoneyRhythm.ROLLING -> "Rolling"
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
        getString(key, default.name)?.let { enumValueOrDefault(it, default) } ?: default

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private companion object {
        const val MODE = "mode"
        const val RHYTHM = "rhythm"
        const val AMPLITUDE = "amplitude"
        const val RESET_DAY = "reset_day"
        const val THEME = "theme"
        const val INITIALIZED = "receipts_initialized"
        const val WRAPPED_SEEN = "wrapped_seen"
        const val HAS_SYNCED = "sms_synced"
        const val REVIEW_PERIOD = "review_period"
        const val REVIEW_COUNT = "review_count"
        const val REVIEW_PEAK_PREFIX = "review_peak_"
        const val DROP_BATCH = "drop_batch"
        const val DROP_BATCH_KEYS = "drop_batch_keys"
        const val DELIVERED_DROPS = "delivered_drops"
        const val DROP_DISMISSAL_MS = 60L * 86_400_000L
        val REVIEW_STATUSES = setOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE)
    }
}
