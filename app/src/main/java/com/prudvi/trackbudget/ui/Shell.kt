package com.prudvi.trackbudget.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.TrackBudgetApplication
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.withRhythm
import com.prudvi.trackbudget.widget.BudgetWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class ReceiptTab(val label: String) { TODAY("Today"), FEED("Feed"), GOALS("Goals"), LEDGER("Ledger") }

private sealed interface ReceiptOverlay {
    data object Add : ReceiptOverlay
    data object Settings : ReceiptOverlay
    data object Budget : ReceiptOverlay
    data object Review : ReceiptOverlay
    data class Detail(val id: String) : ReceiptOverlay
    data class Category(val id: String) : ReceiptOverlay
    data class Wrapped(val periodKey: String) : ReceiptOverlay
}

@Composable
fun ReceiptsRoot(repository: TrackRepository) {
    val preferences by repository.receipts.preferencesFlow.collectAsState()
    ReceiptsTheme(preferences.theme) {
        ReceiptsApp(repository)
    }
}

@Composable
private fun ReceiptsApp(repository: TrackRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by repository.transactions.collectAsState()
    val learnedRules by repository.learnedRules.collectAsState()
    val ignoredSources by repository.ignoredSources.collectAsState()
    val goals by repository.receipts.goals.collectAsState()
    val stamps by repository.receipts.earnedStamps.collectAsState()
    val snapshots by repository.receipts.periodSnapshots.collectAsState()
    val preferences by repository.receipts.preferencesFlow.collectAsState()
    val drops by repository.receipts.drops.collectAsState()
    var onboarding by rememberSaveable { mutableStateOf(!repository.onboardingComplete) }
    var budget by remember { mutableStateOf(repository.budget) }
    var scanning by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }

    fun scanInbox(force: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            scanning = true
            importedCount = withContext(Dispatchers.IO) { repository.importInbox(force = force) }
            scanning = false
        }
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRevision++
        if (result[Manifest.permission.READ_SMS] == true) {
            (context.applicationContext as TrackBudgetApplication).startSmsObserver()
            scanInbox(force = true)
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRevision++
        if (granted) repository.refreshCategoryLimitAlerts()
    }
    val requestSms = { smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val smsInboxGranted = permissionRevision >= 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val smsLiveGranted = permissionRevision >= 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < 33 || permissionRevision >= 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (smsInboxGranted) scanInbox()
    }

    if (onboarding) {
        OnboardingScreen(
            smsGranted = smsInboxGranted && smsLiveGranted,
            onRequestSms = requestSms,
            onFinish = { nextPreferences, nextBudget, goal ->
                repository.receipts.updatePreferences(nextPreferences)
                repository.finishOnboarding(nextBudget.withRhythm(nextPreferences.rhythm, nextPreferences.resetDay))
                budget = repository.budget
                goal?.let { repository.receipts.addGoal(it.name, it.targetMinor, it.targetEpochDay) }
                onboarding = false
            },
            initialPreferences = preferences,
            initialBudget = budget,
            scanning = scanning,
            importedCount = importedCount,
        )
        return
    }

    MainShell(
        transactions = transactions,
        learnedRules = learnedRules,
        ignoredSources = ignoredSources,
        budget = budget,
        goals = goals,
        stamps = stamps,
        snapshots = snapshots,
        drops = drops,
        preferences = preferences,
        smsInboxGranted = smsInboxGranted,
        smsLiveGranted = smsLiveGranted,
        notificationsGranted = notificationsGranted,
        scanning = scanning,
        importedCount = importedCount,
        onRequestSms = requestSms,
        onRequestNotifications = requestNotifications,
        onScan = { if (smsInboxGranted) scanInbox(force = true) else requestSms() },
        onAdd = { transaction -> repository.addManual(transaction.amountMinor, transaction.merchant, transaction.categoryId ?: "misc", transaction.direction, transaction.occurredAt) },
        onSave = repository::save,
        onDelete = repository::delete,
        onBudget = { value -> repository.updateBudget(value); budget = repository.budget },
        onPreferences = { value ->
            val rhythmChanged = value.rhythm != preferences.rhythm || value.resetDay != preferences.resetDay
            repository.receipts.updatePreferences(value)
            if (rhythmChanged) {
                repository.updateBudget(budget.withRhythm(value.rhythm, value.resetDay))
                budget = repository.budget
            }
        },
        onLearn = repository::addLearnedRule,
        onRemoveRule = repository::removeLearnedRule,
        onSourceIgnored = repository::setSourceIgnored,
        onClear = repository::clearAll,
        onDismissDrop = { repository.receipts.dismissDrop(it) },
        onShared = { repository.receipts.markShared() },
        onAddGoal = { name, target, day -> repository.receipts.addGoal(name, target, day) },
        onAddProgress = { id, amount -> repository.receipts.addGoalProgress(id, amount) },
        onDeleteGoal = repository.receipts::deleteGoal,
        onStampSeen = repository.receipts::markStampSeen,
        wrappedWasSeen = repository.receipts::wrappedWasSeen,
        onWrappedSeen = repository.receipts::markWrappedSeen,
    )
}

@Composable
private fun MainShell(
    transactions: List<Transaction>,
    learnedRules: List<com.prudvi.trackbudget.model.LearnedRule>,
    ignoredSources: Set<String>,
    budget: Budget,
    goals: List<Goal>,
    stamps: List<com.prudvi.trackbudget.model.EarnedStamp>,
    snapshots: List<com.prudvi.trackbudget.model.PeriodSnapshot>,
    drops: List<com.prudvi.trackbudget.model.Drop>,
    preferences: com.prudvi.trackbudget.model.ReceiptsPreferences,
    smsInboxGranted: Boolean,
    smsLiveGranted: Boolean,
    notificationsGranted: Boolean,
    scanning: Boolean,
    importedCount: Int?,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onScan: () -> Unit,
    onAdd: (Transaction) -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onBudget: (Budget) -> Unit,
    onPreferences: (com.prudvi.trackbudget.model.ReceiptsPreferences) -> Unit,
    onLearn: (String, String, Direction) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSourceIgnored: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismissDrop: (String) -> Unit,
    onShared: () -> Unit,
    onAddGoal: (String, Long, Long?) -> Unit,
    onAddProgress: (String, Long) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onStampSeen: (String) -> Unit,
    wrappedWasSeen: (String) -> Boolean,
    onWrappedSeen: (String) -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ReceiptTab.TODAY) }
    var overlay by remember { mutableStateOf<ReceiptOverlay?>(null) }
    val motionEnabled = rememberMotionEnabled()
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetProvider = remember { ComponentName(context, BudgetWidgetProvider::class.java) }
    val latestSnapshot = snapshots.maxByOrNull { it.closedAt }
    val unseenStamp = stamps.firstOrNull { !it.seen }
    val today = rememberCurrentDate()

    BackHandler(overlay != null) {
        when (val current = overlay) {
            is ReceiptOverlay.Wrapped -> {
                onWrappedSeen(current.periodKey)
                overlay = null
            }
            ReceiptOverlay.Budget -> overlay = ReceiptOverlay.Settings
            else -> overlay = null
        }
    }
    BackHandler(overlay == null && tab != ReceiptTab.TODAY) { tab = ReceiptTab.TODAY }

    LaunchedEffect(today, latestSnapshot?.periodKey, preferences.amplitude) {
        val latest = latestSnapshot ?: return@LaunchedEffect
        if (today.dayOfMonth == 1 && preferences.amplitude == AppAmplitude.LOUD && !wrappedWasSeen(latest.periodKey)) {
            overlay = ReceiptOverlay.Wrapped(latest.periodKey)
        }
    }

    Box(Modifier.fillMaxSize().background(receiptsColors.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val duration = if (motionEnabled) ReceiptsMotion.TAB else 0
                    fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                },
                label = "receipt-tab",
            ) { current ->
                when (current) {
                    ReceiptTab.TODAY -> TodayScreen(
                        transactions = transactions,
                        budget = budget,
                        mode = preferences.mode,
                        amplitude = preferences.amplitude,
                        onSettings = { overlay = ReceiptOverlay.Settings },
                        onAdd = { overlay = ReceiptOverlay.Add },
                        onReview = { overlay = ReceiptOverlay.Review },
                        onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
                        onCategory = { overlay = ReceiptOverlay.Category(it) },
                        onRescan = onScan,
                        goals = goals,
                        periodSnapshots = snapshots,
                        scanning = scanning,
                    )
                    ReceiptTab.FEED -> FeedScreen(
                        drops,
                        wrappedEntries = snapshots.sortedByDescending { it.closedAt }.map {
                            WrappedFeedEntry(it.periodKey, "${wrappedMonth(it.periodKey)} Wrapped", seen = wrappedWasSeen(it.periodKey))
                        },
                        onOpenWrapped = { overlay = ReceiptOverlay.Wrapped(it.periodKey) },
                        onAdd = { overlay = ReceiptOverlay.Add },
                        onDropShared = { onShared() },
                        onNotUseful = { onDismissDrop(it.ruleKey) },
                    )
                    ReceiptTab.GOALS -> GoalsScreen(goals, stamps, transactions.size, onAddGoal, onAddProgress, onDeleteGoal)
                    ReceiptTab.LEDGER -> LedgerScreen(
                        transactions,
                        onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
                        onAdd = { overlay = ReceiptOverlay.Add },
                        onRecategorize = { tx, category -> onSave(tx.copy(categoryId = category, status = TransactionStatus.CONFIRMED)) },
                        onLearnRule = onLearn,
                    )
                }
            }
            ReceiptTabBar(tab) { tab = it }
        }
        AnimatedContent(
            targetState = overlay,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val duration = if (motionEnabled) ReceiptsMotion.SHEET else 0
                (slideInVertically(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                    (slideOutVertically(tween(duration)) { it } + fadeOut(tween(duration)))
            },
            label = "receipt-overlay",
        ) { current -> when (current) {
            ReceiptOverlay.Add -> AddSheet(onClose = { overlay = null }, onSave = { onAdd(it); overlay = null })
            ReceiptOverlay.Settings -> SettingsScreen(
                preferences, budget, transactions, learnedRules, ignoredSources, smsInboxGranted, smsLiveGranted,
                notificationsGranted, scanning, importedCount, { overlay = null }, onPreferences, onRequestSms,
                onRequestNotifications, onScan, { overlay = ReceiptOverlay.Budget }, onSourceIgnored, onRemoveRule,
                onPinWidget = {
                    if (widgetManager.getAppWidgetIds(widgetProvider).isEmpty()) {
                        widgetManager.requestPinAppWidget(widgetProvider, null, null)
                    }
                },
                onClearAll = onClear,
                widgetInstalled = widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty(),
                widgetPinSupported = widgetManager.isRequestPinAppWidgetSupported,
            )
            ReceiptOverlay.Budget -> BudgetSheet(
                budget = budget,
                transactions = transactions,
                onClose = { overlay = ReceiptOverlay.Settings },
                onSave = { onBudget(it); overlay = ReceiptOverlay.Settings },
            )
            ReceiptOverlay.Review -> ReviewDeck(transactions = transactions, onClose = { overlay = null }, onSave = onSave, onLearn = onLearn)
            is ReceiptOverlay.Detail -> transactions.firstOrNull { it.id == current.id }?.let { tx ->
                TransactionDetailSheet(
                    transaction = tx,
                    onClose = { overlay = null },
                    onSave = onSave,
                    onDelete = { onDelete(it); overlay = null },
                )
            }
            is ReceiptOverlay.Category -> CategoryDetailSheet(
                categoryId = current.id,
                transactions = transactions,
                budget = budget,
                onClose = { overlay = null },
                onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
            )
            is ReceiptOverlay.Wrapped -> WrappedScreen(transactions, budget, stamps, snapshots, {
                onWrappedSeen(current.periodKey)
                overlay = null
            }, onShared = onShared, periodKey = current.periodKey)
            null -> Unit
        } }
        unseenStamp?.let { stamp ->
            StampMoment(earnedStamp = stamp, amplitude = preferences.amplitude, onClose = { onStampSeen(stamp.id) })
        }
    }
}

@Composable
private fun ReceiptTabBar(selected: ReceiptTab, onSelect: (ReceiptTab) -> Unit) {
    val tabStyle = if (LocalDensity.current.fontScale > 1.5f) ReceiptsType.label.copy(letterSpacing = 0.sp) else ReceiptsType.label
    Column(Modifier.fillMaxWidth().background(receiptsColors.paper).windowInsetsPadding(WindowInsets.navigationBars)) {
        ReceiptPerforation()
        Row(Modifier.fillMaxWidth()) {
            ReceiptTab.entries.forEach { tab ->
                Column(
                    Modifier.weight(1f).defaultMinSize(minHeight = ReceiptsSpace.x12)
                        .selectable(tab == selected, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = ReceiptsSpace.x2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x1),
                ) {
                    Text(tab.label.uppercase(), color = if (tab == selected) receiptsColors.ink else receiptsColors.fade, style = tabStyle, maxLines = 1)
                    Box(Modifier.size(if (tab == selected) ReceiptsSpace.x3 else ReceiptsSpace.none, ReceiptsSpace.x1 / 2f).background(if (tab == selected) receiptsColors.chilli else receiptsColors.paper))
                }
            }
        }
    }
}

private fun wrappedMonth(periodKey: String): String = runCatching {
    java.time.YearMonth.parse(periodKey).month.name.lowercase().replaceFirstChar(Char::titlecase)
}.getOrDefault("Latest")
