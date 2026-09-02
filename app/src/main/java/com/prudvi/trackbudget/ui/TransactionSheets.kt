package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Categories
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
fun TransactionDetailEditor(transaction: Transaction, onClose: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit, modifier: Modifier = Modifier) {
    var amount by rememberSaveable(transaction.id) { mutableStateOf(minorToEditable(transaction.amountMinor)) }
    var merchant by rememberSaveable(transaction.id) { mutableStateOf(transaction.merchant) }
    var note by rememberSaveable(transaction.id) { mutableStateOf(transaction.note) }
    var selectedCategory by rememberSaveable(transaction.id) { mutableStateOf(transaction.categoryId) }
    var excluded by rememberSaveable(transaction.id) { mutableStateOf(transaction.status == TransactionStatus.EXCLUDED) }
    var confirmDelete by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val canSave = decimalToMinor(amount) > 0 && (excluded || selectedCategory != null)

    TransactionOverlayFrame("Transaction", onClose, modifier, closeContentDescription = "Close transaction details") {
        MoneyEditor(amount, { amount = it }, "Transaction amount")
        Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
            ReceiptLabel("Who got it")
            ReceiptTextField(merchant, { merchant = it.take(80) }, "Merchant or payee")
            Text(receiptMeta(transaction), color = receiptsColors.fade, style = ReceiptsType.meta)
        }
        ReceiptLabel("Category")
        CategoryChooser(editableCategories(transaction.direction), selectedCategory) { selectedCategory = it }
        ReceiptTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Add a note",
            singleLine = false,
            modifier = Modifier.semantics { contentDescription = "Transaction note" },
        )
        ReceiptCheckRow("Excluded from spending", excluded, "Toggle transaction exclusion") { excluded = !excluded }
        transaction.rawMessage?.let { message ->
            Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                ReceiptLabel("Source message")
                ReceiptCard { Text(message, color = receiptsColors.inkSoft, style = ReceiptsType.meta.copy(fontFamily = ReceiptsFonts.splineSansMono)) }
            }
        }
        Spacer(Modifier.height(ReceiptsSpace.x2))
        ReceiptButton(
            "Save changes",
            onClick = {
                onSave(transaction.copy(amountMinor = decimalToMinor(amount), merchant = merchant.ifBlank { transaction.merchant }, note = note, categoryId = selectedCategory, status = if (excluded) TransactionStatus.EXCLUDED else TransactionStatus.CONFIRMED))
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
            style = ReceiptButtonStyle.ULTRAMARINE,
        )
        if (confirmDelete) DeleteConfirmCard({ confirmDelete = false }) { onDelete(transaction.id); onClose() }
        else ReceiptButton("Delete transaction", { confirmDelete = true }, Modifier.fillMaxWidth(), style = ReceiptButtonStyle.QUIET)
        Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

@Composable
fun TransactionDetailSheet(transaction: Transaction, onClose: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit, modifier: Modifier = Modifier) =
    TransactionDetailEditor(transaction, onClose, onSave, onDelete, modifier)

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

@Composable
private fun MoneyEditor(value: String, onValueChange: (String) -> Unit, description: String) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12).clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paperRaised).border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2).semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
    ) {
        Text("₹", color = receiptsColors.fade, style = ReceiptsType.display)
        BasicTextField(
            value,
            { onValueChange(cleanMoneyInput(it)) },
            Modifier.weight(1f),
            singleLine = true,
            textStyle = ReceiptsType.display.copy(color = receiptsColors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(receiptsColors.ultramarine),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isBlank()) Text("0", color = receiptsColors.fade, style = ReceiptsType.display); inner() } },
        )
    }
}

@Composable
private fun CategoryChooser(categories: List<Category>, selectedCategory: String?, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        categories.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                row.forEach { item -> CategoryChoice(item, selectedCategory == item.id, Modifier.weight(1f)) { onSelected(item.id) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryChoice(item: Category, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = receiptsColors
    Row(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12).clip(RoundedCornerShape(ReceiptsRadius.pill)).background(if (selected) colors.ink else colors.paper)
            .border(1.dp, if (selected) colors.ink else colors.rule, RoundedCornerShape(ReceiptsRadius.pill)).clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = if (selected) "${item.name} category selected" else "Select ${item.name} category" }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
    ) {
        ReceiptCategoryMark(item.id)
        Text(item.name, color = if (selected) colors.paper else colors.ink, style = ReceiptsType.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReceiptCheckRow(label: String, checked: Boolean, description: String, onClick: () -> Unit) {
    val colors = receiptsColors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12).clip(RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { role = Role.Checkbox; contentDescription = description; stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        Box(
            Modifier.size(ReceiptsSpace.x4).clip(RoundedCornerShape(ReceiptsRadius.small)).background(if (checked) colors.ink else colors.paper)
                .border(1.dp, if (checked) colors.ink else colors.ruleHard, RoundedCornerShape(ReceiptsRadius.small)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Box(Modifier.fillMaxSize().padding(ReceiptsSpace.x1).background(colors.paper)) }
        Text(label, color = colors.ink, style = ReceiptsType.body)
    }
}

@Composable
private fun DeleteConfirmCard(onCancel: () -> Unit, onDelete: () -> Unit) {
    ReceiptCard {
        Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
            Text("Delete this transaction", color = receiptsColors.ink, style = ReceiptsType.heading)
            Text("This removes it from your local ledger.", color = receiptsColors.inkSoft, style = ReceiptsType.body)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                ReceiptButton("Cancel", onCancel, Modifier.weight(1f), style = ReceiptButtonStyle.OUTLINE)
                ReceiptButton("Delete", onDelete, Modifier.weight(1f), style = ReceiptButtonStyle.CHILLI)
            }
        }
    }
}

private fun editableCategories(direction: Direction): List<Category> = if (direction == Direction.DEBIT) {
    Categories.filter { it.id !in setOf("income", "refund", "repayments") }
} else {
    Categories.filter { it.id in setOf("income", "refund", "transfers") }
}

private val CategoryDetailStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)

private fun minorToEditable(minor: Long): String {
    val rupees = minor / 100
    val paise = (minor % 100).toString().padStart(2, '0')
    return if (paise == "00") rupees.toString() else "$rupees.${paise.trimEnd('0')}"
}

private fun cleanMoneyInput(raw: String): String {
    val out = StringBuilder(); var dot = false; var decimals = 0
    raw.forEach { char ->
        when {
            char.isDigit() && (!dot || decimals < 2) -> { out.append(char); if (dot) decimals++ }
            char == '.' && !dot -> { if (out.isEmpty()) out.append('0'); out.append(char); dot = true }
        }
    }
    return out.toString().take(12)
}
