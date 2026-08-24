package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.dashboard
import java.time.LocalDate

@Composable
fun BudgetSheet(
    budget: Budget,
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onSave: (Budget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf((budget.amountMinor / 100).toString()) }
    var period by rememberSaveable { mutableStateOf(budget.period) }
    var resetDay by rememberSaveable { mutableStateOf(budget.resetDay.coerceIn(1, 28)) }
    var repeats by rememberSaveable { mutableStateOf(budget.repeats) }
    var carryOver by rememberSaveable { mutableStateOf(budget.carryOver) }
    var start by remember { mutableStateOf(budget.startEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var end by remember { mutableStateOf(budget.endEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(30)) }
    var limits by remember { mutableStateOf(budget.categoryLimits) }
    var editingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var limitAmount by rememberSaveable { mutableStateOf("") }
    val draft = budget.copy(
        amountMinor = decimalToMinor(amount),
        period = period,
        repeats = repeats,
        carryOver = carryOver,
        resetDay = resetDay,
        startEpochDay = if (period == "Custom") start.toEpochDay() else null,
        endEpochDay = if (period == "Custom") end.toEpochDay() else null,
        categoryLimits = limits,
    )
    val range = budgetRange(draft)
    val snapshot = dashboard(transactions, draft)
    val spendingCategories = remember { Categories.filter { it.notSpending.not() && it.id.equals("refund").not() } }

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
                    Text("Budget", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                    ReceiptButton("Close", onClose, style = ReceiptButtonStyle.QUIET)
                }
                ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x3))
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Period")
                        PeriodRow(period) { period = it }
                        if (period == "Custom") DateRangeControl(start, end, { start = it.coerceAtMost(end) }, { end = it.coerceAtLeast(start) })
                        if (period == "Month") StepperRow("Monthly reset day", resetDay.toString()) { resetDay = (resetDay + it).coerceIn(1, 28) }
                        Text("${receiptLongDate(range.start)} to ${receiptLongDate(range.endInclusive)} · ${range.days} days", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Amount")
                        ReceiptTextField(
                            value = amount,
                            onValueChange = { amount = it.filter(Char::isDigit).take(10) },
                            placeholder = "Budget amount in rupees",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("${receiptMoney(draft.amountMinor / range.days)} per day", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                        ReceiptLabel("Rules")
                        ToggleRow("Repeat this budget", repeats) { repeats = it }
                        ToggleRow("Carry unspent money", carryOver) { carryOver = it }
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ReceiptLabel("Category limits", Modifier.weight(1f))
                            ReceiptPill(
                                if (editingCategory == null) "Add" else "Cancel",
                                onClick = {
                                    editingCategory = if (editingCategory == null) spendingCategories.firstOrNull()?.id else null
                                    limitAmount = editingCategory?.let { limits[it]?.div(100)?.toString() }.orEmpty()
                                },
                            )
                        }
                        if (limits.isEmpty()) Text("No category limits set.", color = receiptsColors.fade, style = ReceiptsType.meta)
                        limits.toList().forEach { (categoryId, limitMinor) ->
                            LimitRow(
                                categoryId = categoryId,
                                limitMinor = limitMinor,
                                spentMinor = snapshot.categoryTotals[categoryId] ?: 0L,
                                onEdit = {
                                    editingCategory = categoryId
                                    limitAmount = (limitMinor / 100).toString()
                                },
                                onRemove = {
                                    limits = limits - categoryId
                                    if (editingCategory == categoryId) editingCategory = null
                                },
                            )
                        }
                        editingCategory?.let { selectedCategory ->
                            LimitEditor(
                                categories = spendingCategories.map { it.id },
                                selectedCategory = selectedCategory,
                                amount = limitAmount,
                                onCategory = {
                                    editingCategory = it
                                    limitAmount = limits[it]?.div(100)?.toString().orEmpty()
                                },
                                onAmount = { limitAmount = it.filter(Char::isDigit).take(10) },
                                onSave = {
                                    val limit = decimalToMinor(limitAmount)
                                    if (limit > 0) {
                                        limits = limits + (selectedCategory to limit)
                                        editingCategory = null
                                        limitAmount = ""
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                ReceiptButton(
                    "Save budget",
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = decimalToMinor(amount) > 0,
                    style = ReceiptButtonStyle.INK,
                )
            }
        }
    }
}

@Composable
private fun PeriodRow(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        listOf("Week", "Month", "Rolling", "Custom").chunked(2).forEach { periods ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                periods.forEach { period ->
                    SelectChip(period, selected == period, Modifier.weight(1f)) { onSelected(period) }
                }
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(if (selected) receiptsColors.ink else receiptsColors.paper)
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { role = Role.RadioButton; contentDescription = label; this.selected = selected }
            .padding(horizontal = ReceiptsSpace.x2, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) receiptsColors.paper else receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 1)
    }
}

@Composable
private fun DateRangeControl(start: LocalDate, end: LocalDate, onStart: (LocalDate) -> Unit, onEnd: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        DateStepper("Starts", start, onStart)
        DateStepper("Ends", end, onEnd)
    }
}

@Composable
private fun DateStepper(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.body)
        StepButton("−") { onChange(date.minusDays(1)) }
        Text(receiptShortDate(date), Modifier.padding(horizontal = ReceiptsSpace.x3), color = receiptsColors.ink, style = ReceiptsType.amount)
        StepButton("+") { onChange(date.plusDays(1)) }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.body)
        StepButton("−") { onStep(-1) }
        Text(value, Modifier.padding(horizontal = ReceiptsSpace.x3), color = receiptsColors.ink, style = ReceiptsType.amount)
        StepButton("+") { onStep(1) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = ReceiptsSpace.x12, minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.paper)
            .border(1.dp, receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = receiptsColors.ink, style = ReceiptsType.heading) }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clickable(role = Role.Switch) { onChecked(checked.not()) }
            .semantics { role = Role.Switch; contentDescription = label; stateDescription = if (checked) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body)
        ReceiptPill(if (checked) "On" else "Off", selected = checked, style = ReceiptButtonStyle.QUIET)
    }
}

@Composable
private fun LimitRow(categoryId: String, limitMinor: Long, spentMinor: Long, onEdit: () -> Unit, onRemove: () -> Unit) {
    val fraction = (spentMinor.toFloat() / limitMinor.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12).clickable(role = Role.Button, onClick = onEdit)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
            ReceiptCategoryMark(categoryId)
            Text(categoryName(categoryId), Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong)
            Text(receiptMoney(limitMinor), color = receiptsColors.ink, style = ReceiptsType.amount)
            ReceiptPill("Remove", style = ReceiptButtonStyle.QUIET, onClick = onRemove)
        }
        Box(
            Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x2).height(ReceiptsSpace.x2).clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(receiptsColors.sunk)
                .semantics {
                    contentDescription = "${categoryName(categoryId)} ${receiptMoney(spentMinor)} of ${receiptMoney(limitMinor)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (spentMinor > limitMinor) receiptsColors.chilli else receiptsColors.ultramarine))
        }
        Text("${receiptMoney(spentMinor)} used", color = receiptsColors.fade, style = ReceiptsType.meta)
    }
}

@Composable
private fun LimitEditor(
    categories: List<String>,
    selectedCategory: String,
    amount: String,
    onCategory: (String) -> Unit,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paper).border(1.dp, receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .padding(ReceiptsSpace.x3),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        ReceiptLabel("Limit editor")
        categories.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                row.forEach { id -> SelectChip(categoryName(id), selectedCategory == id, Modifier.weight(1f)) { onCategory(id) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ReceiptTextField(amount, onAmount, "Limit amount in rupees", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        ReceiptButton("Save limit", onSave, Modifier.fillMaxWidth(), enabled = decimalToMinor(amount) > 0, style = ReceiptButtonStyle.OUTLINE)
    }
}
