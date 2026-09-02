package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorOverlay(
    transaction: Transaction,
    onClose: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable(transaction.id) { mutableStateOf(formatMinorForInput(transaction.amountMinor)) }
    var direction by rememberSaveable(transaction.id) { mutableStateOf(transaction.direction) }
    var merchant by rememberSaveable(transaction.id) { mutableStateOf(transaction.merchant) }
    var categoryId by rememberSaveable(transaction.id) { mutableStateOf(transaction.categoryId ?: if (transaction.direction == Direction.CREDIT) "income" else "misc") }
    var occurredAt by rememberSaveable(transaction.id) { mutableStateOf(transaction.occurredAt) }
    var committed by rememberSaveable(transaction.id) { mutableStateOf(transaction.committed) }
    var showAllCategories by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var expandedSms by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(transaction.id) { mutableStateOf(false) }
    val amountMinor = decimalToMinor(amount)
    val zone = ZoneId.systemDefault()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(occurredAt), zone)

    fun save(status: TransactionStatus = transaction.status) {
        if (amountMinor <= 0L && status != TransactionStatus.EXCLUDED) return
        onSave(
            transaction.copy(
                amountMinor = amountMinor,
                direction = direction,
                merchant = merchant.trim().ifBlank { if (direction == Direction.CREDIT) "Money received" else "Manual expense" },
                categoryId = categoryId,
                occurredAt = occurredAt,
                status = status,
                committed = direction == Direction.DEBIT && committed,
            ),
        )
    }

    Box(modifier.fillMaxSize().background(receiptsColors.paper)) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ReceiptsSpace.screen,
                end = ReceiptsSpace.screen,
                top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + ReceiptsSpace.x4,
                bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + ReceiptsSpace.x8,
            ),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Edit receipt", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                    ReceiptButton("Close", onClose, style = ReceiptButtonStyle.QUIET)
                }
                ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x3))
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Amount")
                        ReceiptTextField(
                            amount,
                            { amount = sanitizeDecimal(it) },
                            "Amount",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.semantics { contentDescription = "Receipt amount" },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                            Choice("Spent", direction == Direction.DEBIT, Modifier.weight(1f)) {
                                direction = Direction.DEBIT
                                if (categoryId in CreditCategoryIds) categoryId = "misc"
                            }
                            Choice("Money in", direction == Direction.CREDIT, Modifier.weight(1f)) {
                                direction = Direction.CREDIT
                                if (categoryId !in CreditCategoryIds) categoryId = "income"
                            }
                        }
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel(if (direction == Direction.DEBIT) "To" else "From")
                        ReceiptTextField(merchant, { merchant = it.take(80) }, if (direction == Direction.DEBIT) "Merchant or payee" else "Sender or source")
                        ReceiptLabel("Category")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                            categoryOptions(direction, showAllCategories).forEach { category ->
                                Choice(category.name, category.id == categoryId) { categoryId = category.id }
                            }
                            if (!showAllCategories) Choice("More", false) { showAllCategories = true }
                            else Choice("Less", false) { showAllCategories = false }
                        }
                        if (direction == Direction.DEBIT) {
                            Row(Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x1), verticalAlignment = Alignment.CenterVertically) {
                                Text("Skip in daily total", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 12.sp))
                                ReceiptCheckbox(committed) { committed = it }
                            }
                        }
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Date & time")
                        StepperRow("Date", dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) {
                            occurredAt = dateTime.plusDays(it.toLong()).atZone(zone).toInstant().toEpochMilli()
                        }
                        StepperRow("Time", dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))) {
                            occurredAt = dateTime.plusMinutes(it * 15L).atZone(zone).toInstant().toEpochMilli()
                        }
                    }
                }
            }
            transaction.rawMessage?.let { raw ->
                item {
                    ReceiptCard {
                        Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ReceiptLabel("Raw SMS", Modifier.weight(1f))
                                ReceiptPill(if (expandedSms) "Less" else "More", onClick = { expandedSms = !expandedSms })
                            }
                            Text(
                                raw,
                                color = receiptsColors.inkSoft,
                                style = ReceiptsType.meta.copy(fontFamily = ReceiptsFonts.splineSansMono),
                                maxLines = if (expandedSms) 30 else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                    val excluded = transaction.status == TransactionStatus.EXCLUDED
                    ReceiptButton(
                        if (excluded) "Include" else "Exclude",
                        onClick = { save(if (excluded) TransactionStatus.CONFIRMED else TransactionStatus.EXCLUDED) },
                        modifier = Modifier.weight(1f),
                        style = ReceiptButtonStyle.OUTLINE,
                    )
                    ReceiptButton("Delete", { confirmDelete = true }, Modifier.weight(1f), style = ReceiptButtonStyle.CHILLI)
                }
                ReceiptButton("Save", { save(TransactionStatus.CONFIRMED) }, Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x2), enabled = amountMinor > 0L)
            }
        }
        if (confirmDelete) ConfirmDelete(onCancel = { confirmDelete = false }, onDelete = { onDelete(transaction.id) })
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.body)
        StepButton("−", "Move $label earlier") { onStep(-1) }
        Text(value, Modifier.padding(horizontal = ReceiptsSpace.x3), color = receiptsColors.ink, style = ReceiptsType.amount)
        StepButton("+", "Move $label later") { onStep(1) }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = ReceiptsSpace.x12, minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paper)
            .border(1.dp, receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = receiptsColors.ink, style = ReceiptsType.heading) }
}

@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(if (selected) receiptsColors.ink else receiptsColors.paper)
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = text }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) receiptsColors.paper else receiptsColors.ink, style = ReceiptsType.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ConfirmDelete(onCancel: () -> Unit, onDelete: () -> Unit) {
    Box(Modifier.fillMaxSize().background(receiptsColors.scrim).padding(ReceiptsSpace.screen), contentAlignment = Alignment.Center) {
        ReceiptCard {
            Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4)) {
                Text("Delete receipt?", color = receiptsColors.ink, style = ReceiptsType.heading)
                Text("This removes the transaction from this device.", color = receiptsColors.inkSoft, style = ReceiptsType.body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                    ReceiptButton("Cancel", onCancel, Modifier.weight(1f), style = ReceiptButtonStyle.QUIET)
                    ReceiptButton("Delete", onDelete, Modifier.weight(1f), style = ReceiptButtonStyle.CHILLI)
                }
            }
        }
    }
}

private fun categoryOptions(direction: Direction, showAll: Boolean) = when {
    direction == Direction.CREDIT -> Categories.filter { it.id in CreditCategoryIds }.sortedBy { it.name }
    showAll -> Categories.filter { it.id !in CreditOnlyCategoryIds }.sortedBy { it.name }
    else -> {
        val spend = Categories.filter { it.id !in CreditOnlyCategoryIds }
        listOf("food", "groceries", "transport", "shopping").mapNotNull { id -> spend.firstOrNull { it.id == id } }
    }
}

private fun formatMinorForInput(amountMinor: Long): String = if (amountMinor % 100 == 0L) (amountMinor / 100).toString() else "${amountMinor / 100}.${(amountMinor % 100).toString().padStart(2, '0')}"

private fun sanitizeDecimal(value: String): String {
    val clean = value.filter { it.isDigit() || it == '.' }
    val before = clean.substringBefore('.').take(8)
    val after = clean.substringAfter('.', missingDelimiterValue = "").take(2)
    return if ('.' in clean) "$before.$after" else before
}

private val CreditCategoryIds = setOf("income", "refund", "transfers")
private val CreditOnlyCategoryIds = setOf("income", "refund", "repayments")
