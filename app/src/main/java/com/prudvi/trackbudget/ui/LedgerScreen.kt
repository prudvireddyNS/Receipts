package com.prudvi.trackbudget.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.category
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun LedgerScreen(
    transactions: List<Transaction>,
    onTransaction: (Transaction) -> Unit,
    onAdd: () -> Unit,
    onRecategorize: (Transaction, String) -> Unit,
    onLearnRule: (String, String, Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LedgerFilter.All) }
    val items = remember(transactions, query, filter) { ledgerItems(transactions, query, filter) }

    LazyColumn(
        modifier.fillMaxSize().background(receiptsColors.paper),
        contentPadding = PaddingValues(
            start = ReceiptsSpace.screen,
            end = ReceiptsSpace.screen,
            top = ReceiptsSpace.x4,
            bottom = ReceiptsSpace.x16,
        ),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        item("ledger-tools") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ledger", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                ReceiptIconButton(Icons.Default.Add, "New receipt", onAdd)
            }
            Spacer(Modifier.height(ReceiptsSpace.x3))
            ReceiptTextField(query, { query = it }, "Search receipts")
            Spacer(Modifier.height(ReceiptsSpace.x2))
            LedgerFilterRow(filter) { filter = it }
            ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x3))
        }
        if (items.isEmpty()) {
            item("ledger-empty") {
                val emptyTitle = if (query.isBlank() && filter == LedgerFilter.All) "Nothing yet." else "Nothing found."
                val emptyBody = if (query.isBlank() && filter == LedgerFilter.All) {
                    "Pay for something and this fills itself in."
                } else {
                    "Try another search or filter."
                }
                ReceiptEmptyState(emptyTitle, emptyBody)
            }
        } else {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is LedgerItem.Header -> LedgerDayHeader(item.date, item.debitTotalMinor)
                    is LedgerItem.Row -> LedgerTransactionRow(item.transaction, onTransaction, onRecategorize, onLearnRule)
                }
            }
        }
    }
}

@Composable
private fun LedgerFilterRow(selected: LedgerFilter, onSelected: (LedgerFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        LedgerFilter.entries.forEach { filter ->
            ReceiptPill(filter.label, selected = selected == filter, onClick = { onSelected(filter) })
        }
    }
}

@Composable
private fun LedgerDayHeader(date: LocalDate, debitTotalMinor: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ReceiptLabel(receiptDayDate(date), Modifier.weight(1f))
        if (debitTotalMinor > 0) Text(receiptMoney(debitTotalMinor), color = receiptsColors.fade, style = ReceiptsType.amount)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LedgerTransactionRow(
    transaction: Transaction,
    onTransaction: (Transaction) -> Unit,
    onRecategorize: (Transaction, String) -> Unit,
    onLearnRule: (String, String, Direction) -> Unit,
) {
    var revealed by rememberSaveable(transaction.id) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val thresholdPx = with(density) { (maxWidth * SwipeRevealFraction).toPx() }
        var offsetPx by remember(transaction.id) { mutableFloatStateOf(0f) }
        LaunchedEffect(revealed, thresholdPx) { offsetPx = if (revealed) -thresholdPx else 0f }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(ReceiptsRadius.small))
                .background(if (revealed) receiptsColors.paperRaised else receiptsColors.paper)
                .then(if (revealed) Modifier.border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small)) else Modifier)
                .padding(horizontal = if (revealed) ReceiptsSpace.x2 else ReceiptsSpace.none),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(transaction.id, thresholdPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                offsetPx = (offsetPx + dragAmount).coerceIn(-thresholdPx, 0f)
                            },
                            onDragEnd = { revealed = offsetPx <= -thresholdPx },
                            onDragCancel = { revealed = false },
                        )
                    }
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { if (revealed) revealed = false else onTransaction(transaction) },
                        onLongClick = { transaction.categoryId?.takeIf { canLearnMerchant(transaction.merchant) }?.let { onLearnRule(transaction.merchant, it, transaction.direction) } },
                    )
                    .semantics {
                        contentDescription = ledgerDescription(transaction)
                        customActions = buildList {
                            add(CustomAccessibilityAction("Change category") { revealed = true; true })
                            transaction.categoryId?.takeIf { canLearnMerchant(transaction.merchant) }?.let { categoryId ->
                                add(CustomAccessibilityAction("Always use ${categoryName(categoryId)} for ${receiptMerchant(transaction)}") {
                                    onLearnRule(transaction.merchant, categoryId, transaction.direction)
                                    true
                                })
                            }
                        }
                    }
                    .alpha(if (transaction.status == TransactionStatus.EXCLUDED) DisabledAlpha else 1f)
                    .padding(vertical = ReceiptsSpace.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
            ) {
                ReceiptCategoryMark(transaction.categoryId)
                Column(Modifier.weight(1f)) {
                    Text(
                        receiptMerchant(transaction),
                        color = receiptsColors.ink,
                        style = ReceiptsType.bodyStrong,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (transaction.status == TransactionStatus.EXCLUDED) TextDecoration.LineThrough else null,
                    )
                    Text(
                        "${categoryName(transaction.categoryId)} · ${receiptTime(transaction.occurredAt)}",
                        color = receiptsColors.fade,
                        style = ReceiptsType.meta,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    receiptSignedMoney(transaction),
                    color = if (transaction.direction == Direction.CREDIT) receiptsColors.ultramarine else receiptsColors.ink,
                    style = ReceiptsType.amount,
                    maxLines = 1,
                )
            }
            if (revealed) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = ReceiptsSpace.x2),
                    horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
                    verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
                ) {
                    recategorizeChoices(transaction).forEach { choice ->
                        val mark = categoryVisual(choice.id).mark
                        ReceiptPill(
                            "$mark ${choice.name}",
                            selected = transaction.categoryId == choice.id,
                            onClick = {
                                onRecategorize(transaction, choice.id)
                                revealed = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun ledgerItems(transactions: List<Transaction>, query: String, filter: LedgerFilter): List<LedgerItem> {
    val cleanQuery = query.trim()
    val visible = transactions.asSequence()
        .filter { it.status in LedgerStatuses }
        .filter { filter.matches(it) }
        .filter { transaction ->
            cleanQuery.isBlank() ||
                transaction.merchant.contains(cleanQuery, ignoreCase = true) ||
                transaction.note.contains(cleanQuery, ignoreCase = true) ||
                transaction.rawMessage.orEmpty().contains(cleanQuery, ignoreCase = true) ||
                receiptMoney(transaction.amountMinor).contains(cleanQuery)
        }
        .sortedByDescending { it.occurredAt }
        .toList()
    return visible.groupBy { receiptDate(it.occurredAt) }
        .toSortedMap(compareByDescending { it })
        .flatMap { (date, rows) ->
            val debitTotal = rows.filter {
                it.direction == Direction.DEBIT && it.status != TransactionStatus.EXCLUDED && category(it.categoryId)?.notSpending != true
            }.sumOf { it.amountMinor }
            listOf<LedgerItem>(LedgerItem.Header(date, debitTotal)) + rows.map { LedgerItem.Row(it) }
        }
}

private fun recategorizeChoices(transaction: Transaction): List<Category> = if (transaction.direction == Direction.CREDIT) {
    Categories.filter { it.id in CreditCategoryIds }
} else {
    Categories.filterNot { it.notSpending }
}

private fun ledgerDescription(transaction: Transaction): String = buildString {
    append(receiptMerchant(transaction))
    append(", ")
    append(categoryName(transaction.categoryId))
    append(", ")
    append(receiptSignedMoney(transaction))
    append(", ")
    append(receiptTime(transaction.occurredAt))
}

private enum class LedgerFilter(val label: String) {
    All("All"),
    Food("Food"),
    Shop("Shop"),
    In("In");

    fun matches(transaction: Transaction): Boolean = when (this) {
        All -> true
        Food -> transaction.categoryId == "food"
        Shop -> transaction.categoryId == "shopping"
        In -> transaction.direction == Direction.CREDIT
    }
}

private sealed interface LedgerItem {
    val key: String

    data class Header(val date: LocalDate, val debitTotalMinor: Long) : LedgerItem {
        override val key: String = "day-${date.toEpochDay()}"
    }

    data class Row(val transaction: Transaction) : LedgerItem {
        override val key: String = "receipt-${transaction.id}"
    }
}

private val LedgerStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.EXCLUDED)
private val CreditCategoryIds = setOf("income", "refund", "repayments", "transfers")
private const val SwipeRevealFraction = 0.32f
private const val DisabledAlpha = 0.48f
