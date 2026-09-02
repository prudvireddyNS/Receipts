package com.prudvi.trackbudget.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import java.time.LocalDate

@Composable
fun LedgerScreen(
    transactions: List<Transaction>,
    budget: Budget,
    onTransaction: (Transaction) -> Unit,
    onRecategorize: (Transaction, String) -> Unit,
    onLearnRule: (String, String, Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterId by rememberSaveable { mutableStateOf<String?>(null) }
    val availableCategories = remember(transactions) {
        transactions.asSequence()
            .filter { it.status in LedgerStatuses && it.direction == Direction.DEBIT && it.categoryId != null }
            .groupingBy { it.categoryId!! }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }
    val hasExcluded = remember(transactions) { transactions.any { it.status == TransactionStatus.EXCLUDED } }
    val items = remember(transactions, budget, query, filterId) { ledgerItems(transactions, budget, query, filterId) }
    val listAppearedAt = remember(query, filterId) { android.os.SystemClock.uptimeMillis() }

    Column(modifier.fillMaxSize().background(receiptsColors.paper)) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp)) {
            Text("History", color = receiptsColors.ink, style = ReceiptsType.title)
            ReceiptTextField(query, { query = it }, "Search receipts", Modifier.padding(top = 13.dp))
            LedgerFilterRow(availableCategories, hasExcluded, filterId, { filterId = it }, Modifier.padding(top = 9.dp))
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
        if (items.isEmpty()) {
            item("empty") {
                Spacer(Modifier.height(16.dp))
                ReceiptEmptyState(
                    if (query.isBlank() && filterId == null) "Nothing yet." else "Nothing found.",
                    if (query.isBlank() && filterId == null) "Pay for something and this fills itself in." else "Try another search or filter.",
                )
            }
        } else {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                // Only the first screenful cascades in. Rows composed later (i.e. scrolled to)
                // appear immediately — an entrance delay there just reads as slow loading.
                val fresh = remember { android.os.SystemClock.uptimeMillis() - listAppearedAt < ReceiptsMotion.ENTER }
                val enter = if (fresh) {
                    Modifier.animateItem().receiptEnter(index.coerceAtMost(8), key = item.key, lift = 18.dp)
                } else {
                    Modifier.animateItem()
                }
                when (item) {
                    is LedgerItem.Header -> LedgerDayHeader(item.date, item.debitTotalMinor, Modifier.padding(top = 16.dp).then(enter))
                    is LedgerItem.Row -> LedgerTransactionRow(item.transaction, onTransaction, onRecategorize, onLearnRule, Modifier.padding(top = 6.dp).then(enter))
                }
            }
            item("foot") {
                val rowCount = items.count { it is LedgerItem.Row }
                Text(
                    if (filterId == ExcludedFilterId) "$rowCount excluded · not counted anywhere".uppercase() else "$rowCount receipts stored".uppercase(),
                    color = receiptsColors.fade,
                    style = ReceiptsType.label.copy(letterSpacing = 1.2.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        }
    }
}

@Composable
private fun LedgerFilterRow(
    categories: List<String>,
    hasExcluded: Boolean,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ReceiptPill("All", selected = selected == null, onClick = { onSelected(null) })
        categories.forEach { categoryId ->
            ReceiptPill(categoryName(categoryId), selected = selected == categoryId, onClick = { onSelected(categoryId) })
        }
        ReceiptPill("Money in", selected = selected == MoneyInFilterId, onClick = { onSelected(MoneyInFilterId) })
        // Excluded receipts are always in the list, but they're easy to lose among everything else —
        // this is the only way to pull the ignored pile up on its own and un-ignore something.
        if (hasExcluded) ReceiptPill("Excluded", selected = selected == ExcludedFilterId, onClick = { onSelected(ExcludedFilterId) })
    }
}

@Composable
private fun LedgerDayHeader(date: LocalDate, netTotalMinor: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(receiptDayDate(date).uppercase(), Modifier.weight(1f), color = receiptsColors.fade, style = ReceiptsType.label.copy(letterSpacing = 1.sp))
        if (netTotalMinor != 0L) {
            Text(
                if (netTotalMinor < 0L) "−${receiptMoney(kotlin.math.abs(netTotalMinor))}" else receiptMoney(netTotalMinor),
                color = receiptsColors.ink,
                style = ReceiptsType.amount,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerTransactionRow(
    transaction: Transaction,
    onTransaction: (Transaction) -> Unit,
    onRecategorize: (Transaction, String) -> Unit,
    onLearnRule: (String, String, Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.medium))
            .background(if (transaction.direction == Direction.CREDIT) receiptsColors.ultramarineTint else receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
            .combinedClickable(
                role = Role.Button,
                onClick = { onTransaction(transaction) },
                onLongClick = {
                    transaction.categoryId
                        ?.takeIf { transaction.direction == Direction.DEBIT && canLearnMerchant(transaction.merchant) }
                        ?.let { onLearnRule(transaction.merchant, it, Direction.DEBIT) }
                },
            )
            .semantics {
                contentDescription = ledgerDescription(transaction)
                customActions = buildList {
                    transaction.categoryId?.takeIf { transaction.direction == Direction.DEBIT }?.let { categoryId ->
                        add(CustomAccessibilityAction("Keep ${categoryName(categoryId)} for ${receiptMerchant(transaction)}") {
                            onRecategorize(transaction, categoryId)
                            true
                        })
                    }
                    transaction.categoryId?.takeIf { transaction.direction == Direction.DEBIT && canLearnMerchant(transaction.merchant) }?.let { categoryId ->
                        add(CustomAccessibilityAction("Always use ${categoryName(categoryId)} for ${receiptMerchant(transaction)}") {
                            onLearnRule(transaction.merchant, categoryId, Direction.DEBIT)
                            true
                        })
                    }
                }
            }
            .alpha(if (transaction.status == TransactionStatus.EXCLUDED) DisabledAlpha else 1f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReceiptCategoryMark(transaction.categoryId, size = 26.dp, radius = 8.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    receiptMerchant(transaction),
                    color = receiptsColors.ink,
                    style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (transaction.status == TransactionStatus.EXCLUDED) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (transaction.committed) ReceiptSkipTag()
            }
            val state = when (transaction.status) {
                TransactionStatus.EXCLUDED -> " · Excluded"
                TransactionStatus.CATEGORY_REVIEW, TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE -> " · Needs review"
                else -> ""
            }
            Text(
                "${categoryName(transaction.categoryId)} · ${receiptTime(transaction.occurredAt)}$state",
                color = receiptsColors.fade,
                style = ReceiptsType.meta.copy(fontSize = 10.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            receiptSignedMoney(transaction),
            color = if (transaction.direction == Direction.CREDIT) receiptsColors.purple else receiptsColors.ink,
            style = ReceiptsType.amount,
            maxLines = 1,
        )
    }
}

private fun ledgerItems(transactions: List<Transaction>, budget: Budget, query: String, filterId: String?): List<LedgerItem> {
    val cleanQuery = query.trim()
    val visible = transactions.asSequence()
        .filter { it.status in LedgerStatuses }
        .filter { matchesFilter(it, filterId) }
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
            // Excluded receipts never count towards a day's total — except when they're the only
            // thing on screen, where a column of blank headers would just look broken.
            val counted = if (filterId == ExcludedFilterId) rows else rows.filter { it.status in IncludedTotalStatuses }
            val netTotal = counted.sumOf {
                when {
                    it.direction == Direction.CREDIT -> -it.amountMinor
                    it.categoryId == "investment" && !budget.countInvestmentsAsSpending -> 0L
                    else -> it.amountMinor
                }
            }
            listOf<LedgerItem>(LedgerItem.Header(date, netTotal)) + rows.map { LedgerItem.Row(it) }
        }
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

private const val MoneyInFilterId = "__money_in__"
private const val ExcludedFilterId = "__excluded__"

private fun matchesFilter(transaction: Transaction, filterId: String?): Boolean = when (filterId) {
    null -> true
    MoneyInFilterId -> transaction.direction == Direction.CREDIT
    ExcludedFilterId -> transaction.status == TransactionStatus.EXCLUDED
    else -> transaction.categoryId == filterId
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

private val LedgerStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW, TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE, TransactionStatus.EXCLUDED)
private val IncludedTotalStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)
private const val DisabledAlpha = 0.48f
