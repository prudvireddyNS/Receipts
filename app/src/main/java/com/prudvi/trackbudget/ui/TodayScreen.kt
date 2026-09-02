package com.prudvi.trackbudget.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.DashboardSnapshot
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TodayScreen(
    transactions: List<Transaction>,
    budget: Budget,
    onSettings: () -> Unit,
    onReview: () -> Unit,
    onTransaction: (Transaction) -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = rememberCurrentDate()
    val snapshot = remember(transactions, budget, today) { dashboard(transactions, budget) }
    val reviewCount = transactions.count { it.status in ReviewStatuses }
    val todayRows = remember(transactions, today) {
        transactions.filter { it.status in HomeStatuses && receiptDate(it.occurredAt) == today }
            .sortedByDescending { it.occurredAt }
    }
    val previousComparison = remember(transactions, budget, snapshot.range, snapshot.dayOfPeriod) {
        previousComparison(transactions, budget, snapshot)
    }
    val screenAppearedAt = remember { android.os.SystemClock.uptimeMillis() }
    // Set while a finger is down on the burn-up chart. The hero reads it and becomes a day readout.
    var scrub by remember { mutableStateOf<BurnUpScrub?>(null) }

    Column(modifier.fillMaxSize().background(receiptsColors.paper)) {
        Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp)) { HomeMasthead(onSettings) }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
        item("hero") { HomeHero(snapshot, budget, previousComparison, scrub, Modifier.receiptEnter(0)) }
        if (reviewCount > 0) item("review") { ReviewNudge(reviewCount, onReview, Modifier.padding(top = 11.dp).receiptEnter(1)) }
        item("stats") { HomeStats(snapshot, budget, transactions, Modifier.padding(top = 12.dp).receiptEnter(2)) }
        item("bars") {
            val fresh = remember { android.os.SystemClock.uptimeMillis() - screenAppearedAt < ReceiptsMotion.ENTER }
            BurnUpChart(snapshot, budget, Modifier.padding(top = 14.dp).receiptEnter(3), growChart = fresh, onScrub = { scrub = it })
        }
        item("today-label") { TodayHeader(todayRows, Modifier.padding(top = 16.dp).receiptEnter(4)) }
        if (todayRows.isEmpty()) {
            item("today-empty") {
                Box(
                    Modifier.fillMaxWidth().padding(top = 7.dp)
                        .clip(RoundedCornerShape(ReceiptsRadius.medium))
                        .background(receiptsColors.paper)
                        .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Text("No receipts yet", color = receiptsColors.fade, style = ReceiptsType.bodyStrong)
                }
            }
        } else {
            itemsIndexed(todayRows, key = { _, it -> "today-${it.id}" }) { index, transaction ->
                val fresh = remember { android.os.SystemClock.uptimeMillis() - screenAppearedAt < ReceiptsMotion.ENTER }
                val enter = if (fresh) Modifier.receiptEnter(5 + index, key = transaction.id, lift = 18.dp) else Modifier
                ReceiptRow(
                    transaction = transaction,
                    modifier = Modifier.padding(top = 6.dp).animateItem().then(enter),
                    onClick = { onTransaction(transaction) },
                )
            }
        }
        item("categories") {
            val fresh = remember { android.os.SystemClock.uptimeMillis() - screenAppearedAt < ReceiptsMotion.ENTER }
            CategoryTotals(snapshot.categoryTotals, onCategory, Modifier.padding(top = 16.dp, bottom = 8.dp), animateEntrance = fresh)
        }
        }
    }
}

@Composable
private fun HomeMasthead(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("RECEIPTS", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.heading.copy(fontSize = 17.sp), maxLines = 1)
        ReceiptIconButton(Icons.Default.Settings, "Open settings", onSettings, background = receiptsColors.cyan, size = 44.dp)
    }
}

@Composable
private fun HomeHero(
    snapshot: DashboardSnapshot,
    budget: Budget,
    previousComparison: Pair<String, String>,
    scrub: BurnUpScrub?,
    modifier: Modifier = Modifier,
) {
    val hasBudget = budget.amountMinor > 0L
    val progress = if (hasBudget) snapshot.spentMinor.toFloat() / budget.amountMinor.coerceAtLeast(1L) else 0f
    val overPace = hasBudget && progress > snapshot.dayOfPeriod.toFloat() / snapshot.daysInPeriod.coerceAtLeast(1)
    // Only the card's ground fades between modes; the numbers themselves must track the finger exactly,
    // so the readout uses the un-animated hero amount rather than the counting-up one.
    val motionEnabled = rememberMotionEnabled()
    val background by animateColorAsState(
        targetValue = if (scrub != null) receiptsColors.warm else receiptsColors.yellow,
        animationSpec = if (motionEnabled) {
            androidx.compose.animation.core.tween(ReceiptsMotion.KEY * 2)
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "hero-ground",
    )
    Column(
        modifier.fillMaxWidth()
            .mockShadow(ReceiptsRadius.hero, 4.dp, 4.dp, receiptsColors.ink)
            .clip(RoundedCornerShape(ReceiptsRadius.hero))
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.hero))
            .padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val caption = if (scrub != null) {
                scrub.date.format(ScrubDateFormat).uppercase(Locale.ENGLISH)
            } else {
                "SPENT ${if (budget.period == "Week") "THIS WEEK" else "THIS MONTH"}"
            }
            Text(caption, Modifier.weight(1f), color = receiptsColors.chromeOn, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), maxLines = 1)
            Text(if (budget.period == "Week") periodLabel(snapshot.range) else receiptMonth(snapshot.range.start), color = receiptsColors.chromeOn, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (scrub != null) {
            // The readout's sub-line eats the amount's bottom padding, so entering scrub mode doesn't
            // shove the pace rail and the pill row down the card.
            ReceiptHeroAmount(netMoney(scrub.cumulativeMinor), Modifier.padding(top = 6.dp), color = receiptsColors.chromeOn)
            Text(
                "spent by then · ${netMoney(scrub.dayMinor)} that day",
                Modifier.padding(top = 1.dp, bottom = 2.dp),
                color = receiptsColors.chromeOn,
                style = ReceiptsType.bodyStrong.copy(fontSize = 11.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            ReceiptAnimatedHeroAmount(snapshot.spentMinor, ::netMoney, Modifier.padding(top = 6.dp, bottom = 9.dp), color = receiptsColors.chromeOn)
        }
        if (hasBudget) {
            ReceiptPaceRail(
                progress = progress,
                fill = if (overPace) receiptsColors.pink else receiptsColors.mint,
                description = "Budget ${receiptMoney(snapshot.spentMinor)} of ${receiptMoney(budget.amountMinor)}",
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
                        .background(receiptsColors.paper)
                        .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                        .padding(horizontal = 11.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("BUDGET", color = receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 9.sp, letterSpacing = 1.sp))
                    Text(receiptMoney(budget.amountMinor), color = receiptsColors.ink, style = ReceiptsType.amount)
                }
                Text("${(snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(1)} days left", color = receiptsColors.chromeOn, style = ReceiptsType.bodyStrong.copy(fontSize = 11.5.sp))
            }
        } else {
            Row(
                Modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .background(receiptsColors.paper)
                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(previousComparison.first, style = ReceiptsType.heading.copy(fontSize = 13.sp), color = receiptsColors.ink)
                Text(previousComparison.second, style = ReceiptsType.bodyStrong.copy(fontSize = 11.5.sp), color = receiptsColors.ink)
            }
        }
    }
}

@Composable
private fun ReviewNudge(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val checkColor = receiptsColors.chilliOn
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .mockShadow(ReceiptsRadius.large, 4.dp, 4.dp, receiptsColors.ink)
            .clip(RoundedCornerShape(ReceiptsRadius.large))
            .background(receiptsColors.pink)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.large))
            .clickable(role = Role.Button, onClick = onReview)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.width(26.dp).height(26.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(receiptsColors.paper)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
            contentAlignment = Alignment.Center,
        ) { Text(count.toString(), color = receiptsColors.pink, style = ReceiptsType.amount) }
        Text("receipts need you", Modifier.weight(1f), color = receiptsColors.chilliOn, style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp))
        Text(
            "CHECK",
            color = checkColor,
            style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            modifier = Modifier.drawBehind {
                drawLine(checkColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            },
        )
    }
}

@Composable
private fun HomeStats(snapshot: DashboardSnapshot, budget: Budget, transactions: List<Transaction>, modifier: Modifier = Modifier) {
    val hasBudget = budget.amountMinor > 0L
    val daysLeft = (snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(1)
    val leftPerDay = if (hasBudget && daysLeft > 0) snapshot.remainingMinor.coerceAtLeast(0) / daysLeft else snapshot.spentMinor / snapshot.dayOfPeriod.coerceAtLeast(1)
    // Committed spend (rent, a lump transfer) is already fully paid — project only the flexible
    // share forward, then add committed back as a lump sum instead of smearing it across the period.
    val flexibleSpent = (snapshot.spentMinor - snapshot.committedMinor).coerceAtLeast(0)
    val headingFor = if (hasBudget) flexibleSpent / snapshot.dayOfPeriod.coerceAtLeast(1) * snapshot.daysInPeriod + snapshot.committedMinor else snapshot.dailyTotals.maxOfOrNull { abs(it.value) } ?: 0L
    val elapsed = snapshot.dayOfPeriod.coerceAtLeast(1)
    // `dailyTotals` leaves committed spend out (see dashboard()), so the day rent went out looks
    // empty there. Count the days off the transactions instead, with the same inclusion rule
    // dashboard() applies to spending, so a rent-only day is never called quiet.
    val quietDays = remember(transactions, snapshot.range.start, elapsed, budget.countInvestmentsAsSpending) {
        val lastDay = snapshot.range.start.plusDays((elapsed - 1).toLong())
        val spentOn = transactions.asSequence()
            .filter { it.status in HomeStatuses && it.direction == Direction.DEBIT }
            .filter { budget.countInvestmentsAsSpending || it.categoryId != "investment" }
            .map { receiptDate(it.occurredAt) }
            .filter { !it.isBefore(snapshot.range.start) && !it.isAfter(lastDay) }
            .toSet()
        (elapsed - spentOn.size).coerceAtLeast(0)
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val cyanLabel = if (receiptsColors.monochrome) receiptsColors.ink else androidx.compose.ui.graphics.Color(0xFF0E7C86)
        StatTile(if (hasBudget) "Left per day" else "Daily average", receiptMoney(leftPerDay), receiptsColors.cyan, cyanLabel, Modifier.weight(1f))
        StatTile("Quiet days", "$quietDays of $elapsed", receiptsColors.warm, receiptsColors.ink, Modifier.weight(1f))
        val over = hasBudget && headingFor > budget.amountMinor
        StatTile(if (hasBudget) "Heading for" else "Biggest day", receiptCompactMoney(headingFor), if (over) receiptsColors.pinkTint else receiptsColors.mintTint, if (over) receiptsColors.pink else receiptsColors.ink, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, background: androidx.compose.ui.graphics.Color, labelColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(ReceiptsRadius.large))
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.large))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // 8.5/0.7 rather than 9/1.1: at three tiles across, "DAILY AVERAGE" clips at the wider setting.
        Text(label.uppercase(), color = labelColor, style = ReceiptsType.label.copy(fontSize = 8.5.sp, letterSpacing = 0.7.sp), maxLines = 1)
        Text(value, color = receiptsColors.ink, style = ReceiptsType.heading.copy(fontSize = 24.sp, letterSpacing = (-0.7).sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One day of the burn-up series: what the curve is worth there, and what that day alone cost. */
private data class BurnUpScrub(val date: LocalDate, val cumulativeMinor: Long, val dayMinor: Long)

private val ScrubDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
private fun BurnUpChart(
    snapshot: DashboardSnapshot,
    budget: Budget,
    modifier: Modifier = Modifier,
    growChart: Boolean = true,
    onScrub: (BurnUpScrub?) -> Unit = {},
) {
    val colors = receiptsColors
    val days = snapshot.daysInPeriod.coerceAtLeast(1)
    val dayOfPeriod = snapshot.dayOfPeriod.coerceIn(1, days)
    val hasBudget = budget.amountMinor > 0L

    // `dailyTotals` leaves committed spend (rent, a lump transfer) out so one big payment doesn't
    // crater day-by-day pacing — see dashboard(). A burn-up is read against the whole budget, so the
    // committed block rides along as a day-one baseline: the curve still lands on `spentMinor` today.
    // One series, used both to draw the curve and to answer a scrub, so the readout can never disagree
    // with the point the finger is on.
    val series = remember(snapshot) {
        var running = snapshot.committedMinor
        (0 until dayOfPeriod).map { offset ->
            val date = snapshot.range.start.plusDays(offset.toLong())
            val own = snapshot.dailyTotals[date] ?: 0L
            running += own
            BurnUpScrub(date, running, own)
        }
    }
    val spent = series.last().cumulativeMinor
    // Same projection the "Heading for" tile shows: only the flexible share is extrapolated.
    val flexibleSpent = (snapshot.spentMinor - snapshot.committedMinor).coerceAtLeast(0)
    val projected = flexibleSpent / dayOfPeriod * days + snapshot.committedMinor
    val ceiling = budget.amountMinor
    val topValue = maxOf(ceiling, projected, spent).coerceAtLeast(1L) * 1.14
    val overPace = hasBudget && spent > ceiling.toDouble() / days * dayOfPeriod
    val overBudget = hasBudget && projected > ceiling
    val projectionColor = when {
        !hasBudget -> colors.inkSoft
        overBudget -> colors.pink
        else -> colors.mint
    }
    val areaColor = when {
        !hasBudget -> colors.ink.copy(alpha = 0.07f)
        overPace -> colors.pink.copy(alpha = 0.20f)
        else -> colors.mint.copy(alpha = 0.22f)
    }
    val ticks = remember(days) { burnUpTicks(days) }
    val measurer = rememberTextMeasurer()
    val tickStyle = ReceiptsType.stamp.copy(fontSize = 9.sp, letterSpacing = 0.4.sp, color = colors.fade)
    val ceilingStyle = ReceiptsType.stamp.copy(fontSize = 9.sp, letterSpacing = 0.6.sp, color = colors.ink)
    val projectionStyle = ReceiptsType.amount.copy(fontSize = 10.5.sp, color = colors.ink)

    val motionEnabled = rememberMotionEnabled() && growChart
    val drawn = remember { Animatable(if (motionEnabled) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(motionEnabled, dayOfPeriod) {
        if (!motionEnabled) {
            drawn.snapTo(1f)
            return@LaunchedEffect
        }
        drawn.snapTo(0f)
        kotlinx.coroutines.delay(ReceiptsMotion.STAGGER.toLong() * 3)
        drawn.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
            ),
        )
    }
    val progress = drawn.value
    val lineFraction = (progress / 0.72f).coerceIn(0f, 1f)
    val projectionFraction = ((progress - 0.70f) / 0.30f).coerceIn(0f, 1f)
    // Which day the finger is on, 1-based, or null at rest. Kept beside the hoisted copy in
    // TodayScreen: both are written from the one gesture handler below, so they cannot drift.
    var scrubDay by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val report = androidx.compose.runtime.rememberUpdatedState(onScrub)
    // Scrolling the chart out of the list would otherwise strand the hero in readout mode.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { report.value(null) } }

    val description = "Burn-up. ${receiptMoney(spent)} spent by day $dayOfPeriod of $days" +
        (if (hasBudget) ", budget ${receiptMoney(ceiling)}" else "") +
        ", heading for ${receiptMoney(projected)}"

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            ReceiptLabel(if (hasBudget) "Burn-up vs budget" else "Burn-up", Modifier.weight(1f))
            Text(
                (if (hasBudget) "heading for " else "on this pace ") + receiptCompactMoney(projected),
                color = if (overBudget) colors.pink else colors.fade,
                style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 0.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 7.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.large))
                .background(colors.paper)
                .border(ReceiptsStroke.width, colors.ink, RoundedCornerShape(ReceiptsRadius.large))
                .padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Canvas(
                Modifier.fillMaxWidth().height(184.dp)
                    // Horizontal-only gesture: the down is never consumed, and nothing is claimed until
                    // the pointer clears touch slop *horizontally*. A vertical drag therefore reaches the
                    // LazyColumn untouched, and the moment the list claims it this loop bails out.
                    .pointerInput(series, days) {
                        val padLeft = 3.dp.toPx()
                        val padRight = 3.dp.toPx()

                        fun dayAt(x: Float): Int =
                            burnUpDayAt(x, padLeft, size.width - padLeft - padRight, days, series.size)

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var day = dayAt(down.position.x)
                            scrubDay = day
                            report.value(series[day - 1])
                            val past = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            if (past != null) {
                                fun select(x: Float) {
                                    val next = dayAt(x)
                                    if (next == day) return
                                    day = next
                                    scrubDay = next
                                    report.value(series[next - 1])
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                select(past.position.x)
                                horizontalDrag(past.id) { change ->
                                    select(change.position.x)
                                    change.consume()
                                }
                            }
                            // Covers all three endings: a plain lift, a cancel, and the list stealing the
                            // gesture (which returns null slop). The hero can never stick in readout mode.
                            scrubDay = null
                            report.value(null)
                        }
                    }
                    .semantics { contentDescription = description },
            ) {
                val padLeft = 3.dp.toPx()
                val padRight = 3.dp.toPx()
                val padTop = 19.dp.toPx()
                val padBottom = 19.dp.toPx()
                val plotWidth = (size.width - padLeft - padRight).coerceAtLeast(1f)
                val plotHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val baseY = padTop + plotHeight
                val gap = 3.dp.toPx()

                fun xOf(day: Int): Float =
                    if (days == 1) padLeft + plotWidth / 2f else padLeft + (day - 1).toFloat() / (days - 1) * plotWidth

                fun yOf(value: Long): Float =
                    baseY - (value.toDouble() / topValue).coerceIn(0.0, 1.0).toFloat() * plotHeight

                val points = series.mapIndexed { index, day -> Offset(xOf(index + 1), yOf(day.cumulativeMinor)) }
                val headIndexFloat = if (points.size == 1) 0f else (points.size - 1) * lineFraction
                val headIndex = floor(headIndexFloat).toInt().coerceIn(0, points.size - 1)
                val head = if (headIndex >= points.size - 1) points.last() else {
                    val t = headIndexFloat - headIndex
                    Offset(lerp(points[headIndex].x, points[headIndex + 1].x, t), lerp(points[headIndex].y, points[headIndex + 1].y, t))
                }

                // Area under "your line", clipped to however much of the line has drawn in.
                if (points.size > 1 && head.x > points.first().x) {
                    val area = Path().apply {
                        moveTo(points[0].x, baseY)
                        lineTo(points[0].x, points[0].y)
                        for (k in 1..headIndex) lineTo(points[k].x, points[k].y)
                        lineTo(head.x, head.y)
                        lineTo(head.x, baseY)
                        close()
                    }
                    drawPath(area, areaColor)
                }

                // Budget ceiling.
                if (hasBudget) {
                    val ceilingY = yOf(ceiling)
                    drawLine(colors.ink.copy(alpha = 0.40f), Offset(padLeft, ceilingY), Offset(padLeft + plotWidth, ceilingY), strokeWidth = 1.6.dp.toPx())
                }

                // Even pace: the diagonal that lands exactly on budget on the last day. With no budget
                // set it becomes the chord of your own average so far, so the curve still has a datum.
                val paceEnd = if (hasBudget) Offset(xOf(days), yOf(ceiling)) else points.last()
                val paceStart = Offset(xOf(1), baseY)
                val paceTip = if (hasBudget) paceEnd else Offset(lerp(paceStart.x, paceEnd.x, lineFraction), lerp(paceStart.y, paceEnd.y, lineFraction))
                drawLine(
                    color = colors.ink.copy(alpha = 0.55f),
                    start = paceStart,
                    end = paceTip,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.5.dp.toPx())),
                    alpha = if (hasBudget) lineFraction else 1f,
                )

                // Projection: today's pace carried to the end of the period.
                val projectionEnd = Offset(xOf(days), yOf(projected))
                fun projectionYAt(x: Float): Float {
                    val run = projectionEnd.x - points.last().x
                    if (run <= 0f) return projectionEnd.y
                    val t = ((x - points.last().x) / run).coerceIn(0f, 1f)
                    return lerp(points.last().y, projectionEnd.y, t)
                }
                val projectionTip = Offset(
                    lerp(points.last().x, projectionEnd.x, projectionFraction),
                    lerp(points.last().y, projectionEnd.y, projectionFraction),
                )
                if (projectionFraction > 0f && dayOfPeriod < days) {
                    drawLine(
                        color = projectionColor,
                        start = points.last(),
                        end = projectionTip,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.5.dp.toPx(), 4.dp.toPx())),
                    )
                }

                // Your line.
                if (points.size > 1) {
                    val line = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (k in 1..headIndex) lineTo(points[k].x, points[k].y)
                        lineTo(head.x, head.y)
                    }
                    drawPath(line, colors.ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Projection endpoint, then today — today sits on top so it always reads.
                if (projectionFraction > 0f && dayOfPeriod < days) {
                    drawCircle(projectionColor, radius = 4.dp.toPx(), center = projectionTip)
                    drawCircle(colors.ink, radius = 4.dp.toPx(), center = projectionTip, style = Stroke(width = 1.6.dp.toPx()))
                }
                drawCircle(colors.paper, radius = 6.2.dp.toPx(), center = head)
                drawCircle(colors.ink, radius = 4.2.dp.toPx(), center = head)

                // Scrub indicator. Drawn as an overlay from `points`, which the entrance animation
                // never touches, so a scrub mid-entrance cannot disturb the curve growing underneath.
                scrubDay?.let { selected ->
                    val marked = points[(selected - 1).coerceIn(0, points.lastIndex)]
                    drawLine(
                        color = colors.purple,
                        start = Offset(marked.x, padTop),
                        end = Offset(marked.x, baseY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(colors.paper, radius = 6.2.dp.toPx(), center = marked)
                    drawCircle(colors.purple, radius = 4.2.dp.toPx(), center = marked)
                }

                // Labels last, each clamped inside the canvas so nothing can clip.
                val ceilingLabel = if (hasBudget) measurer.measure("BUDGET ${receiptMoney(ceiling)}", ceilingStyle) else null
                val projectionLabel = measurer.measure(receiptCompactMoney(projected), projectionStyle)
                val projectionLabelY = (projectionEnd.y - projectionLabel.size.height - gap)
                    .coerceIn(0f, size.height - projectionLabel.size.height)
                if (ceilingLabel != null) {
                    val ceilingLabelY = (yOf(ceiling) - ceilingLabel.size.height - gap)
                        .coerceIn(0f, size.height - ceilingLabel.size.height)
                    // Both labels live at the right edge. If the projection label lands on top of the
                    // budget caption, or the projection line runs straight through it, the caption
                    // moves to the left edge instead — nothing ever stacks or reads through a line.
                    val labelLeft = size.width - padRight - ceilingLabel.size.width
                    val spanStart = projectionYAt(labelLeft)
                    val spanEnd = projectionYAt(size.width - padRight)
                    val collides = dayOfPeriod < days && (
                        (ceilingLabelY < projectionLabelY + projectionLabel.size.height + gap &&
                            projectionLabelY < ceilingLabelY + ceilingLabel.size.height + gap) ||
                            (maxOf(spanStart, spanEnd) > ceilingLabelY - gap &&
                                minOf(spanStart, spanEnd) < ceilingLabelY + ceilingLabel.size.height + gap)
                        )
                    val ceilingLabelX = if (collides) padLeft else (size.width - padRight - ceilingLabel.size.width).coerceAtLeast(0f)
                    drawText(ceilingLabel, topLeft = Offset(ceilingLabelX, ceilingLabelY))
                }
                if (dayOfPeriod < days) {
                    drawText(
                        projectionLabel,
                        topLeft = Offset((size.width - padRight - projectionLabel.size.width).coerceAtLeast(0f), projectionLabelY),
                        alpha = ((projectionFraction - 0.45f) / 0.4f).coerceIn(0f, 1f),
                    )
                }
                ticks.forEach { day ->
                    val label = measurer.measure(day.toString(), tickStyle)
                    val x = (xOf(day) - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
                    drawText(label, topLeft = Offset(x, baseY + gap))
                }
            }
            Spacer(Modifier.fillMaxWidth().height(11.dp).receiptPerforation(colors.ink))
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                BurnUpKey("you", colors.ink, 3.dp, null)
                BurnUpKey(if (hasBudget) "even pace" else "your average", colors.ink.copy(alpha = 0.55f), 1.8.dp, listOf(5.dp, 4.5.dp))
                if (dayOfPeriod < days) BurnUpKey("projection", projectionColor, 2.dp, listOf(0.5.dp, 4.dp))
            }
        }
    }
}

@Composable
private fun BurnUpKey(label: String, color: Color, thickness: Dp, dash: List<Dp>?) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier.width(17.dp).height(8.dp).drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = thickness.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(it[0].toPx(), it[1].toPx())) },
                )
            },
        )
        Text(label, color = receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 9.sp, letterSpacing = 0.3.sp), maxLines = 1)
    }
}

/**
 * Inverse of the burn-up chart's `xOf`: the nearest day under a pointer at [x], clamped to
 * 1..[elapsed] so a scrub can never run into the part of the period that has not happened yet.
 * Kept out of the composable so the mapping is unit-testable.
 */
internal fun burnUpDayAt(x: Float, padLeft: Float, plotWidth: Float, days: Int, elapsed: Int): Int {
    if (days <= 1) return 1
    val t = ((x - padLeft) / plotWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return (1 + (t * (days - 1)).roundToInt()).coerceIn(1, elapsed.coerceAtLeast(1))
}

/** Four x-ticks at most, and fewer on a short period, so day numbers never crowd each other. */
private fun burnUpTicks(days: Int): List<Int> = when {
    days <= 4 -> (1..days).toList()
    days <= 10 -> listOf(1, (days + 1) / 2, days)
    else -> listOf(1, 1 + (days - 1) / 3, 1 + 2 * (days - 1) / 3, days)
}.distinct()

/** Compact rupees for the in-plot labels, where a fully grouped amount would not fit. */
private fun receiptCompactMoney(minor: Long): String {
    val rupees = abs(minor) / 100.0
    return when {
        rupees >= 10_000_000 -> "₹%.1fCr".format(Locale.ENGLISH, rupees / 10_000_000)
        rupees >= 100_000 -> "₹%.1fL".format(Locale.ENGLISH, rupees / 100_000)
        rupees >= 1_000 -> "₹%.1fk".format(Locale.ENGLISH, rupees / 1_000)
        else -> "₹${rupees.roundToInt()}"
    }
}

@Composable
private fun TodayHeader(rows: List<Transaction>, modifier: Modifier = Modifier) {
    val total = rows.filter { it.direction == Direction.DEBIT }.sumOf { it.amountMinor }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        ReceiptLabel("Today")
        Text(receiptMoney(total), color = receiptsColors.ink, style = ReceiptsType.amount.copy(fontSize = 11.sp))
    }
}

/** One ring wedge. A null [categoryId] is the synthetic "Other" bucket — it is never tappable. */
private class DonutSlice(
    val categoryId: String?,
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float,
)

private val DonutSize = 112.dp
private val DonutRing = 15.dp
private val DonutSeam = 2.2.dp

/** Below this share a category becomes a sliver nobody can read, so it joins "Other". */
private const val DonutMinShare = 0.04f

/**
 * Largest-remainder rounding, so the printed shares add to exactly 100 — plain rounding lands on
 * 99% or 101% often enough to read as a bug.
 */
private fun sharePercents(amounts: List<Long>): List<Int> {
    if (amounts.isEmpty()) return emptyList()
    val total = amounts.sum().coerceAtLeast(1L)
    val exact = amounts.map { it.toDouble() * 100.0 / total }
    val out = exact.map { floor(it).toInt() }.toMutableList()
    var spare = 100 - out.sum()
    val byRemainder = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
    var i = 0
    while (spare > 0 && i < byRemainder.size * 2) {
        out[byRemainder[i % byRemainder.size]]++
        spare--
        i++
    }
    return out
}

@Composable
private fun CategoryTotals(totals: Map<String, Long>, onCategory: (String) -> Unit, modifier: Modifier = Modifier, animateEntrance: Boolean = true) {
    val rows = totals.toList().filter { it.second != 0L }.sortedByDescending { abs(it.second) }
    val positives = rows.filter { it.second > 0L }.sortedByDescending { it.second }
    val total = positives.sumOf { it.second }.coerceAtLeast(1L)
    val percents = remember(positives) {
        val values = sharePercents(positives.map { it.second })
        positives.mapIndexed { index, row -> row.first to values[index] }.toMap()
    }

    // Slivers collapse into one "Other" wedge — but a lone straggler stays itself rather than
    // becoming a bucket of one, which would only cost it its tap target.
    val slivers = positives.filter { it.second.toFloat() / total < DonutMinShare }
    val wedged = if (slivers.size <= 1) positives else positives - slivers.toSet()
    val otherAmount = if (slivers.size <= 1) 0L else slivers.sumOf { it.second }
    val wedges = wedged.map { (id, amount) -> Triple(id as String?, amount, categoryVisual(id).color) } +
        (if (otherAmount > 0L) listOf(Triple(null, otherAmount, receiptsColors.fade)) else emptyList())
    val slices = remember(wedges) {
        var cursor = 0f
        wedges.sortedByDescending { it.second }.map { (id, amount, color) ->
            val slice = DonutSlice(id, color, cursor, amount.toFloat() / total * 360f)
            cursor += slice.sweepAngle
            slice
        }
    }

    val expanded = remember { androidx.compose.runtime.mutableStateOf(false) }
    val legend = rows.take(4)
    val extras = rows.drop(4)

    Column(modifier.fillMaxWidth()) {
        ReceiptLabel("Where it went")
        if (rows.isEmpty()) {
            Text("Nothing to split yet.", color = receiptsColors.fade, style = ReceiptsType.body, modifier = Modifier.padding(top = 9.dp))
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryDonut(slices, positives.firstOrNull(), percents, onCategory, animateEntrance)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                legend.forEachIndexed { index, (categoryId, amount) ->
                    CategoryLegendRow(categoryId, amount, percents[categoryId], onCategory, index, animateEntrance)
                }
            }
        }
        if (extras.isNotEmpty()) {
            val extrasTotal = extras.sumOf { it.second }
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp).defaultMinSize(minHeight = 34.dp)
                    .clip(RoundedCornerShape(ReceiptsRadius.small))
                    .background(receiptsColors.sunk)
                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.small))
                    .clickable(role = Role.Button) { expanded.value = !expanded.value }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .semantics { contentDescription = "${extras.size} more categories, ${netMoney(extrasTotal)}" },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+${extras.size} MORE", color = receiptsColors.ink, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 0.8.sp), maxLines = 1)
                    Text(netMoney(extrasTotal), color = if (extrasTotal < 0L) receiptsColors.purple else receiptsColors.fade, style = ReceiptsType.amount.copy(fontSize = 11.5.sp), maxLines = 1)
                }
                val underline = receiptsColors.fade
                Text(
                    if (expanded.value) "HIDE" else "SHOW",
                    color = underline,
                    style = ReceiptsType.label.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                    modifier = Modifier.drawBehind {
                        drawLine(underline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.6.dp.toPx())
                    },
                )
            }
            if (expanded.value) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    extras.forEachIndexed { index, (categoryId, amount) ->
                        CategoryLegendRow(categoryId, amount, percents[categoryId], onCategory, index, animateEntrance = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(
    slices: List<DonutSlice>,
    top: Pair<String, Long>?,
    percents: Map<String, Int>,
    onCategory: (String) -> Unit,
    animateEntrance: Boolean,
) {
    val ink = receiptsColors.ink
    val motionEnabled = rememberMotionEnabled() && animateEntrance
    val sweep = remember { Animatable(if (motionEnabled) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(motionEnabled, slices.size) {
        if (!motionEnabled) {
            sweep.snapTo(1f)
            return@LaunchedEffect
        }
        sweep.snapTo(0f)
        kotlinx.coroutines.delay(ReceiptsMotion.STAGGER.toLong() * 2)
        sweep.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
            ),
        )
    }
    val drawnTo = 360f * sweep.value
    val description = slices.joinToString(", ", prefix = "Spending by category. ") { slice ->
        "${if (slice.categoryId == null) "Other" else categoryName(slice.categoryId)} ${(slice.sweepAngle / 360f * 100f).roundToInt()}%"
    }

    Box(Modifier.size(DonutSize), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val outer = minOf(size.width, size.height) / 2f
                        val inner = outer - DonutRing.toPx() - DonutSeam.toPx()
                        val dx = offset.x - size.width / 2f
                        val dy = offset.y - size.height / 2f
                        val distance = hypot(dx, dy)
                        if (distance < inner || distance > outer) return@detectTapGestures
                        // Screen angle runs clockwise from 3 o'clock; the ring starts at 12.
                        val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 450f) % 360f
                        slices.firstOrNull { angle >= it.startAngle && angle < it.startAngle + it.sweepAngle }
                            ?.categoryId?.let(onCategory)
                    }
                }
                .semantics { contentDescription = description },
        ) {
            val seam = DonutSeam.toPx()
            val outer = size.minDimension / 2f - seam / 2f
            val thickness = DonutRing.toPx()
            val ringRadius = outer - thickness / 2f
            val inner = outer - thickness
            val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            val arcSize = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f)
            slices.forEach { slice ->
                val drawn = (drawnTo - slice.startAngle).coerceIn(0f, slice.sweepAngle)
                if (drawn <= 0f) return@forEach
                // A 360° stroked arc leaves a hairline seam where the ends meet; a circle does not.
                if (slice.sweepAngle >= 359.5f && drawn >= slice.sweepAngle) {
                    drawCircle(slice.color, radius = ringRadius, style = Stroke(thickness))
                } else {
                    drawArc(
                        color = slice.color,
                        startAngle = -90f + slice.startAngle,
                        sweepAngle = drawn,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(thickness),
                    )
                }
            }
            drawCircle(ink, radius = outer, style = Stroke(seam))
            drawCircle(ink, radius = inner, style = Stroke(seam))
            // In the monochrome themes hue can't separate neighbours, so these seams do the work.
            if (slices.size > 1) slices.forEach { slice ->
                if (drawnTo <= slice.startAngle) return@forEach
                val radians = Math.toRadians((slice.startAngle - 90f).toDouble())
                val ux = cos(radians).toFloat()
                val uy = sin(radians).toFloat()
                drawLine(
                    ink,
                    Offset(center.x + ux * inner, center.y + uy * inner),
                    Offset(center.x + ux * outer, center.y + uy * outer),
                    strokeWidth = seam,
                )
            }
        }
        if (top != null) {
            val name = categoryName(top.first).uppercase()
            // The hole gives 68dp of usable width; step the mono down so a long name never ellipsises.
            val nameSize = when {
                name.length <= 9 -> 9.5.sp
                name.length <= 11 -> 9.sp
                else -> 8.sp
            }
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${percents[top.first] ?: 0}%",
                    color = receiptsColors.ink,
                    style = ReceiptsType.heading,
                    maxLines = 1,
                )
                Text(
                    name,
                    color = receiptsColors.fade,
                    style = ReceiptsType.stamp.copy(fontSize = nameSize, letterSpacing = 0.3.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(
    categoryId: String,
    amount: Long,
    percent: Int?,
    onCategory: (String) -> Unit,
    index: Int,
    animateEntrance: Boolean,
) {
    val visual = categoryVisual(categoryId)
    val share = when {
        amount <= 0L -> "—"
        percent == null || percent <= 0 -> "<1%"
        else -> "$percent%"
    }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp)
            .then(if (animateEntrance) Modifier.receiptEnter(index, key = categoryId, lift = 14.dp) else Modifier)
            .clickable(role = Role.Button) { onCategory(categoryId) }
            .semantics { contentDescription = "${categoryName(categoryId)} ${receiptMoney(amount)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(20.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.tiny))
                .background(visual.color)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.tiny)),
            contentAlignment = Alignment.Center,
        ) {
            Text(visual.mark, color = categoryMarkTextColor(visual.color), style = ReceiptsType.stamp.copy(fontSize = 7.5.sp, letterSpacing = 0.sp), maxLines = 1)
        }
        Text(
            categoryName(categoryId),
            Modifier.weight(1f),
            color = receiptsColors.ink,
            style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(netMoney(amount), color = if (amount < 0L) receiptsColors.purple else receiptsColors.ink, style = ReceiptsType.amount.copy(fontSize = 11.5.sp), maxLines = 1)
        Text(
            share,
            Modifier.width(30.dp),
            color = receiptsColors.fade,
            style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun previousComparison(transactions: List<Transaction>, budget: Budget, snapshot: DashboardSnapshot): Pair<String, String> {
    val days = snapshot.dayOfPeriod.coerceAtLeast(1)
    val previousEnd = snapshot.range.start.minusDays(1)
    val previousStart = previousEnd.minusDays((days - 1).toLong())
    val zone = ZoneId.systemDefault()
    val start = previousStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = previousEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val previous = transactions.filter { it.status in HomeStatuses && it.occurredAt in start until end }.sumOf {
        when {
            it.direction == Direction.CREDIT -> -it.amountMinor
            it.categoryId == "investment" && !budget.countInvestmentsAsSpending -> 0L
            else -> it.amountMinor
        }
    }
    val difference = snapshot.spentMinor - previous
    val word = if (days == 1) "day" else "days"
    return when {
        difference > 0L -> "↑" to "${receiptMoney(difference)} more than the $days $word before"
        difference < 0L -> "↓" to "${receiptMoney(abs(difference))} less than the $days $word before"
        else -> "=" to "Same as the $days $word before"
    }
}

private fun netMoney(amountMinor: Long): String = if (amountMinor < 0L) "−${receiptMoney(abs(amountMinor))}" else receiptMoney(amountMinor)

private val ReviewStatuses = setOf(
    TransactionStatus.CATEGORY_REVIEW,
    TransactionStatus.NEEDS_REVIEW,
    TransactionStatus.NEEDS_RESOLUTION,
    TransactionStatus.UNPARSEABLE,
)
private val HomeStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)
