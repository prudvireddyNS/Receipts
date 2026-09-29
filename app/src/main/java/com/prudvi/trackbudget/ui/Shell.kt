package com.prudvi.trackbudget.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.withRhythm
import com.prudvi.trackbudget.widget.BudgetWidgetProvider

private enum class ReceiptTab(val label: String) { HOME("Home"), HISTORY("History") }

private sealed interface ReceiptOverlay {
    data object Add : ReceiptOverlay
    data object Settings : ReceiptOverlay
    data class Budget(val returnToSettings: Boolean) : ReceiptOverlay
    data class Review(val initialTransactionId: String? = null) : ReceiptOverlay
    data class Detail(val id: String) : ReceiptOverlay
    data class Category(val id: String) : ReceiptOverlay
    data object Recap : ReceiptOverlay
}

@Composable
fun ReceiptsRoot(
    repository: TrackRepository,
    openReview: Boolean = false,
    initialReviewTransactionId: String? = null,
    onReviewConsumed: () -> Unit = {},
) {
    val preferences by repository.receipts.preferencesFlow.collectAsState()
    ReceiptsTheme(preferences.theme) {
        ReceiptsApp(repository, openReview, initialReviewTransactionId, onReviewConsumed)
    }
}

@Composable
private fun ReceiptsApp(
    repository: TrackRepository,
    openReview: Boolean = false,
    initialReviewTransactionId: String? = null,
    onReviewConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val transactions by repository.transactions.collectAsState()
    val learnedRules by repository.learnedRules.collectAsState()
    val preferences by repository.receipts.preferencesFlow.collectAsState()
    var onboarding by rememberSaveable { mutableStateOf(!repository.onboardingComplete) }
    var budget by remember { mutableStateOf(repository.budget) }
    var budgetConfirmed by remember { mutableStateOf(repository.currentPeriodBudgetConfirmed) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val today = rememberCurrentDate()

    LaunchedEffect(today) {
        budgetConfirmed = repository.currentPeriodBudgetConfirmed
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRevision++
        repository.receipts.updatePreferences(preferences.copy(smsTrackingEnabled = granted))
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRevision++
        if (granted) repository.refreshCategoryLimitAlerts()
    }
    val requestSms = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val smsLiveGranted = permissionRevision >= 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < 33 || permissionRevision >= 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricAvailable = remember(permissionRevision) { BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS }
    fun refreshBudgetState() {
        budget = repository.budget
        budgetConfirmed = repository.currentPeriodBudgetConfirmed
    }

    if (onboarding) {
        OnboardingScreen(
            smsGranted = smsLiveGranted,
            onRequestSms = requestSms,
            onFinish = { nextPreferences, nextBudget ->
                repository.receipts.updatePreferences(nextPreferences.copy(smsTrackingEnabled = nextPreferences.smsTrackingEnabled && smsLiveGranted))
                repository.finishOnboarding(nextBudget.withRhythm(nextPreferences.rhythm, nextPreferences.resetDay))
                repository.confirmCurrentPeriodBudget()
                refreshBudgetState()
                onboarding = false
            },
            initialPreferences = preferences,
            initialBudget = budget,
        )
        return
    }

    MainShell(
        transactions = transactions,
        learnedRules = learnedRules,
        budget = budget,
        budgetConfirmed = budgetConfirmed,
        previousBudgetAmountMinor = repository.previousBudgetAmountMinor,
        preferences = preferences,
        smsLiveGranted = smsLiveGranted,
        notificationsGranted = notificationsGranted,
        biometricAvailable = biometricAvailable,
        onRequestSms = requestSms,
        onRequestNotifications = requestNotifications,
        onAdd = { transaction ->
            repository.addManual(transaction.amountMinor, transaction.merchant, transaction.categoryId ?: "misc", transaction.direction, transaction.occurredAt)
            if (transaction.direction == Direction.DEBIT && canLearnMerchant(transaction.merchant)) {
                repository.addLearnedRule(transaction.merchant, transaction.categoryId ?: "misc", Direction.DEBIT)
            }
        },
        onSave = repository::save,
        onDelete = repository::delete,
        onBudget = { value ->
            val rhythm = if (value.period == "Week") MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
            repository.receipts.updatePreferences(preferences.copy(rhythm = rhythm, resetDay = value.resetDay))
            repository.updateBudget(value)
            repository.confirmCurrentPeriodBudget()
            refreshBudgetState()
        },
        onConfirmBudget = {
            repository.confirmCurrentPeriodBudget()
            refreshBudgetState()
        },
        onNoBudget = {
            repository.updateBudget(budget.copy(amountMinor = 0L, categoryLimits = emptyMap(), carryOver = false, repeats = true))
            repository.confirmCurrentPeriodBudget()
            refreshBudgetState()
        },
        onPreferences = { value ->
            val cleanRhythm = if (value.rhythm == MoneyRhythm.WEEKLY) MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
            val clean = value.copy(rhythm = cleanRhythm, resetDay = value.resetDay.coerceIn(if (cleanRhythm == MoneyRhythm.WEEKLY) 1..7 else 1..28))
            val rhythmChanged = clean.rhythm != preferences.rhythm || clean.resetDay != preferences.resetDay
            repository.receipts.updatePreferences(clean)
            if (rhythmChanged) repository.updateBudget(budget.withRhythm(clean.rhythm, clean.resetDay))
            refreshBudgetState()
        },
        onLearn = repository::addLearnedRule,
        onRemoveRule = repository::removeLearnedRule,
        onClear = repository::clearAll,
        onRerunOnboarding = {
            repository.rerunOnboarding()
            onboarding = true
        },
        openReview = openReview,
        initialReviewTransactionId = initialReviewTransactionId,
        onReviewConsumed = onReviewConsumed,
    )
}

@Composable
private fun MainShell(
    transactions: List<Transaction>,
    learnedRules: List<com.prudvi.trackbudget.model.LearnedRule>,
    budget: Budget,
    budgetConfirmed: Boolean,
    previousBudgetAmountMinor: Long,
    preferences: com.prudvi.trackbudget.model.ReceiptsPreferences,
    smsLiveGranted: Boolean,
    notificationsGranted: Boolean,
    biometricAvailable: Boolean,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onAdd: (Transaction) -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onBudget: (Budget) -> Unit,
    onConfirmBudget: () -> Unit,
    onNoBudget: () -> Unit,
    onPreferences: (com.prudvi.trackbudget.model.ReceiptsPreferences) -> Unit,
    onLearn: (String, String, Direction) -> Unit,
    onRemoveRule: (String) -> Unit,
    onClear: () -> Unit,
    onRerunOnboarding: () -> Unit,
    openReview: Boolean = false,
    initialReviewTransactionId: String? = null,
    onReviewConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ReceiptTab.HOME) }
    var overlay by remember { mutableStateOf<ReceiptOverlay?>(null) }
    val motionEnabled = rememberMotionEnabled()
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetProvider = remember { ComponentName(context, BudgetWidgetProvider::class.java) }
    val effectiveBudget = if (budgetConfirmed) budget else budget.copy(amountMinor = 0L)

    BackHandler(overlay != null) { overlay = null }
    BackHandler(overlay == null && tab != ReceiptTab.HOME) { tab = ReceiptTab.HOME }

    LaunchedEffect(openReview, initialReviewTransactionId) {
        if (openReview) {
            overlay = ReceiptOverlay.Review(initialReviewTransactionId)
            onReviewConsumed()
        }
    }
    LaunchedEffect(budgetConfirmed, openReview) {
        if (!budgetConfirmed && !openReview && overlay == null) overlay = ReceiptOverlay.Budget(returnToSettings = false)
    }

    Box(Modifier.fillMaxSize().background(receiptsColors.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val duration = if (motionEnabled) ReceiptsMotion.TAB else 0
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = tween<Float>(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    val slide = tween<androidx.compose.ui.unit.IntOffset>(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    (
                        slideInHorizontally(slide) { width -> if (forward) width else -width } +
                            fadeIn(enter) +
                            androidx.compose.animation.scaleIn(enter, initialScale = 0.92f)
                        ) togetherWith (
                        slideOutHorizontally(slide) { width -> if (forward) -width / 3 else width / 3 } +
                            fadeOut(tween(duration / 2, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                            androidx.compose.animation.scaleOut(enter, targetScale = 0.94f)
                        )
                },
                label = "receipt-tab",
            ) { current ->
                when (current) {
                    ReceiptTab.HOME -> TodayScreen(
                        transactions = transactions,
                        budget = effectiveBudget,
                        onSettings = { overlay = ReceiptOverlay.Settings },
                        onReview = { overlay = ReceiptOverlay.Review() },
                        onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
                        onCategory = { overlay = ReceiptOverlay.Category(it) },
                    )
                    ReceiptTab.HISTORY -> LedgerScreen(
                        transactions = transactions,
                        budget = effectiveBudget,
                        onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
                        onRecategorize = { tx, category -> onSave(tx.copy(categoryId = category, status = TransactionStatus.CONFIRMED)) },
                        onLearnRule = onLearn,
                    )
                }
            }
            ReceiptTabBar(tab, onSelect = { tab = it }, onAdd = { overlay = ReceiptOverlay.Add })
        }
        AnimatedContent(
            targetState = overlay,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val duration = if (motionEnabled) ReceiptsMotion.SHEET else 0
                val opening = initialState == null
                val fade = tween<Float>(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                val slide = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.86f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                )
                (
                    fadeIn(fade) +
                        slideInVertically(if (motionEnabled) slide else tween(0)) { height -> if (opening) height else height / 6 } +
                        androidx.compose.animation.scaleIn(fade, initialScale = 0.96f)
                    ) togetherWith (
                    fadeOut(tween(duration / 2)) +
                        slideOutVertically(tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { height -> height / 4 } +
                        androidx.compose.animation.scaleOut(fade, targetScale = 0.96f)
                    )
            },
            label = "receipt-overlay",
        ) { current ->
            when (current) {
                ReceiptOverlay.Add -> AddSheet(onClose = { overlay = null }, onSave = { onAdd(it); overlay = null }, learnedRules = learnedRules)
                ReceiptOverlay.Settings -> SettingsScreen(
                    preferences = preferences,
                    budget = budget,
                    transactions = transactions,
                    learnedRules = learnedRules,
                    smsLiveGranted = smsLiveGranted,
                    notificationsGranted = notificationsGranted,
                    biometricAvailable = biometricAvailable,
                    onClose = { overlay = null },
                    onPreferencesChange = onPreferences,
                    onRequestSms = onRequestSms,
                    onRequestNotifications = onRequestNotifications,
                    onEditBudget = { overlay = ReceiptOverlay.Budget(returnToSettings = true) },
                    onRemoveRule = onRemoveRule,
                    onPinWidget = {
                        if (widgetManager.getAppWidgetIds(widgetProvider).isEmpty()) widgetManager.requestPinAppWidget(widgetProvider, null, null)
                        overlay = null
                    },
                    onOpenRecap = { overlay = ReceiptOverlay.Recap },
                    onBudgetChange = onBudget,
                    onNoBudget = onNoBudget,
                    onRerunOnboarding = onRerunOnboarding,
                    onClearAll = onClear,
                    onExport = { exportReceipts(context, transactions) },
                    widgetInstalled = widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty(),
                    widgetPinSupported = widgetManager.isRequestPinAppWidgetSupported,
                )
                is ReceiptOverlay.Budget -> BudgetSheet(
                    budget = budget,
                    previousAmountMinor = previousBudgetAmountMinor,
                    onClose = { overlay = if (current.returnToSettings) ReceiptOverlay.Settings else null },
                    onSave = { onBudget(it); overlay = if (current.returnToSettings) ReceiptOverlay.Settings else null },
                )
                is ReceiptOverlay.Review -> ReviewDeck(
                    transactions = transactions,
                    onClose = { overlay = null },
                    onSave = onSave,
                    onLearn = onLearn,
                    initialTransactionId = current.initialTransactionId,
                    onGoHome = { overlay = null; tab = ReceiptTab.HOME },
                    onGoAdd = { overlay = ReceiptOverlay.Add },
                    onGoHistory = { overlay = null; tab = ReceiptTab.HISTORY },
                )
                is ReceiptOverlay.Detail -> transactions.firstOrNull { it.id == current.id }?.let { tx ->
                    TransactionEditorOverlay(
                        transaction = tx,
                        onClose = { overlay = null },
                        onSave = {
                            onSave(it.copy(note = if (it.source == com.prudvi.trackbudget.model.TransactionSource.SMS) TrackRepository.USER_EDITED_MARKER else it.note))
                            if (it.status != TransactionStatus.EXCLUDED && it.direction == Direction.DEBIT && it.categoryId != tx.categoryId && canLearnMerchant(it.merchant)) {
                                onLearn(it.merchant, it.categoryId ?: "misc", Direction.DEBIT)
                            }
                            overlay = null
                        },
                        onDelete = { onDelete(it); overlay = null },
                    )
                }
                is ReceiptOverlay.Category -> CategoryDetailSheet(
                    categoryId = current.id,
                    transactions = transactions,
                    budget = effectiveBudget,
                    onClose = { overlay = null },
                    onTransaction = { overlay = ReceiptOverlay.Detail(it.id) },
                )
                ReceiptOverlay.Recap -> CurrentRecapScreen(transactions = transactions, budget = effectiveBudget, onClose = { overlay = ReceiptOverlay.Settings }, onDone = { overlay = null; tab = ReceiptTab.HOME })
                null -> Unit
            }
        }
    }
}

@Composable
private fun ReceiptTabBar(selected: ReceiptTab, onSelect: (ReceiptTab) -> Unit, onAdd: () -> Unit) {
    val colors = receiptsColors
    Column(
        Modifier.fillMaxWidth()
            .background(colors.warm)
            .drawBehind { drawLine(colors.ink, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = ReceiptsStroke.width.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavTab(ReceiptTab.HOME, Icons.Default.Home, selected, Modifier.weight(1f), onSelect)
            val addInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val addPressed by addInteraction.collectIsPressedAsState()
            val addScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (addPressed) 0.86f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                ),
                label = "add-scale",
            )
            val addSpin by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (addPressed) 90f else 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
                label = "add-spin",
            )
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .clickable(interactionSource = addInteraction, indication = null, role = Role.Button, onClick = onAdd)
                    .semantics { role = Role.Button; contentDescription = "Add receipt" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp)
                        .graphicsLayer { scaleX = addScale; scaleY = addScale; rotationZ = addSpin }
                        .mockShadow(ReceiptsRadius.pill, 3.dp, 3.dp, receiptsColors.ink)
                        .clip(RoundedCornerShape(ReceiptsRadius.pill))
                        .background(receiptsColors.yellow)
                        .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = receiptsColors.chromeOn, style = ReceiptsType.heading.copy(fontSize = 23.sp), maxLines = 1)
                }
            }
            NavTab(ReceiptTab.HISTORY, Icons.Default.History, selected, Modifier.weight(1f), onSelect)
        }
    }
}

@Composable
private fun NavTab(tab: ReceiptTab, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: ReceiptTab, modifier: Modifier, onSelect: (ReceiptTab) -> Unit) {
    val active = tab == selected
    val motionEnabled = rememberMotionEnabled()
    val spec = androidx.compose.animation.core.spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
    )
    val emphasis by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (motionEnabled) spec else androidx.compose.animation.core.snap(),
        label = "nav-emphasis",
    )
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) receiptsColors.ink else receiptsColors.fade,
        animationSpec = tween(if (motionEnabled) ReceiptsMotion.TAB else 0),
        label = "nav-tint",
    )
    Column(
        modifier.defaultMinSize(minHeight = 44.dp)
            .selectable(active, role = Role.Tab) { onSelect(tab) }
            .semantics { contentDescription = tab.label; role = Role.Tab }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(30.dp).graphicsLayer {
                val lift = 1f + 0.14f * emphasis
                scaleX = lift
                scaleY = lift
                translationY = -4.dp.toPx() * emphasis
            },
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(3.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth(emphasis).height(3.dp)
                    .clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .background(receiptsColors.pink),
            )
        }
    }
}
