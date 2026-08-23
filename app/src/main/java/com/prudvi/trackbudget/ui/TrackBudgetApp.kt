package com.prudvi.trackbudget.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.TrackBudgetApplication
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.CategoryLimitLevel
import com.prudvi.trackbudget.model.CategoryLimitStatus
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.SpendingAnalytics
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.categoryLimitStatuses
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import com.prudvi.trackbudget.model.spendingAnalytics
import com.prudvi.trackbudget.widget.BudgetWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tab(val label: String, val glyph: String) {
    Home("Home", "⌂"), Timeline("Timeline", "≡"), Insights("Insights", "▥"), Settings("Settings", "⚙"),
}

private sealed interface Overlay {
    data object Add : Overlay
    data object BudgetEditor : Overlay
    data object Review : Overlay
    data class Credit(val id: String) : Overlay
    data class Detail(val id: String) : Overlay
    data class CategoryDetail(val id: String) : Overlay
}

@Composable
fun TrackBudgetApp(repository: TrackRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by repository.transactions.collectAsState()
    val learnedRules by repository.learnedRules.collectAsState()
    val ignoredSources by repository.ignoredSources.collectAsState()
    var onboarding by rememberSaveable { mutableStateOf(!repository.onboardingComplete) }
    var budget by remember { mutableStateOf(repository.budget) }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }

    fun scanInbox() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            scanning = true
            importedCount = withContext(Dispatchers.IO) { repository.importInbox() }
            scanning = false
        }
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) {
            (context.applicationContext as TrackBudgetApplication).startSmsObserver()
            scanInbox()
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) repository.refreshCategoryLimitAlerts()
    }
    val requestSms = { smsPermission.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            scanInbox()
        }
    }

    if (onboarding) {
        Onboarding(
            transactions = transactions,
            scanning = scanning,
            importedCount = importedCount,
            ignoredSources = ignoredSources,
            onEnableSms = requestSms,
            onSourceIgnored = repository::setSourceIgnored,
            onFinish = {
                repository.finishOnboarding(it)
                budget = repository.budget
                onboarding = false
            },
        )
    } else {
        MainShell(
            transactions = transactions,
            learnedRules = learnedRules,
            ignoredSources = ignoredSources,
            budget = budget,
            inboxGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED,
            liveSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            notificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            scanning = scanning,
            importedCount = importedCount,
            onRequestSms = requestSms,
            onRequestNotifications = requestNotifications,
            onScan = ::scanInbox,
            onAdd = repository::addManual,
            onSave = repository::save,
            onDelete = repository::delete,
            onBudget = {
                repository.updateBudget(it)
                budget = repository.budget
            },
            onLearn = repository::addLearnedRule,
            onRemoveRule = repository::removeLearnedRule,
            onSourceIgnored = repository::setSourceIgnored,
            onSamples = repository::addSampleData,
            onClear = repository::clearAll,
        )
    }
}

@Composable
private fun Onboarding(
    transactions: List<Transaction>,
    scanning: Boolean,
    importedCount: Int?,
    ignoredSources: Set<String>,
    onEnableSms: () -> Unit,
    onSourceIgnored: (String, Boolean) -> Unit,
    onFinish: (Budget) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var manualOnly by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("30000") }
    var period by rememberSaveable { mutableStateOf("Month") }
    var customStart by remember { mutableStateOf(LocalDate.now()) }
    var customEnd by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    val context = LocalContext.current
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val accounts = transactions.mapNotNull { it.accountTail }.distinct()

    Column(
        Modifier.fillMaxSize().background(Ink).padding(WindowInsets.safeDrawing.asPaddingValues()).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(RuleStrong)) {
            Box(Modifier.fillMaxWidth(step / 4f).fillMaxHeight().background(Brass))
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            when (step) {
                1 -> Column(Modifier.padding(top = 90.dp)) {
                    Text("Set a budget.\nWe’ll read the rest.", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Track Budget reads payment messages already on your phone and keeps a running total, so you don’t have to log anything.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                2 -> Column {
                    Text("Track Budget needs to read your messages", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Your bank sends you a text every time money moves. That’s what we read.", color = TextSecondary)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = RuleStrong)
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("WHAT STAYS TRUE")
                    listOf(
                        "Messages are read and processed on this phone only.",
                        "Nothing is uploaded. The app has no internet access at all.",
                        "Personal messages are ignored — only bank and payment sender IDs are read.",
                        "You can see and delete everything we stored, any time.",
                    ).forEach { Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge) }
                    if (smsGranted) {
                        Text(
                            when {
                                scanning -> "Scanning the last 90 days…"
                                importedCount != null -> "$importedCount transactions imported"
                                else -> "SMS access is on"
                            },
                            Modifier.padding(top = 18.dp), color = Mint, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                3 -> Column {
                    Text(
                        "Found ${transactions.size} transactions across ${accounts.size} ${if (accounts.size == 1) "account" else "accounts"}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("in the last 90 days", Modifier.padding(top = 8.dp, bottom = 16.dp), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = RuleStrong)
                    if (accounts.isEmpty()) {
                        Text("No account identifiers were found. You can still track manually.", Modifier.padding(vertical = 18.dp), color = TextSecondary)
                    }
                    accounts.forEach { tail ->
                        SourceToggleRow("••$tail", "account:$tail", ignoredSources, onSourceIgnored)
                    }
                }
                else -> Column {
                    Text("Set your first budget", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(18.dp))
                    SegmentedChoice(listOf("Week", "Month", "Custom"), period) { period = it }
                    if (period == "Custom") {
                        Spacer(Modifier.height(12.dp))
                        DateRangeControl(customStart, customEnd, { customStart = it.coerceAtMost(customEnd) }, { customEnd = it.coerceAtLeast(customStart) })
                    } else {
                        val range = budgetRange(Budget(period = period))
                        Eyebrow(
                            if (period == "Week") "WEEK · MON – SUN · ${shortDate(range.start)} – ${shortDate(range.endInclusive)}"
                            else "MONTH · CALENDAR-ALIGNED · ${shortDate(range.start)} – ${shortDate(range.endInclusive)}",
                            Modifier.padding(top = 14.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    FlatMoneyField(amount) { amount = it }
                    Text("Category caps are optional — most people skip this.", Modifier.padding(top = 14.dp), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        when (step) {
            1 -> PrimaryButton("Continue", Modifier.fillMaxWidth()) { step = 2 }
            2 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { manualOnly = true; step = 4 }, modifier = Modifier.weight(1f).height(50.dp),
                        border = BorderStroke(1.dp, RuleStrong), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    ) { Text("I’ll enter manually", maxLines = 1, fontSize = 12.sp) }
                    PrimaryButton(if (smsGranted) "Continue" else "Allow access", Modifier.weight(2f), enabled = !scanning) {
                        if (smsGranted) step = 3 else onEnableSms()
                    }
                }
            }
            3 -> PrimaryButton("Continue", Modifier.fillMaxWidth()) { step = 4 }
            else -> PrimaryButton("Get started", Modifier.fillMaxWidth(), enabled = decimalToMinor(amount) > 0) {
                onFinish(
                    Budget(
                        amountMinor = decimalToMinor(amount), period = period,
                        startEpochDay = if (period == "Custom") customStart.toEpochDay() else null,
                        endEpochDay = if (period == "Custom") customEnd.toEpochDay() else null,
                    ),
                )
            }
        }
        if (manualOnly) Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun MainShell(
    transactions: List<Transaction>,
    learnedRules: List<LearnedRule>,
    ignoredSources: Set<String>,
    budget: Budget,
    inboxGranted: Boolean,
    liveSmsGranted: Boolean,
    notificationsGranted: Boolean,
    scanning: Boolean,
    importedCount: Int?,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onScan: () -> Unit,
    onAdd: (Long, String, String, Direction, Long) -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    onBudget: (Budget) -> Unit,
    onLearn: (String, String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSourceIgnored: (String, Boolean) -> Unit,
    onSamples: () -> Unit,
    onClear: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    BackHandler(overlay != null) { overlay = null }
    BackHandler(overlay == null && tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Scaffold(containerColor = Ink, bottomBar = { TabBar(tab) { tab = it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!liveSmsGranted || !inboxGranted) {
                    Row(
                        Modifier.fillMaxWidth().background(BrassDim).clickable(onClick = onRequestSms).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("SMS reading is off — add transactions manually.", Modifier.weight(1f), fontSize = 12.sp, color = Brass)
                        Text("Turn on", fontSize = 12.sp, color = Brass, textDecoration = TextDecoration.Underline)
                    }
                }
                when (tab) {
                    Tab.Home -> HomeScreen(transactions, budget, onOverlay = { overlay = it }, onShowCategories = { tab = Tab.Settings })
                    Tab.Timeline -> TimelineScreen(transactions) { overlay = Overlay.Detail(it.id) }
                    Tab.Insights -> InsightsScreen(transactions, budget) { overlay = Overlay.Detail(it.id) }
                    Tab.Settings -> SettingsScreen(
                        transactions, learnedRules, ignoredSources, inboxGranted, liveSmsGranted, notificationsGranted,
                        scanning, importedCount, onRequestSms, onRequestNotifications, onScan,
                        onBudget = { overlay = Overlay.BudgetEditor }, onRemoveRule, onSourceIgnored, onSamples, onClear,
                    )
                }
            }
        }
        if (overlay == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 78.dp).size(52.dp).clickable { overlay = Overlay.Add },
                color = Brass, shape = CircleShape, shadowElevation = 8.dp,
            ) { Box(contentAlignment = Alignment.Center) { Text("+", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light) } }
        }

        when (val current = overlay) {
            Overlay.Add -> AddSheet(onClose = { overlay = null }) { amount, merchant, categoryId, direction, occurredAt ->
                onAdd(amount, merchant, categoryId, direction, occurredAt)
                overlay = null
            }
            Overlay.BudgetEditor -> BudgetSheet(budget, transactions, onClose = { overlay = null }) {
                onBudget(it)
                overlay = null
            }
            Overlay.Review -> ReviewSheet(
                transaction = transactions.firstOrNull { it.status in listOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.UNPARSEABLE) },
                remaining = transactions.count { it.status in listOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.UNPARSEABLE) },
                tracked = transactions.count { it.status == TransactionStatus.CONFIRMED },
                onClose = { overlay = null }, onSave = onSave, onLearn = onLearn,
            )
            is Overlay.Credit -> transactions.firstOrNull { it.id == current.id }?.let { transaction ->
                CreditSheet(
                    transaction = transaction,
                    onClose = { overlay = null },
                    onLearn = onLearn,
                    onIgnore = {
                        onSave(transaction.copy(status = TransactionStatus.EXCLUDED))
                        overlay = null
                    },
                ) { categoryId, refund ->
                    val candidate = if (refund) transactions.filter {
                        it.direction == Direction.DEBIT && it.status == TransactionStatus.CONFIRMED
                    }.minByOrNull { abs(it.amountMinor - transaction.amountMinor) } else null
                    onSave(transaction.copy(categoryId = categoryId, refundOfId = candidate?.id, status = TransactionStatus.CONFIRMED))
                    overlay = null
                }
            }
            is Overlay.Detail -> transactions.firstOrNull { it.id == current.id }?.let { transaction ->
                DetailSheet(transaction, onClose = { overlay = null }, onSave = onSave, onDelete = {
                    onDelete(it); overlay = null
                })
            }
            is Overlay.CategoryDetail -> CategoryDetailSheet(
                categoryId = current.id,
                transactions = transactions,
                budget = budget,
                onClose = { overlay = null },
                onTransaction = { overlay = Overlay.Detail(it.id) },
            )
            null -> Unit
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(WindowInsets.navigationBars.asPaddingValues()).padding(top = 6.dp, bottom = 6.dp),
    ) {
        Tab.entries.forEach { tab ->
            Column(
                Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tab.glyph, fontSize = 20.sp, color = if (selected == tab) Brass else TextMuted)
                Text(tab.label, fontSize = 10.sp, color = if (selected == tab) Brass else TextMuted)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    transactions: List<Transaction>,
    budget: Budget,
    onOverlay: (Overlay) -> Unit,
    onShowCategories: () -> Unit,
) {
    val analytics = remember(transactions, budget) { spendingAnalytics(transactions, budget) }
    val snapshot = analytics.snapshot
    val reviewCount = transactions.count { it.status in listOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.UNPARSEABLE) }
    val pendingCredit = transactions.firstOrNull { it.status == TransactionStatus.NEEDS_RESOLUTION }
    val recent = transactions.filter { it.status in listOf(TransactionStatus.CONFIRMED, TransactionStatus.EXCLUDED) }.take(5)
    val paceTarget = budget.amountMinor * snapshot.dayOfPeriod / snapshot.daysInPeriod
    val paceDelta = paceTarget - snapshot.spentMinor
    val activeLimitStatuses = remember(transactions, budget) {
        categoryLimitStatuses(transactions, budget).filter { it.level != CategoryLimitLevel.NORMAL }
    }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(periodLabel(snapshot.range), style = MaterialTheme.typography.titleLarge)
                Text("Day ${snapshot.dayOfPeriod} of ${snapshot.daysInPeriod}", Modifier.padding(start = 8.dp).weight(1f), fontSize = 11.sp, color = TextSecondary)
                Surface(
                    Modifier.size(34.dp).clickable { onOverlay(Overlay.BudgetEditor) }, shape = CircleShape,
                    color = Surface, border = BorderStroke(1.dp, Rule),
                ) { Box(contentAlignment = Alignment.Center) { Text("☷", color = TextSecondary, fontSize = 17.sp) } }
            }
        }
        if (pendingCredit != null) item {
            AlertCard("↓", "${money(pendingCredit.amountMinor)} from ${pendingCredit.merchant}", "Resolve →", Mint) {
                onOverlay(Overlay.Credit(pendingCredit.id))
            }
        }
        if (reviewCount > 0) item {
            AlertCard("!", "$reviewCount need a category", "Review →", Brass) { onOverlay(Overlay.Review) }
        }
        items(activeLimitStatuses, key = { it.categoryId }) { status ->
            CategoryLimitAlertCard(status) { onOverlay(Overlay.CategoryDetail(status.categoryId)) }
        }
        item {
            BudgetHero(snapshot, budget, paceDelta)
        }
        item { DailySpendCard(snapshot) }
        if (snapshot.categoryTotals.isNotEmpty()) item {
            CategorySummaryCard(snapshot.categoryTotals, onShowCategories) { onOverlay(Overlay.CategoryDetail(it)) }
        }
        item {
            Eyebrow("RECENT", Modifier.padding(top = 4.dp))
            if (recent.isEmpty()) {
                EmptyState("No transactions yet", "Add one manually or turn on SMS reading to begin.")
            } else recent.forEach { transaction ->
                RecentTransactionRow(transaction) { onOverlay(Overlay.Detail(transaction.id)) }
            }
        }
    }
}

@Composable
private fun AlertCard(icon: String, label: String, action: String, color: Color, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick), color = if (color == Mint) MintDim else BrassDim,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, color.copy(alpha = .38f)),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = color, fontSize = 18.sp)
            Text(label, Modifier.padding(start = 10.dp).weight(1f), color = color, fontSize = 13.sp)
            Text(action, color = color, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryLimitAlertCard(status: CategoryLimitStatus, onClick: () -> Unit) {
    val exceeded = status.level == CategoryLimitLevel.EXCEEDED
    val name = category(status.categoryId)?.name ?: "Category"
    AlertCard(
        icon = if (exceeded) "!" else "↑",
        label = if (exceeded) "$name is ${money(-status.remainingMinor)} over its limit" else "${money(status.remainingMinor)} left in $name",
        action = "View →",
        color = if (exceeded) Clay else Brass,
        onClick = onClick,
    )
}

@Composable
private fun BudgetHero(snapshot: com.prudvi.trackbudget.model.DashboardSnapshot, budget: Budget, paceDelta: Long) {
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (snapshot.safeTodayMinor < 0) "OVER BY" else "LEFT TO SPEND TODAY")
                Text(
                    money(abs(snapshot.safeTodayMinor)), style = MaterialTheme.typography.displayLarge,
                    color = if (snapshot.safeTodayMinor < 0) Clay else TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(color = if (paceDelta >= 0) MintDim else ClayDim, shape = RoundedCornerShape(13.dp)) {
                Text(
                    "${if (paceDelta >= 0) "↓" else "↑"} ${money(abs(paceDelta))} ${if (paceDelta >= 0) "under" else "over"} pace",
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp,
                    color = if (paceDelta >= 0) Mint else Clay, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PaceRail(snapshot.spentMinor, budget.amountMinor, snapshot.dayOfPeriod, snapshot.daysInPeriod)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("SPENT", money(snapshot.spentMinor), Alignment.Start, Modifier.weight(1f))
            Metric("LEFT", money(abs(snapshot.remainingMinor)), Alignment.CenterHorizontally, Modifier.weight(1f), if (snapshot.remainingMinor < 0) Clay else TextPrimary)
            Metric("BUDGET", money(budget.amountMinor), Alignment.End, Modifier.weight(1f), TextSecondary)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, alignment: Alignment.Horizontal, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Column(modifier, horizontalAlignment = alignment) {
        Eyebrow(label)
        Text(value, fontFamily = SpaceGrotesk, fontSize = 15.sp, color = color)
    }
}

@Composable
private fun PaceRail(spent: Long, budget: Long, day: Int, days: Int) {
    val spentFraction = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
    val timeFraction = (day.toFloat() / days).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Rule)) {
        Box(Modifier.fillMaxWidth(minOf(spentFraction, timeFraction)).fillMaxHeight().background(Brass))
        if (spentFraction > timeFraction) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(timeFraction.coerceAtLeast(.001f)))
                Box(Modifier.weight((spentFraction - timeFraction).coerceAtLeast(.001f)).fillMaxHeight().background(Clay))
                if (spentFraction < 1f) Spacer(Modifier.weight(1f - spentFraction))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(timeFraction.coerceAtLeast(.001f)))
            Box(Modifier.width(2.dp).fillMaxHeight().background(TextPrimary))
            Spacer(Modifier.weight((1f - timeFraction).coerceAtLeast(.001f)))
        }
    }
}

@Composable
private fun DailySpendCard(snapshot: com.prudvi.trackbudget.model.DashboardSnapshot) {
    val max = snapshot.dailyTotals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val average = if (snapshot.dayOfPeriod == 0) 0 else snapshot.spentMinor / snapshot.dayOfPeriod
    var selectedDate by remember(snapshot.range) { mutableStateOf<LocalDate?>(null) }
    val selectedAmount = selectedDate?.let { snapshot.dailyTotals[it] ?: 0L }
    Card {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("DAILY SPEND", Modifier.weight(1f))
            Text(
                if (selectedDate == null) {
                    "avg ${money(average)} · ${snapshot.daysInPeriod - snapshot.dayOfPeriod + 1} days left"
                } else {
                    "${shortDate(selectedDate!!)} · ${money(selectedAmount!!)}"
                },
                fontSize = 11.sp,
                color = if (selectedDate == null) TextSecondary else TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            repeat(snapshot.daysInPeriod) { index ->
                val date = snapshot.range.start.plusDays(index.toLong())
                val value = snapshot.dailyTotals[date] ?: 0L
                val height = if (value == 0L) 3 else (5 + 53 * value.toFloat() / max).roundToInt()
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        selectedDate = if (selectedDate == date) null else date
                    },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(2.dp)).background(
                            when {
                                index + 1 > snapshot.dayOfPeriod -> SurfaceHigh
                                index + 1 == snapshot.dayOfPeriod -> TextPrimary
                                value > 0 -> Brass
                                else -> RuleStrong
                            },
                        ).then(
                            if (selectedDate == date) Modifier.border(1.dp, Mint, RoundedCornerShape(2.dp)) else Modifier,
                        ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("1", Modifier.weight(1f), fontSize = 10.sp, color = TextMuted)
            Text(
                selectedDate?.dayOfMonth?.toString() ?: "today",
                Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp,
                color = if (selectedDate == null) TextPrimary else Mint,
            )
            Text(snapshot.daysInPeriod.toString(), Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
private fun CategorySummaryCard(totals: Map<String, Long>, onAll: () -> Unit, onCategory: (String) -> Unit) {
    val sorted = totals.entries.filter { category(it.key) != null && it.value > 0 }.sortedByDescending { it.value }
    val sum = sorted.sumOf { it.value }.coerceAtLeast(1)
    Card {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("WHERE IT’S GOING", Modifier.weight(1f))
            Text("All →", Modifier.clickable(onClick = onAll), color = Brass, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            sorted.take(5).forEach { (id, total) ->
                Box(Modifier.weight(total.toFloat() / sum).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(category(id)!!.color)))
            }
        }
        Spacer(Modifier.height(12.dp))
        sorted.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (id, total) ->
                    val item = category(id) ?: return@forEach
                    Row(Modifier.weight(1f).clickable { onCategory(id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(item.color)))
                        Text(item.name, Modifier.padding(start = 7.dp).weight(1f), fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(total), fontFamily = SpaceGrotesk, fontSize = 12.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentTransactionRow(transaction: Transaction, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp).alpha(if (transaction.status == TransactionStatus.EXCLUDED) .45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = category(transaction.categoryId)
        Surface(Modifier.size(32.dp), color = item?.let { Color(it.color).copy(alpha = .17f) } ?: SurfaceHigh, shape = RoundedCornerShape(11.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(transaction.merchant.trim().firstOrNull()?.uppercase() ?: "?", color = item?.let { Color(it.color) } ?: TextSecondary, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(transaction.merchant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (transaction.status == TransactionStatus.EXCLUDED) TextDecoration.LineThrough else null)
            Text("${item?.name ?: "Uncategorized"} · ${timeLabel(transaction)}", fontSize = 11.sp, color = TextSecondary)
        }
        AmountText(transaction)
    }
}

@Composable
private fun TimelineScreen(transactions: List<Transaction>, onTransaction: (Transaction) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val active = transactions.filter { it.status !in listOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE) }
    val filtered = active.filter {
        query.isBlank() || it.merchant.contains(query, true) || it.note.contains(query, true) ||
            it.rawMessage.orEmpty().contains(query, true) || money(it.amountMinor).contains(query)
    }
    val groups = filtered.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        .toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Timeline", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            TrackTextField(query, { query = it }, "Search merchant, amount, note…")
        }
        if (groups.isEmpty()) item { EmptyState("Nothing found", if (query.isBlank()) "Transactions will appear here." else "Try a different search.") }
        groups.forEach { (date, values) ->
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(dateLabel(date), Modifier.weight(1f))
                    val net = values.sumOf { if (it.direction == Direction.CREDIT) it.amountMinor else -it.amountMinor }
                    Text(signedMoney(net), fontFamily = SpaceGrotesk, fontSize = 13.sp, color = if (net > 0) Mint else TextPrimary)
                }
                HorizontalDivider(Modifier.padding(top = 6.dp), color = RuleStrong)
                values.forEach { transaction ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onTransaction(transaction) }.padding(vertical = 9.dp).alpha(if (transaction.status == TransactionStatus.EXCLUDED) .42f else 1f),
                    ) {
                        Text(timeLabel(transaction), Modifier.width(64.dp), fontSize = 11.sp, color = TextSecondary)
                        Column(Modifier.weight(1f)) {
                            Text(transaction.merchant, fontSize = 14.sp, textDecoration = if (transaction.status == TransactionStatus.EXCLUDED) TextDecoration.LineThrough else null)
                            Text(category(transaction.categoryId)?.name ?: "Uncategorized", fontSize = 11.sp, color = TextSecondary)
                        }
                        AmountText(transaction, transaction.status == TransactionStatus.EXCLUDED)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(transactions: List<Transaction>, budget: Budget, onTransaction: (Transaction) -> Unit) {
    val analytics = remember(transactions, budget) { spendingAnalytics(transactions, budget) }
    val snapshot = analytics.snapshot
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Insights", style = MaterialTheme.typography.titleLarge) }
        item { ProjectionCard(analytics, budget) }
        item { SpendingCalendar(snapshot) }
        item { FixedFlexibleCard(analytics) }
        item { PreviousPeriodCard(analytics) }
        item { MerchantCard(analytics) }
        item {
            Card {
                Row(Modifier.fillMaxWidth()) {
                    Eyebrow("REPEATS EVERY MONTH", Modifier.weight(1f))
                    Text(money(analytics.recurring.sumOf { it.amountMinor }), fontFamily = SpaceGrotesk, fontSize = 12.sp, color = TextSecondary)
                }
                if (analytics.recurring.isEmpty()) Text("Recurring payments will appear after a pattern is found.", Modifier.padding(top = 10.dp), color = TextSecondary, fontSize = 12.sp)
                analytics.recurring.forEach { transaction ->
                    Row(Modifier.fillMaxWidth().clickable { onTransaction(transaction) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("↻", color = TextSecondary)
                        Text(transaction.merchant, Modifier.padding(start = 10.dp).weight(1f), fontSize = 13.sp)
                        Text(money(transaction.amountMinor), fontFamily = SpaceGrotesk, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectionCard(analytics: SpendingAnalytics, budget: Budget) {
    val projected = analytics.projectedMinor
    val over = projected > budget.amountMinor
    val max = maxOf(projected, budget.amountMinor, 1)
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("ON THIS PACE, BY ${shortDate(analytics.snapshot.range.endInclusive).uppercase()}")
                Text(money(projected), style = MaterialTheme.typography.headlineLarge, color = if (over) Clay else Mint)
            }
            Surface(color = if (over) ClayDim else MintDim, shape = RoundedCornerShape(13.dp)) {
                Text(
                    if (over) "${money(projected - budget.amountMinor)} over budget" else "${money(budget.amountMinor - projected)} spare",
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, color = if (over) Clay else Mint,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Rule)) {
            Box(Modifier.fillMaxWidth(projected.toFloat() / max).fillMaxHeight().background(if (over) Clay else Mint))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(budget.amountMinor.toFloat() / max))
                Box(Modifier.width(2.dp).fillMaxHeight().background(TextPrimary))
                if (budget.amountMinor < max) Spacer(Modifier.weight((max - budget.amountMinor).toFloat() / max))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("${money(projected)} projected", Modifier.weight(1f), fontSize = 11.sp, color = TextSecondary)
            Text("${money(budget.amountMinor)} budget", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SpendingCalendar(snapshot: com.prudvi.trackbudget.model.DashboardSnapshot) {
    val max = snapshot.dailyTotals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val heaviest = snapshot.dailyTotals.maxByOrNull { it.value }?.key
    Card {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("SPENDING CALENDAR", Modifier.weight(1f))
            Text("heaviest ${heaviest?.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)) ?: "—"}", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 9.sp, color = TextMuted) }
        }
        val cells = buildList<LocalDate?> {
            repeat(snapshot.range.start.dayOfWeek.value % 7) { add(null) }
            repeat(snapshot.daysInPeriod) { add(snapshot.range.start.plusDays(it.toLong())) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    val value = date?.let { snapshot.dailyTotals[it] } ?: 0L
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(
                            when {
                                date == null -> Color.Transparent
                                date > LocalDate.now() -> Surface.copy(alpha = .55f)
                                value == 0L -> SurfaceHigh
                                else -> Brass.copy(alpha = .28f + .72f * value.toFloat() / max)
                            },
                        ), contentAlignment = Alignment.Center,
                    ) { if (date != null) Text(date.dayOfMonth.toString(), fontSize = 9.sp, color = if (value > max / 2) Ink else TextSecondary) }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FixedFlexibleCard(analytics: SpendingAnalytics) {
    val total = (analytics.committedMinor + analytics.flexibleMinor).coerceAtLeast(1)
    Card {
        Eyebrow("FIXED VS FLEXIBLE")
        Row(Modifier.fillMaxWidth().height(14.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (analytics.committedMinor > 0) Box(Modifier.weight(analytics.committedMinor.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(TextMuted))
            if (analytics.flexibleMinor > 0) Box(Modifier.weight(analytics.flexibleMinor.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Brass))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            LegendMetric("Fixed", analytics.committedMinor, TextMuted, Modifier.weight(1f))
            LegendMetric("Flexible", analytics.flexibleMinor, Brass, Modifier.weight(1f))
        }
        Text(
            "Of what you can still control, ${money(analytics.snapshot.remainingMinor.coerceAtLeast(0))} is left for ${analytics.snapshot.daysInPeriod - analytics.snapshot.dayOfPeriod + 1} days.",
            Modifier.padding(top = 10.dp), fontSize = 12.sp, lineHeight = 18.sp, color = TextSecondary,
        )
    }
}

@Composable
private fun LegendMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, Modifier.padding(start = 7.dp), fontSize = 12.sp, color = TextSecondary)
        Text(money(value), Modifier.padding(start = 7.dp), fontFamily = SpaceGrotesk, fontSize = 12.sp)
    }
}

@Composable
private fun PreviousPeriodCard(analytics: SpendingAnalytics) {
    val shifts = analytics.categoryShifts
    val max = shifts.maxOfOrNull { abs(it.amountMinor) }?.coerceAtLeast(1) ?: 1
    val total = shifts.sumOf { it.amountMinor }
    Card {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("VS LAST PERIOD", Modifier.weight(1f))
            Text(signedMoney(total), fontSize = 11.sp, color = if (total > 0) Clay else Mint)
        }
        if (shifts.isEmpty()) Text("A comparison appears after one full prior period.", Modifier.padding(top = 10.dp), fontSize = 12.sp, color = TextSecondary)
        shifts.forEach { shift ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(category(shift.categoryId)?.name ?: "Other", Modifier.width(82.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.weight(1f).height(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (shift.amountMinor < 0) Box(Modifier.fillMaxWidth(abs(shift.amountMinor).toFloat() / max).height(9.dp).clip(RoundedCornerShape(3.dp)).background(Mint))
                    }
                    Box(Modifier.width(1.dp).height(11.dp).background(RuleStrong))
                    Box(Modifier.weight(1f)) {
                        if (shift.amountMinor > 0) Box(Modifier.fillMaxWidth(abs(shift.amountMinor).toFloat() / max).height(9.dp).clip(RoundedCornerShape(3.dp)).background(Clay))
                    }
                }
                Text(signedMoney(shift.amountMinor), Modifier.width(68.dp), textAlign = TextAlign.End, fontFamily = SpaceGrotesk, fontSize = 12.sp, color = if (shift.amountMinor > 0) Clay else Mint)
            }
        }
    }
}

@Composable
private fun MerchantCard(analytics: SpendingAnalytics) {
    val max = analytics.topMerchants.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    Card {
        Row(Modifier.fillMaxWidth()) {
            Eyebrow("TOP MERCHANTS", Modifier.weight(1f))
            Text("${(analytics.topThreeShare * 100).roundToInt()}% of spend", fontSize = 11.sp, color = TextSecondary)
        }
        analytics.topMerchants.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.merchant, Modifier.width(90.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(9.dp).clip(CircleShape).background(Rule)) {
                    Box(Modifier.fillMaxWidth(item.amountMinor.toFloat() / max).fillMaxHeight().background(Brass))
                }
                Text(money(item.amountMinor), Modifier.width(78.dp), textAlign = TextAlign.End, fontFamily = SpaceGrotesk, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    transactions: List<Transaction>, learnedRules: List<LearnedRule>, ignoredSources: Set<String>,
    inboxGranted: Boolean, liveSmsGranted: Boolean, notificationsGranted: Boolean,
    scanning: Boolean, importedCount: Int?, onPermission: () -> Unit, onNotificationPermission: () -> Unit,
    onScan: () -> Unit, onBudget: () -> Unit, onRemoveRule: (String) -> Unit,
    onSourceIgnored: (String, Boolean) -> Unit, onSamples: () -> Unit, onClear: () -> Unit,
) {
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetProvider = remember { ComponentName(context, BudgetWidgetProvider::class.java) }
    val widgetInstalled = widgetManager.getAppWidgetIds(widgetProvider).isNotEmpty()
    var widgetRequestPending by remember { mutableStateOf(false) }
    val canPinWidget = widgetManager.isRequestPinAppWidgetSupported && !widgetInstalled && !widgetRequestPending
    val sourceRows = buildList {
        transactions.mapNotNull { it.accountTail }.distinct().forEach { add("••$it" to "account:$it") }
        transactions.mapNotNull { it.sender }.distinct().filter { sender -> none { it.second == "sender:$sender" } }.forEach { add(it to "sender:$it") }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 88.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }
        item {
            SettingsRow("Privacy & data", if (privacyOpen) "⌄" else "›") { privacyOpen = !privacyOpen }
            if (privacyOpen) {
                Text(
                    "Everything is stored on this phone only. Raw messages never leave the device — there is no server and no account.",
                    Modifier.padding(bottom = 12.dp), color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                )
                Eyebrow("SENDERS READ", Modifier.padding(vertical = 6.dp))
                if (sourceRows.isEmpty()) Text("No bank or payment senders found yet.", Modifier.padding(vertical = 8.dp), fontSize = 13.sp, color = TextSecondary)
                sourceRows.forEach { (label, key) -> SourceToggleRow(label, key, ignoredSources, onSourceIgnored) }
                SettingsToggleRow("SMS permission", inboxGranted && liveSmsGranted, onPermission)
                InsightLine("Stored transactions", transactions.size.toString())
                InsightLine("Network access", "None")
                SettingsRow("Past inbox import", if (inboxGranted) "Scan" else "Off") { if (inboxGranted) onScan() else onPermission() }
                if (inboxGranted) Text(
                    when {
                        scanning -> "Scanning the last 90 days…"
                        importedCount != null -> "Last scan added $importedCount transactions."
                        else -> "Tap to scan the last 90 days again."
                    }, Modifier.padding(bottom = 8.dp), fontSize = 12.sp, color = TextSecondary,
                )
                SettingsRow("Notifications", if (notificationsGranted) "On" else "Off", onClick = onNotificationPermission)
                SettingsRow("Add sample transactions", "+", onClick = onSamples)
                SettingsRow("Delete all transaction data", "", danger = true) { confirmDelete = true }
            }
            SettingsRow("Budgets", "›", onClick = onBudget)
            SettingsRow(
                "Home screen widget",
                when {
                    widgetInstalled -> "Added"
                    widgetRequestPending -> "Requested"
                    widgetManager.isRequestPinAppWidgetSupported -> "Add"
                    else -> "Unavailable"
                },
                enabled = canPinWidget,
            ) {
                if (widgetManager.getAppWidgetIds(widgetProvider).isEmpty()) {
                    widgetRequestPending = widgetManager.requestPinAppWidget(widgetProvider, null, null)
                }
            }
            SettingsRow("Categories", if (categoriesOpen) "⌄" else "›") { categoriesOpen = !categoriesOpen }
            if (categoriesOpen) {
                Categories.filter { it.id != "refund" }.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(item.color)))
                        Text(item.name, Modifier.padding(start = 10.dp).weight(1f), fontSize = 13.sp)
                        if (item.notSpending) Surface(color = Color.Transparent, border = BorderStroke(1.dp, RuleStrong), shape = RoundedCornerShape(4.dp)) {
                            Text("NOT SPENDING", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
                Eyebrow("LEARNED RULES", Modifier.padding(top = 14.dp, bottom = 6.dp))
                if (learnedRules.isEmpty()) Text("Rules you save while reviewing transactions appear here.", Modifier.padding(bottom = 8.dp), fontSize = 12.sp, color = TextSecondary)
                learnedRules.forEach { rule ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${rule.merchant} → ${category(rule.categoryId)?.name ?: rule.categoryId}", Modifier.weight(1f), fontSize = 13.sp, color = TextSecondary)
                        Text("×", Modifier.clickable { onRemoveRule(rule.merchant) }.padding(6.dp), color = TextSecondary)
                    }
                }
            }
            listOf("App lock", "Accounts", "Reminders", "Appearance", "Backup", "About").forEach { label ->
                SettingsRow(label, "›", enabled = false) {}
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = SurfaceHigh,
        title = { Text("Delete all transactions?") }, text = { Text("This cannot be undone. Your budget will stay in place.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmDelete = false }) { Text("Delete", color = Clay) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SourceToggleRow(label: String, key: String, ignoredSources: Set<String>, onChange: (String, Boolean) -> Unit) {
    val enabled = key !in ignoredSources
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        MiniSwitch(enabled) { onChange(key, !it) }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        MiniSwitch(checked) { onClick() }
    }
}

@Composable
private fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(40.dp).height(24.dp).clip(CircleShape).background(if (checked) Brass else RuleStrong).clickable { onChange(!checked) },
    ) {
        Box(Modifier.padding(3.dp).offset(x = if (checked) 16.dp else 0.dp).size(18.dp).clip(CircleShape).background(TextPrimary))
    }
}

@Composable
private fun SettingsRow(label: String, value: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else .55f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = if (danger) Clay else TextPrimary)
        Text(value, color = if (danger) Clay else TextSecondary)
    }
    HorizontalDivider(color = RuleStrong)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSheet(onClose: () -> Unit, onAdd: (Long, String, String, Direction, Long) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(Direction.DEBIT) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    FullScreenFrame("Add manually", onClose, scroll = false) {
        SegmentedChoice(listOf("Spent", "Received"), if (direction == Direction.DEBIT) "Spent" else "Received") {
            direction = if (it == "Spent") Direction.DEBIT else Direction.CREDIT
            selectedCategory = if (direction == Direction.CREDIT) "income" else null
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text("₹", fontSize = 30.sp, color = TextSecondary)
            Text(if (amount.isBlank()) "0" else amount.toLongOrNull()?.let(::formatIndianNumber) ?: amount, fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = if (amount.isBlank()) RuleStrong else if (direction == Direction.CREDIT) Mint else TextPrimary)
        }
        TrackTextField(merchant, { merchant = it }, "Merchant or note")
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(LocalDate.now() to "Today", LocalDate.now().minusDays(1) to "Yesterday", LocalDate.now().minusDays(2) to shortDate(LocalDate.now().minusDays(2))).forEach { (date, label) ->
                ChoiceChip(label, selectedDate == date) { selectedDate = date }
            }
        }
        Eyebrow("CATEGORY")
        Spacer(Modifier.height(8.dp))
        val options = if (direction == Direction.CREDIT) Categories.filter { it.id in listOf("income", "repayments", "transfers") }
        else Categories.filter { it.id !in listOf("income", "refund") && (showAll || it.id in listOf("food", "groceries", "bills", "shopping", "transport")) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { item -> CategoryChip(item, selectedCategory == item.id) { selectedCategory = item.id; error = false } }
            if (!showAll && direction == Direction.DEBIT) ChoiceChip("+ More", false) { showAll = true }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(top = 10.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫")).forEach { keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { key ->
                        Surface(
                            Modifier.weight(1f).padding(bottom = 6.dp).height(46.dp).clickable {
                                amount = when {
                                    key == "⌫" -> amount.dropLast(1)
                                    amount.length >= 8 -> amount
                                    key == "00" && amount.isBlank() -> amount
                                    else -> (amount + key).trimStart('0').ifBlank { "0" }
                                }
                                error = false
                            }, color = Surface, shape = RoundedCornerShape(12.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(key, fontSize = 19.sp) } }
                    }
                }
            }
            PrimaryButton(if (error) "Enter an amount and category" else "Save", Modifier.fillMaxWidth(), enabled = amount.toLongOrNull()?.let { it > 0 } == true && selectedCategory != null) {
                val minor = (amount.toLongOrNull() ?: 0) * 100
                if (minor <= 0 || selectedCategory == null) error = true else onAdd(
                    minor, merchant, selectedCategory!!, direction,
                    selectedDate.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant().toEpochMilli(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetSheet(budget: Budget, transactions: List<Transaction>, onClose: () -> Unit, onSave: (Budget) -> Unit) {
    var amount by rememberSaveable { mutableStateOf((budget.amountMinor / 100).toString()) }
    var period by rememberSaveable { mutableStateOf(budget.period) }
    var start by remember { mutableStateOf(budget.startEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var end by remember { mutableStateOf(budget.endEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(30)) }
    var repeats by rememberSaveable { mutableStateOf(budget.repeats) }
    var carry by rememberSaveable { mutableStateOf(budget.carryOver) }
    var limits by remember { mutableStateOf(budget.categoryLimits) }
    var editingLimitCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var limitAmount by rememberSaveable { mutableStateOf("") }
    val draft = Budget(decimalToMinor(amount), period, repeats, carry, start.toEpochDay(), end.toEpochDay(), limits)
    val range = budgetRange(draft)
    val snapshot = dashboard(transactions, draft)
    FullScreenFrame("Budget setup", onClose) {
        Eyebrow("PERIOD")
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(listOf("Week", "Month", "Custom"), period) { period = it }
        if (period == "Custom") {
            Spacer(Modifier.height(10.dp))
            DateRangeControl(start, end, { start = it.coerceAtMost(end) }, { end = it.coerceAtLeast(start) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text("${longDate(range.start)} – ${longDate(range.endInclusive)} · ${range.days} days", Modifier.weight(1f), fontSize = 12.sp, color = TextSecondary)
            Text("${money(draft.amountMinor / range.days)} per day", fontSize = 12.sp, color = TextSecondary)
        }
        Eyebrow("BUDGET AMOUNT")
        Spacer(Modifier.height(8.dp))
        FlatMoneyField(amount) { amount = it }
        Spacer(Modifier.height(10.dp))
        CheckboxRow("Repeat automatically each ${if (period == "Week") "week" else "month"}", repeats) { repeats = it }
        CheckboxRow("Carry unspent money into next period", carry) { carry = it }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("CATEGORY LIMITS (OPTIONAL)", Modifier.weight(1f))
            Text(
                if (editingLimitCategory == null) "+ Add limit" else "Cancel",
                Modifier.clickable {
                    editingLimitCategory = if (editingLimitCategory == null) "food" else null
                    limitAmount = ""
                }.padding(vertical = 6.dp),
                color = Brass,
                fontSize = 12.sp,
            )
        }
        if (limits.isEmpty() && editingLimitCategory == null) {
            Text("No category limits set.", Modifier.padding(vertical = 10.dp), fontSize = 12.sp, color = TextSecondary)
        }
        limits.toList().forEach { (id, limit) ->
            val spent = snapshot.categoryTotals[id] ?: 0L
            val remaining = limit - spent
            Column(
                Modifier.fillMaxWidth().clickable {
                    editingLimitCategory = id
                    limitAmount = (limit / 100).toString()
                }.padding(vertical = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(category(id)?.name ?: id, Modifier.width(100.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(money(limit), Modifier.width(80.dp), fontFamily = SpaceGrotesk, fontSize = 13.sp)
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(RuleStrong)) {
                        Box(Modifier.fillMaxWidth((spent.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(if (spent >= limit) Clay else Brass))
                    }
                    Text(
                        "×",
                        Modifier.clickable {
                            limits = limits - id
                            if (editingLimitCategory == id) {
                                editingLimitCategory = null
                                limitAmount = ""
                            }
                        }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                        color = TextSecondary,
                    )
                }
                Text(
                    "${money(spent)} used · ${if (remaining >= 0) "${money(remaining)} left" else "${money(-remaining)} over"}",
                    Modifier.fillMaxWidth().padding(top = 4.dp, end = 24.dp),
                    textAlign = TextAlign.End,
                    fontSize = 11.sp,
                    color = when {
                        spent >= limit -> Clay
                        spent * 100 >= limit * 80 -> Brass
                        else -> TextSecondary
                    },
                )
            }
        }
        if (editingLimitCategory != null) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Rule)) {
                Column(Modifier.padding(14.dp)) {
                    Eyebrow(if (editingLimitCategory in limits) "EDIT LIMIT" else "ADD LIMIT")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Categories.filter { !it.notSpending && it.id != "refund" }.forEach { item ->
                            CategoryChip(item, editingLimitCategory == item.id) {
                                editingLimitCategory = item.id
                                limitAmount = limits[item.id]?.div(100)?.toString().orEmpty()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlatMoneyField(limitAmount) { limitAmount = it }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        if (editingLimitCategory in limits) "Update limit" else "Add limit",
                        Modifier.fillMaxWidth(),
                        enabled = decimalToMinor(limitAmount) > 0,
                    ) {
                        limits = limits + (editingLimitCategory!! to decimalToMinor(limitAmount))
                        editingLimitCategory = null
                        limitAmount = ""
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save budget", Modifier.fillMaxWidth(), enabled = decimalToMinor(amount) > 0) {
            onSave(
                draft.copy(
                    startEpochDay = if (period == "Custom") start.toEpochDay() else null,
                    endEpochDay = if (period == "Custom") end.toEpochDay() else null,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSheet(
    transaction: Transaction?, remaining: Int, tracked: Int, onClose: () -> Unit,
    onSave: (Transaction) -> Unit, onLearn: (String, String) -> Unit,
) {
    var always by rememberSaveable(transaction?.id) { mutableStateOf(true) }
    var showAll by rememberSaveable(transaction?.id) { mutableStateOf(false) }
    var askingAmount by rememberSaveable(transaction?.id) { mutableStateOf(false) }
    var unparsedAmount by rememberSaveable(transaction?.id) { mutableStateOf("") }
    FullScreenFrame("Review", onClose, trailing = if (remaining > 0) "$remaining left" else null) {
        if (transaction == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("All caught up.", "$tracked transactions tracked this period.")
            }
            return@FullScreenFrame
        }
        if (transaction.status == TransactionStatus.UNPARSEABLE) {
            Surface(color = SurfaceHigh, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Couldn’t read this one.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    transaction.rawMessage?.let {
                        Surface(Modifier.fillMaxWidth().padding(top = 14.dp), color = Ink, shape = RoundedCornerShape(8.dp)) {
                            Text(it, Modifier.padding(11.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = TextSecondary)
                        }
                    }
                    Text("Was this money you spent?", Modifier.padding(top = 14.dp), fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResolutionChip("Yes", Modifier.weight(1f)) { askingAmount = true }
                        ResolutionChip("No — ignore", Modifier.weight(1f)) { onSave(transaction.copy(status = TransactionStatus.EXCLUDED)) }
                    }
                    if (askingAmount) {
                        Spacer(Modifier.height(12.dp))
                        FlatMoneyField(unparsedAmount) { unparsedAmount = it }
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Save", Modifier.fillMaxWidth(), enabled = decimalToMinor(unparsedAmount) > 0) {
                            onSave(
                                transaction.copy(
                                    amountMinor = decimalToMinor(unparsedAmount),
                                    categoryId = "misc",
                                    status = TransactionStatus.CONFIRMED,
                                ),
                            )
                        }
                    }
                }
            }
            return@FullScreenFrame
        }
        Surface(color = SurfaceHigh, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(money(transaction.amountMinor), style = MaterialTheme.typography.headlineLarge)
                Text(transaction.merchant, fontSize = 15.sp)
                Text(transactionMeta(transaction), fontSize = 12.sp, color = TextSecondary)
                transaction.rawMessage?.let {
                    Surface(Modifier.fillMaxWidth().padding(top = 14.dp), color = Ink, shape = RoundedCornerShape(8.dp)) {
                        Text(it, Modifier.padding(11.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = TextSecondary)
                    }
                }
                Eyebrow("CATEGORY", Modifier.padding(top = 16.dp, bottom = 8.dp))
                val options = Categories.filter { !it.notSpending && it.id != "refund" && (showAll || it.id in listOf("food", "groceries", "bills", "shopping", "transport")) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { item -> CategoryChip(item, false) {
                        if (always) onLearn(transaction.merchant, item.id)
                        onSave(transaction.copy(categoryId = item.id, status = TransactionStatus.CONFIRMED))
                    } }
                    if (!showAll) ChoiceChip("+ More", false) { showAll = true }
                }
                CheckboxRow("Always use this for ${transaction.merchant}", always) { always = it }
                OutlinedButton(
                    onClick = { onSave(transaction.copy(status = TransactionStatus.EXCLUDED)) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    border = BorderStroke(1.dp, RuleStrong), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                ) { Text("Ignore") }
            }
        }
    }
}

@Composable
private fun CreditSheet(
    transaction: Transaction,
    onClose: () -> Unit,
    onLearn: (String, String) -> Unit,
    onIgnore: () -> Unit,
    onResolve: (String, Boolean) -> Unit,
) {
    var always by rememberSaveable { mutableStateOf(true) }
    fun resolve(categoryId: String, refund: Boolean) {
        if (always) onLearn(transaction.merchant, categoryId)
        onResolve(categoryId, refund)
    }
    BottomSheetFrame(onClose) {
        Text("${money(transaction.amountMinor)} received from ${transaction.merchant}", fontSize = 14.sp)
        Text(transactionMeta(transaction), Modifier.padding(top = 4.dp), fontSize = 12.sp, color = TextSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolutionChip("Refund", Modifier.weight(1f)) { resolve("refund", true) }
            ResolutionChip("Money back", Modifier.weight(1f)) { resolve("repayments", false) }
            ResolutionChip("Income", Modifier.weight(1f)) { resolve("income", false) }
        }
        CheckboxRow("Always do this for ${transaction.merchant}", always) { always = it }
        OutlinedButton(
            onClick = onIgnore,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            border = BorderStroke(1.dp, RuleStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        ) { Text("Ignore") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSheet(transaction: Transaction, onClose: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit) {
    var amount by remember(transaction.id) { mutableStateOf((transaction.amountMinor / 100).toString()) }
    var note by remember(transaction.id) { mutableStateOf(transaction.note) }
    var selectedCategory by remember(transaction.id) { mutableStateOf(transaction.categoryId) }
    var excluded by remember(transaction.id) { mutableStateOf(transaction.status == TransactionStatus.EXCLUDED) }
    var confirmDelete by remember { mutableStateOf(false) }
    FullScreenFrame("Transaction", onClose) {
        FlatMoneyField(amount) { amount = it }
        Text(transaction.merchant, Modifier.padding(top = 14.dp), fontSize = 16.sp, textDecoration = if (excluded) TextDecoration.LineThrough else null)
        Text(transactionMeta(transaction), Modifier.padding(top = 4.dp, bottom = 16.dp), fontSize = 12.sp, color = TextSecondary)
        Eyebrow("CATEGORY", Modifier.padding(bottom = 8.dp))
        val options = if (transaction.direction == Direction.DEBIT) Categories.filter { it.id != "income" } else Categories.filter { it.notSpending }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { item -> CategoryChip(item, selectedCategory == item.id) { selectedCategory = item.id } }
        }
        Spacer(Modifier.height(16.dp))
        TrackTextField(note, { note = it }, "Add a note…")
        OutlinedButton(
            onClick = { excluded = !excluded }, Modifier.fillMaxWidth().padding(top = 18.dp),
            border = BorderStroke(1.dp, RuleStrong), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (excluded) Mint else Clay),
        ) { Text(if (excluded) "Mark as an expense again" else "Not an expense") }
        transaction.rawMessage?.let {
            Eyebrow("SOURCE MESSAGE", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Surface(color = SurfaceHigh, shape = RoundedCornerShape(8.dp)) {
                Text(it, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save changes", Modifier.fillMaxWidth(), enabled = decimalToMinor(amount) > 0 && (excluded || selectedCategory != null)) {
            onSave(transaction.copy(amountMinor = decimalToMinor(amount), note = note, categoryId = selectedCategory, status = if (excluded) TransactionStatus.EXCLUDED else TransactionStatus.CONFIRMED))
            onClose()
        }
        TextButton(onClick = { confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("Delete transaction", color = Clay) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = SurfaceHigh,
        title = { Text("Delete this transaction?") },
        confirmButton = { TextButton(onClick = { onDelete(transaction.id) }) { Text("Delete", color = Clay) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryDetailSheet(
    categoryId: String,
    transactions: List<Transaction>,
    budget: Budget,
    onClose: () -> Unit,
    onTransaction: (Transaction) -> Unit,
) {
    val range = budgetRange(budget)
    val zone = ZoneId.systemDefault()
    val items = transactions.filter {
        val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
        it.categoryId == categoryId &&
            it.status == TransactionStatus.CONFIRMED &&
            date >= range.start && date <= range.endInclusive
    }
    val limitStatus = categoryLimitStatuses(transactions, budget).firstOrNull { it.categoryId == categoryId }
    val total = dashboard(transactions, budget).categoryTotals[categoryId] ?: 0L
    FullScreenFrame(category(categoryId)?.name ?: "Category", onClose) {
        Text(money(total), style = MaterialTheme.typography.headlineLarge)
        if (limitStatus != null) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(RuleStrong)) {
                Box(
                    Modifier.fillMaxWidth(limitStatus.fraction.coerceIn(0f, 1f)).fillMaxHeight().background(
                        when (limitStatus.level) {
                            CategoryLimitLevel.NORMAL -> Brass
                            CategoryLimitLevel.WARNING -> Brass
                            CategoryLimitLevel.EXCEEDED -> Clay
                        },
                    ),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("${money(limitStatus.spentMinor)} of ${money(limitStatus.limitMinor)}", Modifier.weight(1f), fontSize = 12.sp, color = TextSecondary)
                Text(
                    if (limitStatus.remainingMinor >= 0) "${money(limitStatus.remainingMinor)} left" else "${money(-limitStatus.remainingMinor)} over",
                    fontSize = 12.sp,
                    color = if (limitStatus.level == CategoryLimitLevel.EXCEEDED) Clay else if (limitStatus.level == CategoryLimitLevel.WARNING) Brass else TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        items.forEach { transaction ->
            Row(Modifier.fillMaxWidth().clickable { onTransaction(transaction) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(transaction.merchant, fontSize = 14.sp)
                    Text(dateLabel(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()), fontSize = 11.sp, color = TextSecondary)
                }
                AmountText(transaction)
            }
            HorizontalDivider(color = RuleStrong)
        }
    }
}

@Composable
private fun FullScreenFrame(title: String, onClose: () -> Unit, trailing: String? = null, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (trailing != null) Text("$trailing · ", color = TextSecondary, fontSize = 12.sp)
                Text("×", Modifier.clickable(onClick = onClose).padding(6.dp), color = TextSecondary, fontSize = 20.sp)
            }
            if (scroll) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), content = content)
            else Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun BottomSheetFrame(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth().clickable(enabled = false) {}, color = SurfaceHigh, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
            Column(Modifier.padding(20.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), content = content)
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Rule)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun DateRangeControl(start: LocalDate, end: LocalDate, onStart: (LocalDate) -> Unit, onEnd: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateStepper("STARTS", start, Modifier.weight(1f), onStart)
        DateStepper("ENDS", end, Modifier.weight(1f), onEnd)
    }
}

@Composable
private fun DateStepper(label: String, date: LocalDate, modifier: Modifier, onChange: (LocalDate) -> Unit) {
    Surface(modifier, color = Surface, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Rule)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Eyebrow(label)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", Modifier.clickable { onChange(date.minusDays(1)) }.padding(horizontal = 4.dp), fontSize = 18.sp, color = TextSecondary)
                Text(shortDate(date), Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = SpaceGrotesk, fontSize = 13.sp)
                Text("›", Modifier.clickable { onChange(date.plusDays(1)) }.padding(horizontal = 4.dp), fontSize = 18.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                .background(if (checked) Brass else Color.Transparent)
                .border(1.dp, if (checked) Brass else TextSecondary, RoundedCornerShape(4.dp)),
        )
        Text(label, Modifier.padding(start = 8.dp), fontSize = 13.sp, color = if (checked) TextPrimary else TextSecondary)
    }
}

@Composable
private fun SegmentedChoice(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Surface(
                Modifier.weight(1f).clickable { onSelected(option) }, shape = RoundedCornerShape(8.dp),
                color = if (selected == option) BrassDim else Color.Transparent,
                border = BorderStroke(1.dp, if (selected == option) Brass else RuleStrong),
            ) { Text(option, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, color = if (selected == option) Brass else TextSecondary, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = if (selected) BrassDim else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Brass else Rule),
    ) { Text(label, Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = if (selected) Brass else TextSecondary, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun CategoryChip(item: Category, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = if (selected) BrassDim else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Brass else Rule),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(item.color)))
            Text(item.name, Modifier.padding(start = 7.dp), fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ResolutionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = Color.Transparent, border = BorderStroke(1.dp, RuleStrong), shape = RoundedCornerShape(10.dp)) {
        Text(label, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun AmountText(transaction: Transaction, strike: Boolean = false) {
    Text(
        (if (transaction.direction == Direction.CREDIT) "+" else "−") + money(transaction.amountMinor),
        fontFamily = SpaceGrotesk, fontSize = 14.sp, color = if (transaction.direction == Direction.CREDIT) Mint else TextPrimary,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )
}

@Composable
private fun InsightLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = TextSecondary)
        Text(value, fontFamily = SpaceGrotesk, fontSize = 12.sp)
    }
}

@Composable
private fun FlatMoneyField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onValueChange(it.filter(Char::isDigit).take(10)) }, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Text("₹", fontSize = 24.sp, color = TextSecondary) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineLarge.copy(fontFamily = SpaceGrotesk), colors = fieldColors(),
    )
}

@Composable
private fun TrackTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(11.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brass, unfocusedBorderColor = RuleStrong, focusedLabelColor = Brass,
    unfocusedLabelColor = TextSecondary, cursorColor = Brass, focusedContainerColor = SurfaceHigh, unfocusedContainerColor = SurfaceHigh,
)

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier = modifier.height(50.dp), enabled = enabled, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brass, contentColor = Ink, disabledContainerColor = SurfaceHigh, disabledContentColor = TextMuted),
    ) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, Modifier.widthIn(max = 280.dp).padding(top = 7.dp), textAlign = TextAlign.Center, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 10.sp, letterSpacing = 1.sp, color = TextSecondary)
}

private fun decimalToMinor(value: String): Long = value.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0
private val IndianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
private fun formatIndianNumber(value: Long): String = NumberFormat.getIntegerInstance(IndianLocale).format(value)
private fun money(minor: Long): String {
    val absolute = abs(minor)
    val formatter = NumberFormat.getNumberInstance(IndianLocale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = if (absolute % 100 == 0L) 0 else 2
    }
    return "₹${formatter.format(absolute / 100.0)}"
}
private fun signedMoney(value: Long): String = (if (value >= 0) "+" else "−") + money(value)
private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
private fun longDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
private fun dateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "TODAY"
    LocalDate.now().minusDays(1) -> "YESTERDAY"
    else -> shortDate(date).uppercase()
}
private fun timeLabel(transaction: Transaction): String = Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
private fun transactionMeta(transaction: Transaction): String {
    val date = Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))
    return buildString {
        append(date)
        transaction.accountTail?.let { append(" · ••$it") }
        append(" · ${transaction.source.name.lowercase().replaceFirstChar(Char::titlecase)}")
    }
}
