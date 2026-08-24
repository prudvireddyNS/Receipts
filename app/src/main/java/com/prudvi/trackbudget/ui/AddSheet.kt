package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSheet(
    onClose: () -> Unit,
    onSave: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(Direction.DEBIT) }
    var selectedCategory by rememberSaveable { mutableStateOf("food") }
    var showWho by rememberSaveable { mutableStateOf(false) }
    var whoGotIt by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val today = rememberCurrentDate()
    val amountMinor = integerRupeesToMinor(amount)

    fun setDirection(next: Direction) {
        direction = next
        selectedCategory = if (next == Direction.CREDIT) "income" else "food"
        showAll = false
    }

    fun handleKey(key: String) {
        when (key) {
            "note" -> showWho = true
            "⌫" -> amount = amount.dropLast(1)
            else -> if (key.all(Char::isDigit) && amount.length < 8) {
                amount = (amount + key).trimStart('0')
            }
        }
    }

    fun save() {
        if (amountMinor <= 0) return
        val categoryId = selectedCategory.ifBlank { if (direction == Direction.CREDIT) "income" else "misc" }
        onSave(
            Transaction(
                id = UUID.randomUUID().toString(),
                amountMinor = amountMinor,
                direction = direction,
                occurredAt = System.currentTimeMillis(),
                merchant = whoGotIt.ifBlank { if (direction == Direction.CREDIT) "Money received" else "Manual expense" },
                categoryId = categoryId,
                note = "",
                status = TransactionStatus.CONFIRMED,
                source = TransactionSource.MANUAL,
            ),
        )
    }

    Column(
        modifier.fillMaxSize()
            .background(receiptsColors.paper)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .imePadding()
            .padding(horizontal = ReceiptsSpace.screen, vertical = ReceiptsSpace.x4),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("New receipt", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
            CloseTextButton("Close new receipt", onClose)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
        ) {
            ReceiptHeroAmount(
                receiptMoney(amountMinor),
                Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x6),
                color = if (amountMinor == 0L) receiptsColors.fade else receiptsColors.ink,
                textAlign = TextAlign.End,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DirectionPill("Spent", direction == Direction.DEBIT) { setDirection(Direction.DEBIT) }
                Spacer(Modifier.width(ReceiptsSpace.x2))
                DirectionPill("Money in", direction == Direction.CREDIT) { setDirection(Direction.CREDIT) }
            }
            ReceiptDivider()
            ReceiptLabel("Category")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
                verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
            ) {
                categoryOptions(direction, showAll).forEach { item ->
                    CategoryChoice(item, selectedCategory == item.id) { selectedCategory = item.id }
                }
                if (direction == Direction.DEBIT && !showAll) {
                    MoreChoice { showAll = true }
                }
            }
            ReceiptLabel("Today · ${receiptLongDate(today)}")
            if (showWho) {
                ReceiptTextField(
                    value = whoGotIt,
                    onValueChange = { whoGotIt = it },
                    placeholder = "Who got it",
                )
            }
        }

        ReceiptAmountPad(onKey = ::handleKey, modifier = Modifier.padding(top = ReceiptsSpace.x3))
        ReceiptButton(
            text = "Save",
            onClick = ::save,
            enabled = amountMinor > 0,
            modifier = Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x2),
        )
    }
}

@Composable
private fun CloseTextButton(description: String, onClick: () -> Unit) {
    ReceiptIconButton(Icons.Default.Close, description, onClick)
}

@Composable
private fun DirectionPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .background(if (selected) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.pill))
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = if (selected) receiptsColors.paper else receiptsColors.fade, style = ReceiptsType.label)
    }
}

@Composable
private fun CategoryChoice(category: Category, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .background(if (selected) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.pill))
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptCategoryMark(category.id)
        Text(
            category.name,
            color = if (selected) receiptsColors.paper else receiptsColors.ink,
            style = ReceiptsType.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreChoice(onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text("MORE", color = receiptsColors.fade, style = ReceiptsType.label)
    }
}

private fun categoryOptions(direction: Direction, showAll: Boolean): List<Category> {
    if (direction == Direction.CREDIT) return Categories.filter { it.id in listOf("income", "repayments", "transfers") }
    val priority = listOf("food", "groceries", "transport", "shopping", "bills")
    val spend = Categories.filter { !it.notSpending && it.id != "refund" && it.id != "income" }
    return if (!showAll) priority.mapNotNull { id -> spend.firstOrNull { it.id == id } }
    else spend.sortedBy { item -> priority.indexOf(item.id).let { if (it == -1) Int.MAX_VALUE else it } }
}
