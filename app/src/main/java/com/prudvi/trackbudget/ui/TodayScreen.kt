package com.prudvi.trackbudget.ui
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.DashboardSnapshot
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.PeriodSnapshot
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import com.prudvi.trackbudget.model.rollingDailyBaseline
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    transactions: List<Transaction>,
    budget: Budget,
    mode: AppMode,
    amplitude: AppAmplitude,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onReview: () -> Unit,
    onTransaction: (Transaction) -> Unit,
    onCategory: (String) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    goals: List<Goal> = emptyList(),
    periodSnapshots: List<PeriodSnapshot> = emptyList(),
    scanning: Boolean = false,
) {
    val today = rememberCurrentDate()
    val snapshot = remember(transactions, budget, today) { dashboard(transactions, budget) }
    val motionEnabled = rememberMotionEnabled()
    val view = LocalView.current
    val reviewCount = transactions.count { it.status in ReviewStatuses }
    val todayRows = remember(transactions, today) {
        transactions.filter {
            it.status in TodayStatuses && receiptDate(it.occurredAt) == today
        }.sortedByDescending { it.occurredAt }
    }
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    val topGoal = remember(goals) {
        goals.filterNot { it.isComplete }.sortedWith(compareBy<Goal> { it.targetEpochDay ?: Long.MAX_VALUE }.thenBy { it.createdAt }).firstOrNull()
    }
    PullToRefreshBox(
        isRefreshing = scanning,
        onRefresh = {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CLOCK_TICK)
            onRescan()
        },
        modifier = modifier.fillMaxSize().background(receiptsColors.paper),
        indicator = {
            if (scanning) ReceiptPill("Scanning", selected = true)
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ReceiptsSpace.screen,
                end = ReceiptsSpace.screen,
                top = ReceiptsSpace.x4,
                bottom = ReceiptsSpace.x16,
            ),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
        ) {
            item("brand") { TodayMasthead(onSettings, onAdd) }
            item("hero") {
                Column(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (historyVisible) historyVisible = false },
                        onLongClickLabel = "Show previous periods",
                        onLongClick = { historyVisible = true },
                    ),
                ) {
                    ReceiptLabel("${periodLabel(snapshot.range)} · day ${snapshot.dayOfPeriod} of ${snapshot.daysInPeriod}")
                    Spacer(Modifier.height(ReceiptsSpace.x3))
                    val loud = amplitude == AppAmplitude.LOUD
                    when (mode) {
                        AppMode.CHILL -> ChillHero(snapshot.spentMinor, periodSnapshots, spendLabel(budget.period), loud)
                        AppMode.PACE -> PaceHero(transactions, budget, snapshot, loud)
                        AppMode.STACK -> StackHero(topGoal, loud)
                    }
                    if (historyVisible) {
                        Spacer(Modifier.height(ReceiptsSpace.x3))
                        periodSnapshots.sortedByDescending { it.closedAt }.take(3).forEach { previous ->
                            Row(Modifier.fillMaxWidth().padding(vertical = ReceiptsSpace.x1)) {
                                Text(receiptMonth(receiptDate(previous.closedAt)), Modifier.weight(1f), color = receiptsColors.fade, style = ReceiptsType.meta)
                                Text(receiptMoney(previous.spentMinor), color = receiptsColors.ink, style = ReceiptsType.amount)
                            }
                        }
                        if (periodSnapshots.isEmpty()) Text("Previous periods appear here after they close.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
            item("review") {
                ReceiptPerforation()
                if (mode == AppMode.STACK) {
                    Text("${receiptMoney(snapshot.spentMinor)} ${spendLabel(budget.period).lowercase()}.", color = receiptsColors.fade, style = ReceiptsType.amount)
                    Spacer(Modifier.height(ReceiptsSpace.x3))
                }
                if (reviewCount > 0) ReviewNudge(reviewCount, onReview) else CleanReviewNudge()
            }
            item("today-label") { ReceiptLabel("Today") }
            if (todayRows.isEmpty()) {
                item("today-empty") { ReceiptEmptyState("Nothing yet.", "Pay for something and this fills itself in.") }
            } else {
                itemsIndexed(todayRows, key = { _, item -> "today-${item.id}" }) { index, transaction ->
                    AnimatedVisibility(
                        visible = !scanning,
                        enter = fadeIn(tween(if (motionEnabled) 160 else 0, delayMillis = if (motionEnabled) minOf(index, 8) * 60 else 0)) +
                            slideInVertically(tween(if (motionEnabled) 160 else 0, delayMillis = if (motionEnabled) minOf(index, 8) * 60 else 0)) { -it },
                        exit = ExitTransition.None,
                    ) {
                        ReceiptRow(transaction = transaction, onClick = { onTransaction(transaction) })
                    }
                }
            }
            item("where-it-went") { WhereItWentRail(snapshot.categoryTotals, onCategory) }
        }
    }
}
@Composable
private fun TodayMasthead(onSettings: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("RECEIPTS", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
        ReceiptIconButton(Icons.Default.Add, "New receipt", onAdd)
        ReceiptIconButton(Icons.Default.Settings, "Open settings", onSettings)
    }
    ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x2))
}
@Composable
private fun ChillHero(spentMinor: Long, periodSnapshots: List<PeriodSnapshot>, label: String, loud: Boolean) {
    val previous = periodSnapshots.maxByOrNull { it.closedAt }
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        ReceiptLabel(label)
        ReceiptHeroAmount(receiptMoney(spentMinor), loud = loud)
        Text(
            previous?.let { "${receiptMoney(it.spentMinor)} last ${receiptMonth(receiptDate(it.closedAt))}" } ?: "No pace rail. Just receipts.",
            color = receiptsColors.fade,
            style = ReceiptsType.amount,
        )
    }
}
@Composable
private fun PaceHero(transactions: List<Transaction>, budget: Budget, snapshot: DashboardSnapshot, loud: Boolean) {
    val motionEnabled = rememberMotionEnabled()
    val rolling = budget.period == "Rolling"
    val rollingBaseline = remember(transactions, snapshot.range) { rollingDailyBaseline(transactions, snapshot.range.start) }
    val budgetMinor = if (rolling) (rollingBaseline ?: 0L) * snapshot.daysInPeriod else budget.amountMinor
    val marker = if (rolling) {
        val maximum = maxOf(snapshot.spentMinor, budgetMinor, 1L)
        budgetMinor.toFloat() / maximum
    } else {
        snapshot.dayOfPeriod.toFloat() / snapshot.daysInPeriod.coerceAtLeast(1)
    }.coerceIn(0f, 1f)
    val target = if (rolling) budgetMinor else (budgetMinor * marker).roundToLong()
    val state = PaceState.from(snapshot.spentMinor.toFloat() / target.coerceAtLeast(1L))
    val progress = if (rolling) {
        snapshot.spentMinor.toFloat() / maxOf(snapshot.spentMinor, budgetMinor, 1L)
    } else paceProgress(snapshot.spentMinor, budgetMinor)
    val daysLeft = (snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(1)
    val holdMinor = when {
        rolling -> rollingBaseline ?: 0L
        snapshot.remainingMinor >= 0 -> snapshot.remainingMinor / daysLeft
        else -> kotlin.math.abs(snapshot.remainingMinor)
    }
    val holdSuffix = when {
        rolling -> "/day is your usual pace"
        snapshot.remainingMinor >= 0 -> "/day holds this"
        else -> " past this budget"
    }
    val animatedAmount = remember { Animatable(if (motionEnabled) 0f else snapshot.spentMinor.toFloat()) }
    val animatedRail = remember { Animatable(if (motionEnabled) 0f else progress) }
    LaunchedEffect(snapshot.spentMinor, budgetMinor, motionEnabled) {
        if (!motionEnabled) {
            animatedAmount.snapTo(snapshot.spentMinor.toFloat())
            animatedRail.snapTo(progress)
        } else {
            animatedRail.snapTo(0f)
            animatedAmount.snapTo(0f)
            animatedAmount.animateTo(snapshot.spentMinor.toFloat(), tween(ReceiptsMotion.HERO, easing = FastOutSlowInEasing))
            animatedRail.animateTo(progress, spring(dampingRatio = 0.75f, stiffness = 380f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        ReceiptLabel(spendLabel(budget.period))
        ReceiptHeroAmount(receiptMoney(animatedAmount.value.toLong()), loud = loud)
        if (budgetMinor > 0) {
            ReceiptPaceRail(animatedRail.value, marker, receiptsColors.ink, description = "${state.words}, ${receiptMoney(snapshot.spentMinor)} recorded")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x1),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = paceColor(state), fontWeight = FontWeight.SemiBold)) { append(state.words) }
                        append(" · ")
                        withStyle(SpanStyle(fontFamily = ReceiptsFonts.splineSansMono, fontWeight = FontWeight.SemiBold)) { append(receiptWholeMoney(holdMinor)) }
                        append(holdSuffix)
                    },
                    color = receiptsColors.fade, style = ReceiptsType.meta,
                )
                if (!rolling) Text("$daysLeft left", color = receiptsColors.fade, style = ReceiptsType.amount, maxLines = 1)
            }
        } else {
            Text("30-day view", color = receiptsColors.fade, style = ReceiptsType.amount)
        }
    }
}
@Composable
private fun StackHero(goal: Goal?, loud: Boolean) {
    if (goal == null) {
        ReceiptEmptyState("Nothing saved for yet.", "What are you after?")
    } else {
        GoalTicket(goal, loud)
    }
}
@Composable
private fun GoalTicket(goal: Goal, loud: Boolean) {
    val remaining = (goal.targetMinor - goal.savedMinor).coerceAtLeast(0L)
    val months = goal.targetEpochDay?.let {
        ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), LocalDate.ofEpochDay(it).withDayOfMonth(1)).toInt().coerceAtLeast(1)
    } ?: 1
    val line = if (goal.targetEpochDay == null) "${receiptMoney(remaining)} left to make it" else "${receiptMoney(remaining / months)}/month to make it"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ReceiptsRadius.small))
            .border(ReceiptsSpace.x1 / 4f, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .padding(ReceiptsSpace.x4),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(goal.name, Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(receiptPercent(goal.progress), color = receiptsColors.fade, style = ReceiptsType.label)
        }
        ReceiptHeroAmount(receiptMoney(goal.savedMinor), loud = loud)
        GoalRail(goal.progress, "${goal.name}, ${receiptPercent(goal.progress)} saved")
        Text(line, color = receiptsColors.fade, style = ReceiptsType.amount)
    }
}
@Composable
private fun GoalRail(progress: Float, description: String) {
    Box(
        Modifier.fillMaxWidth().height(ReceiptsSpace.x2).clip(RoundedCornerShape(ReceiptsRadius.pill)).background(receiptsColors.sunk)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(receiptsColors.chrome))
    }
}
@Composable
private fun ReviewNudge(count: Int, onReview: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.chilliTint)
            .clickable(role = Role.Button, onClick = onReview).padding(ReceiptsSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        Text("$count ${if (count == 1) "receipt needs" else "receipts need"} you", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong)
        ReceiptPill("Review", style = ReceiptButtonStyle.CHILLI)
    }
}
@Composable
private fun CleanReviewNudge() {
    Text("Nothing needs you. Clean.", color = receiptsColors.fade, style = ReceiptsType.meta)
}
@Composable
private fun WhereItWentRail(totals: Map<String, Long>, onCategory: (String) -> Unit) {
    val visible = totals.filterValues { it > 0 }.toList().sortedByDescending { it.second }.take(5)
    val total = visible.sumOf { it.second }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
        ReceiptLabel("Where it went")
        if (visible.isEmpty()) {
            Text("Nothing to split yet.", color = receiptsColors.fade, style = ReceiptsType.meta)
        } else {
            Row(Modifier.fillMaxWidth().height(ReceiptsSpace.x2), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x1)) {
                visible.forEach { (categoryId, amount) ->
                    val visual = categoryVisual(categoryId)
                    Box(
                        Modifier.weight(amount.toFloat() / total).fillMaxHeight().background(visual.color)
                            .semantics { contentDescription = "${categoryName(categoryId)} ${receiptMoney(amount)}" },
                    )
                }
            }
            visible.take(4).forEach { (categoryId, amount) ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
                        .clickable(role = Role.Button) { onCategory(categoryId) }
                        .padding(vertical = ReceiptsSpace.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReceiptCategoryMark(categoryId)
                    Text(categoryName(categoryId), Modifier.padding(start = ReceiptsSpace.x2).weight(1f), color = receiptsColors.fade, style = ReceiptsType.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(receiptMoney(amount), color = receiptsColors.ink, style = ReceiptsType.amount)
                }
            }
        }
    }
}
private enum class PaceState(val words: String) {
    Under("under pace"),
    On("on pace"),
    Over("ahead of pace");
    companion object {
        fun from(ratio: Float): PaceState = when {
            ratio < 0.92f -> Under
            ratio > 1.08f -> Over
            else -> On
        }
    }
}
@Composable
private fun paceColor(state: PaceState): Color = when (state) {
    PaceState.Under -> receiptsColors.ultramarine
    PaceState.On -> receiptsColors.ink
    PaceState.Over -> receiptsColors.chilli
}
private fun paceProgress(spentMinor: Long, budgetMinor: Long): Float = if (budgetMinor <= 0) 0f else (spentMinor.toFloat() / budgetMinor).coerceIn(0f, 1f)
private val ReviewStatuses = setOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE)
private val TodayStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.EXCLUDED)
