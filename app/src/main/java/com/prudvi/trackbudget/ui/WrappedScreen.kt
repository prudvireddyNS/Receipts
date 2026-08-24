package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.BudgetRange
import com.prudvi.trackbudget.model.EarnedStamp
import com.prudvi.trackbudget.model.PeriodSnapshot
import com.prudvi.trackbudget.model.SpendingAnalytics
import com.prudvi.trackbudget.model.StampEngine
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.spendingAnalytics
import java.time.YearMonth
import java.time.ZoneId

private val WrappedExportSize = ShareImageSize(1080, 1920)

@Composable
fun WrappedScreen(
    transactions: List<Transaction>,
    budget: Budget,
    earnedStamps: List<EarnedStamp>,
    periodSnapshots: List<PeriodSnapshot>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShared: () -> Unit = {},
    periodKey: String,
) {
    val target = periodSnapshots.firstOrNull { it.periodKey == periodKey } ?: return
    val range = remember(periodKey) { wrappedRange(periodKey) }
    val zone = ZoneId.systemDefault()
    val closedAt = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val wrappedBudget = budget.copy(
        amountMinor = target.budgetMinor,
        period = "Custom",
        startEpochDay = range.start.toEpochDay(),
        endEpochDay = range.endInclusive.toEpochDay(),
    )
    val analytics = remember(transactions, wrappedBudget, closedAt) { spendingAnalytics(transactions, wrappedBudget, closedAt) }
    val priorKey = range.start.minusMonths(1).let { "%04d-%02d".format(it.year, it.monthValue) }
    val prior = periodSnapshots.firstOrNull { it.periodKey == priorKey }
    val periodStamps = earnedStamps.filter { it.periodKey == periodKey || receiptDate(it.earnedAt) in range.start..range.endInclusive }
    val cards = remember(analytics, target, prior, periodStamps) { wrappedCards(analytics, target, prior, periodStamps) }
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val share1 = rememberShareCaptureState("wrapped-$periodKey-1", WrappedExportSize, onShared)
    val share2 = rememberShareCaptureState("wrapped-$periodKey-2", WrappedExportSize, onShared)
    val share3 = rememberShareCaptureState("wrapped-$periodKey-3", WrappedExportSize, onShared)
    val share4 = rememberShareCaptureState("wrapped-$periodKey-4", WrappedExportSize, onShared)
    val share5 = rememberShareCaptureState("wrapped-$periodKey-5", WrappedExportSize, onShared)
    val shareStates = listOf(share1, share2, share3, share4, share5)

    Box(modifier.fillMaxSize().background(receiptsColors.ink)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0, reverseLayout = true) { page ->
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val cardModifier = if (maxWidth / maxHeight > 9f / 16f) {
                    Modifier.fillMaxHeight().aspectRatio(9f / 16f)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                }
                WrappedStoryCard(cards[page], page, cards.size, cardModifier.captureForShare(shareStates[page]))
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(ReceiptsSpace.screen),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoryControl("Close", "Close Wrapped", onClose)
            StoryControl("Share", "Share current Wrapped card", shareStates[pagerState.currentPage]::share)
        }
    }
}

@Composable
private fun WrappedStoryCard(card: WrappedCard, page: Int, count: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(receiptsColors.ink).padding(horizontal = ReceiptsSpace.x8, vertical = ReceiptsSpace.x12)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReceiptLabel(card.label, color = receiptsColors.chrome)
            Spacer(Modifier.weight(1f))
            Text("${page + 1}/$count", color = receiptsColors.chrome, style = ReceiptsType.stamp)
        }
        Spacer(Modifier.size(ReceiptsSpace.x12))
        Text(card.headline, color = receiptsColors.chrome, style = ReceiptsType.hero, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(ReceiptsSpace.x6))
        Text(card.body, color = receiptsColors.chromeTint, style = ReceiptsType.title)
        Spacer(Modifier.weight(1f))
        card.rows.forEach { row -> WrappedMetricRow(row) }
        Spacer(Modifier.fillMaxWidth().padding(vertical = ReceiptsSpace.x4).receiptPerforation(receiptsColors.chromeTint))
        Text("RECEIPTS · WRAPPED", Modifier.fillMaxWidth(), color = receiptsColors.chrome, style = ReceiptsType.stamp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WrappedMetricRow(row: WrappedRow) {
    Row(
        Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x3),
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ReceiptsSpace.x3).clip(RoundedCornerShape(ReceiptsRadius.pill)).background(receiptsColors.chrome))
        Text(row.label, Modifier.weight(1f), color = receiptsColors.chromeTint, style = ReceiptsType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.value, color = receiptsColors.chrome, style = ReceiptsType.amount, maxLines = 1)
    }
}

@Composable
private fun StoryControl(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = ReceiptsSpace.x12, minHeight = ReceiptsSpace.x12)
            .border(1.dp, receiptsColors.chrome, RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x4, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = receiptsColors.chrome, style = ReceiptsType.label) }
}

private data class WrappedCard(val label: String, val headline: String, val body: String, val rows: List<WrappedRow>)
private data class WrappedRow(val label: String, val value: String)

private fun wrappedCards(
    analytics: SpendingAnalytics,
    target: PeriodSnapshot,
    prior: PeriodSnapshot?,
    earnedStamps: List<EarnedStamp>,
): List<WrappedCard> {
    val categories = analytics.snapshot.categoryTotals.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val topCategory = categories.firstOrNull()
    val topMerchant = analytics.topMerchants.firstOrNull()
    val delta = prior?.let { target.spentMinor - it.spentMinor }
    val stampTitles = earnedStamps.takeLast(3).map { stamp -> StampEngine.definitions.firstOrNull { it.id == stamp.id }?.title ?: stamp.id.uppercase() }
    return listOf(
        WrappedCard(
            "TOTAL",
            receiptMoney(target.spentMinor),
            "spent in ${wrappedMonth(target.periodKey)}.",
            listOf(
                WrappedRow("Budget", if (target.budgetMinor > 0) receiptMoney(target.budgetMinor) else "Not recorded"),
                WrappedRow("Left", if (target.budgetMinor > 0) receiptMoney((target.budgetMinor - target.spentMinor).coerceAtLeast(0)) else "Not recorded"),
                WrappedRow("Refunds", receiptMoney(analytics.snapshot.refundedMinor)),
            ),
        ),
        WrappedCard(
            "WHERE IT WENT",
            topCategory?.let { category(it.key)?.name ?: categoryName(it.key) } ?: "No category yet",
            topCategory?.let { "Your biggest category took ${receiptMoney(it.value)}." } ?: "Categories appear after receipts are sorted.",
            categories.take(4).map { WrappedRow(categoryName(it.key), receiptMoney(it.value)) },
        ),
        WrappedCard(
            "STANDOUT",
            topMerchant?.merchant ?: "Still warming up",
            topMerchant?.let { "Your most visible merchant took ${receiptMoney(it.amountMinor)}." } ?: "One standout appears after a pattern forms.",
            analytics.topMerchants.take(4).map { WrappedRow(it.merchant, receiptMoney(it.amountMinor)) },
        ),
        WrappedCard(
            "STAMPS EARNED",
            earnedStamps.size.toString(),
            if (earnedStamps.size == 1) "stamp earned this month." else "stamps earned this month.",
            if (stampTitles.isEmpty()) listOf(WrappedRow("Shelf", "No new stamps")) else stampTitles.map { WrappedRow(it, "earned") },
        ),
        WrappedCard(
            "VERSUS LAST MONTH",
            delta?.let { (if (it >= 0) "+" else "−") + receiptMoney(kotlin.math.abs(it)) } ?: "No prior month",
            delta?.let { if (it <= 0) "Lower than last month." else "Higher than last month." } ?: "Comparison unlocks after another closed month.",
            listOf(WrappedRow("This month", receiptMoney(target.spentMinor)), WrappedRow("Last month", prior?.let { receiptMoney(it.spentMinor) } ?: "waiting")),
        ),
    )
}

private fun wrappedRange(periodKey: String): BudgetRange {
    val month = runCatching { YearMonth.parse(periodKey) }.getOrElse { YearMonth.now().minusMonths(1) }
    val start = month.atDay(1)
    return BudgetRange(start, month.atEndOfMonth())
}

private fun wrappedMonth(periodKey: String): String = runCatching {
    YearMonth.parse(periodKey).month.name.lowercase().replaceFirstChar(Char::titlecase)
}.getOrDefault("Last month")
