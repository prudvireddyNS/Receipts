package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.CategoryLimitLevel
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.categoryLimitStatuses
import com.prudvi.trackbudget.model.dashboard
import java.time.Instant
import java.time.ZoneId

@Composable
fun TransactionOverlayFrame(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    scroll: Boolean = true,
    closeContentDescription: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = receiptsColors
    Column(modifier.fillMaxSize().background(colors.paper).padding(WindowInsets.safeDrawing.asPaddingValues()).imePadding()) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
                .padding(start = ReceiptsSpace.screen, end = ReceiptsSpace.x1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
        ) {
            Text(title, Modifier.weight(1f), color = colors.ink, style = ReceiptsType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) Text(trailing, color = colors.fade, style = ReceiptsType.meta, maxLines = 1)
            ReceiptIconButton(Icons.Default.Close, closeContentDescription, onClose)
        }
        ReceiptPerforation()
        val body = Modifier.fillMaxSize().padding(horizontal = ReceiptsSpace.screen, vertical = ReceiptsSpace.x4)
        if (scroll) Column(body.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4), content = content)
        else Column(body, verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4), content = content)
    }
}

@Composable
fun CategoryDetailScreen(
    categoryId: String,
    transactions: List<Transaction>,
    budget: Budget,
    onClose: () -> Unit,
    onTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = budgetRange(budget)
    val zone = ZoneId.systemDefault()
    val items = remember(categoryId, transactions, budget) {
        val categoryDebitIds = transactions.filter { it.direction == Direction.DEBIT && it.categoryId == categoryId }.mapTo(mutableSetOf()) { it.id }
        transactions.filter { item ->
            val date = Instant.ofEpochMilli(item.occurredAt).atZone(zone).toLocalDate()
            val belongsToCategory = item.categoryId == categoryId || item.direction == Direction.CREDIT && item.refundOfId in categoryDebitIds
            belongsToCategory && item.status in CategoryDetailStatuses && date >= range.start && date <= range.endInclusive
        }
    }
    val limitStatus = remember(transactions, budget, categoryId) { categoryLimitStatuses(transactions, budget).firstOrNull { it.categoryId == categoryId } }
    val total = remember(transactions, budget, categoryId) { dashboard(transactions, budget).categoryTotals[categoryId] ?: 0L }

    TransactionOverlayFrame(
        title = category(categoryId)?.name ?: categoryName(categoryId),
        onClose = onClose,
        modifier = modifier,
        trailing = "${receiptShortDate(range.start)} – ${receiptShortDate(range.endInclusive)}",
        closeContentDescription = "Close category details",
    ) {
        ReceiptCard {
            Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                ReceiptLabel("Total")
                Text(
                    if (total < 0L) "−${receiptMoney(kotlin.math.abs(total))}" else receiptMoney(total),
                    color = if (total < 0L) receiptsColors.ultramarine else receiptsColors.ink,
                    style = ReceiptsType.display,
                )
                if (limitStatus != null) CategoryLimitMeter(limitStatus.level, limitStatus.spentMinor, limitStatus.limitMinor, limitStatus.remainingMinor, limitStatus.fraction)
            }
        }
        ReceiptLabel("Transactions")
        if (items.isEmpty()) ReceiptEmptyState("No transactions", "This category has no spending in the selected period.")
        else items.forEach { item ->
            ReceiptRow(
                transaction = item,
                modifier = Modifier.semantics { contentDescription = "Open transaction from ${item.merchant}" },
                onClick = { onTransaction(item) },
            )
            ReceiptDivider()
        }
    }
}

@Composable
fun CategoryDetailSheet(categoryId: String, transactions: List<Transaction>, budget: Budget, onClose: () -> Unit, onTransaction: (Transaction) -> Unit, modifier: Modifier = Modifier) =
    CategoryDetailScreen(categoryId, transactions, budget, onClose, onTransaction, modifier)

@Composable
private fun CategoryLimitMeter(level: CategoryLimitLevel, spent: Long, limit: Long, remaining: Long, fraction: Float) {
    val colors = receiptsColors
    val fill = when (level) {
        CategoryLimitLevel.EXCEEDED -> colors.chilli
        CategoryLimitLevel.WARNING -> colors.chrome
        CategoryLimitLevel.NORMAL -> colors.ultramarine
    }
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        Box(
            Modifier.fillMaxWidth().height(ReceiptsSpace.x2).clip(RoundedCornerShape(ReceiptsRadius.pill)).background(colors.sunk)
                .semantics {
                    contentDescription = "Category limit progress"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                },
        ) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(fill)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
            Text("${receiptMoney(spent)} of ${receiptMoney(limit)}", Modifier.weight(1f), color = colors.fade, style = ReceiptsType.meta)
            Text(if (remaining >= 0) "${receiptMoney(remaining)} left" else "${receiptMoney(-remaining)} over", color = if (level == CategoryLimitLevel.EXCEEDED) colors.chilli else colors.fade, style = ReceiptsType.meta)
        }
    }
}

private val CategoryDetailStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)
