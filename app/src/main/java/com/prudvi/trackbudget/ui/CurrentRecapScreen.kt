package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.countsAsSpend
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.netSpend
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CurrentRecapScreen(
    transactions: List<Transaction>,
    budget: Budget,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = onClose,
) {
    val recap = remember(transactions, budget) { buildRecap(transactions, budget) }
    val share = rememberShareCaptureState("receipts-recap")
    LazyColumn(
        modifier.fillMaxSize().background(receiptsColors.paper).imePadding().captureForShare(share),
        contentPadding = PaddingValues(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().background(receiptsColors.purple).padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 22.dp,
                    bottom = 24.dp,
                ),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECAP", Modifier.weight(1f), color = receiptsColors.ultramarineOn, style = ReceiptsType.label.copy(letterSpacing = 1.6.sp))
                    Box(
                        Modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
                            .border(ReceiptsStroke.width, receiptsColors.ultramarineOn, RoundedCornerShape(ReceiptsRadius.pill))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClose)
                            .padding(horizontal = 11.dp, vertical = 3.dp),
                    ) { Text("Close".uppercase(), color = receiptsColors.ultramarineOn, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 1.sp)) }
                }
                Text(recap.title, color = receiptsColors.ultramarineOn, style = ReceiptsType.display, modifier = Modifier.padding(top = 14.dp))
                Text(if (recap.spentMinor < 0L) "−${receiptMoney(abs(recap.spentMinor))}" else receiptMoney(recap.spentMinor), color = receiptsColors.ultramarineOn, style = ReceiptsType.recapHero, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
                if (recap.obligationsMinor > 0L) {
                    // The hero is what actually left the account. Fixed obligations were taken off
                    // the budget instead, so name them here rather than letting the gap go unexplained.
                    Text(
                        "plus ${receiptMoney(recap.obligationsMinor)} fixed, set aside off your budget",
                        color = receiptsColors.ultramarineOn,
                        style = ReceiptsType.meta.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 12.dp)
                        .clip(RoundedCornerShape(ReceiptsRadius.pill))
                        .border(ReceiptsStroke.width, receiptsColors.ultramarineOn, RoundedCornerShape(ReceiptsRadius.pill))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(recap.compareArrow, color = receiptsColors.ultramarineOn, style = ReceiptsType.heading.copy(fontSize = 13.sp))
                    Text(recap.comparison, color = receiptsColors.ultramarineOn, style = ReceiptsType.bodyStrong.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                ReceiptLabel("Where it went")
                Column(Modifier.fillMaxWidth().padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (recap.categories.isEmpty()) Text("Nothing to split yet.", color = receiptsColors.fade, style = ReceiptsType.body)
                    recap.categories.forEach { cat -> RecapCategoryRow(cat, recap.categoryMax, recap.categoryTotal) }
                }

                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecapTile("Biggest day", recap.biggestAmount, recap.biggestDay, receiptsColors.yellow, receiptsColors.chromeOn, Modifier.weight(1f), contentColor = receiptsColors.chromeOn)
                    RecapTile("Quiet days", recap.quietCount, recap.quietUnder, receiptsColors.cyan, ColorCyanLabel, Modifier.weight(1f))
                }

                ReceiptLabel("Biggest spends", modifier = Modifier.padding(top = 18.dp))
                Column(Modifier.fillMaxWidth().padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (recap.merchants.isEmpty()) Text("Nothing yet.", color = receiptsColors.fade, style = ReceiptsType.body)
                    recap.merchants.forEach { m -> MerchantRow(m) }
                }

                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptButton(
                        "Share as image",
                        share::share,
                        Modifier.weight(1f),
                        style = ReceiptButtonStyle.CHROME,
                        radius = ReceiptsRadius.card,
                        shadowOffset = 4.dp,
                        textStyle = ReceiptsType.button.copy(fontSize = 14.sp),
                    )
                    Box(
                        Modifier.defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(ReceiptsRadius.card))
                            .background(receiptsColors.paper)
                            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.card))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onDone)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Done", color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 13.sp)) }
                }
            }
        }
    }
}

@Composable
private fun RecapCategoryRow(cat: RecapCategory, max: Long, total: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReceiptCategoryMark(cat.categoryId, size = 28.dp, radius = 9.dp)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(categoryName(cat.categoryId), color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (cat.amountMinor < 0L) "−${receiptMoney(abs(cat.amountMinor))}" else receiptMoney(cat.amountMinor), color = if (cat.amountMinor < 0L) receiptsColors.purple else receiptsColors.ink, style = ReceiptsType.amount.copy(fontSize = 12.sp), maxLines = 1)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 5.dp).height(11.dp)
                    .clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .background(receiptsColors.paper)
                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
            ) {
                if (cat.amountMinor > 0L) {
                    Box(
                        Modifier.fillMaxWidth((cat.amountMinor.toFloat() / max.coerceAtLeast(1L)).coerceIn(0.04f, 1f)).fillMaxHeight()
                            .background(categoryVisual(cat.categoryId).color)
                            .border(ReceiptsStroke.width, receiptsColors.ink),
                    )
                }
            }
        }
        Text(if (cat.amountMinor > 0L) "${(cat.amountMinor.toFloat() / total.coerceAtLeast(1L) * 100f).roundToInt()}%" else "—", color = receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 10.5.sp, letterSpacing = 0.sp), modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun RecapTile(
    label: String,
    value: String,
    body: String,
    background: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    contentColor: androidx.compose.ui.graphics.Color = receiptsColors.ink,
) {
    Column(
        modifier.clip(RoundedCornerShape(ReceiptsRadius.large))
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.large))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(label.uppercase(), color = labelColor, style = ReceiptsType.label.copy(fontSize = 9.sp, letterSpacing = 1.1.sp))
        Text(value, color = contentColor, style = ReceiptsType.heading.copy(fontSize = 22.sp, letterSpacing = (-0.6).sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Text(body, color = contentColor, style = ReceiptsType.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MerchantRow(row: RecapMerchant) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(ReceiptsRadius.card))
            .background(receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.card))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(receiptsColors.warm)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
            contentAlignment = Alignment.Center,
        ) { Text(row.rank.toString(), color = receiptsColors.ink, style = ReceiptsType.amount.copy(fontSize = 11.sp)) }
        Text(row.name, Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.count, color = receiptsColors.fade, style = ReceiptsType.meta.copy(fontSize = 11.sp), maxLines = 1)
        Text(receiptMoney(row.amountMinor), color = receiptsColors.ink, style = ReceiptsType.amount, maxLines = 1)
    }
}

private val ColorCyanLabel: androidx.compose.ui.graphics.Color
    @Composable get() = if (receiptsColors.monochrome) receiptsColors.ink else androidx.compose.ui.graphics.Color(0xFF0E7C86)

private data class RecapCategory(val categoryId: String, val amountMinor: Long)
private data class RecapMerchant(val rank: Int, val name: String, val count: String, val amountMinor: Long)
private data class RecapData(
    val title: String,
    val spentMinor: Long,
    val obligationsMinor: Long,
    val compareArrow: String,
    val comparison: String,
    val categories: List<RecapCategory>,
    val categoryTotal: Long,
    val categoryMax: Long,
    val biggestAmount: String,
    val biggestDay: String,
    val quietCount: String,
    val quietUnder: String,
    val merchants: List<RecapMerchant>,
)

private fun buildRecap(transactions: List<Transaction>, budget: Budget): RecapData {
    val snapshot = dashboard(transactions, budget)
    val zone = ZoneId.systemDefault()
    val elapsedDays = snapshot.dayOfPeriod.coerceAtLeast(1)
    val previousRange = budgetRange(budget, snapshot.range.start.minusDays(1))
    val previousStart = previousRange.start
    val previousEndInclusive = previousStart.plusDays((elapsedDays - 1).toLong()).coerceAtMost(previousRange.endInclusive)
    val previousStartMillis = previousStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val previousEndMillis = previousEndInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val previousNet = transactions.filter { it.status in IncludedStatuses && it.occurredAt in previousStartMillis until previousEndMillis }
        .sumOf { it.netSpend(budget.countInvestmentsAsSpending) }
    val delta = snapshot.spentMinor - previousNet
    val categories = snapshot.categoryTotals.toList().filter { it.second != 0L }.sortedByDescending { abs(it.second) }.map { RecapCategory(it.first, it.second) }
    val categoryTotal = categories.filter { it.amountMinor > 0L }.sumOf { it.amountMinor }.coerceAtLeast(1L)
    val categoryMax = categories.filter { it.amountMinor > 0L }.maxOfOrNull { it.amountMinor } ?: 1L
    val startMillis = snapshot.range.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = snapshot.range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val merchants = transactions.filter {
        it.status in IncludedStatuses && it.countsAsSpend(budget.countInvestmentsAsSpending) && it.occurredAt in startMillis until endMillis
    }
        .groupBy { receiptMerchant(it) }
        .map { (merchant, rows) -> Triple(merchant, rows.size, rows.sumOf { it.amountMinor }) }
        .sortedByDescending { it.third }
        .mapIndexed { index, (merchant, count, amount) -> RecapMerchant(index + 1, merchant, "$count ${if (count == 1) "order" else "orders"}", amount) }
    val biggest = snapshot.dailyTotals.filterValues { it > 0L }.maxByOrNull { it.value }
    // Same rule as the Home tile: a quiet day is one with no spending at all.
    val quietCount = (0 until elapsedDays).count { offset ->
        val day = snapshot.range.start.plusDays(offset.toLong())
        (snapshot.dailyTotals[day] ?: 0L) <= 0L
    }
    val title = if (budget.period == "Week") "This week" else "${receiptMonth(snapshot.range.start)} so far"
    return RecapData(
        title = title,
        spentMinor = snapshot.spentMinor,
        obligationsMinor = snapshot.obligationsMinor,
        compareArrow = if (delta >= 0) "↑" else "↓",
        comparison = when {
            previousNet == 0L -> "No equal stretch yet"
            delta > 0 -> "${receiptMoney(delta)} more than before"
            delta < 0 -> "${receiptMoney(abs(delta))} less than before"
            else -> "Same as before"
        },
        categories = categories,
        categoryTotal = categoryTotal,
        categoryMax = categoryMax,
        biggestAmount = biggest?.let { receiptMoney(it.value) } ?: "₹0",
        biggestDay = biggest?.let { receiptDayDate(it.key) } ?: "None yet",
        quietCount = "$quietCount of $elapsedDays",
        quietUnder = "nothing spent",
        merchants = merchants,
    )
}

private val IncludedStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)
