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
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.obligationsMinor

@Composable
fun BudgetSheet(
    budget: Budget,
    onClose: () -> Unit,
    onSave: (Budget) -> Unit,
    modifier: Modifier = Modifier,
    previousAmountMinor: Long = 0L,
) {
    var amount by rememberSaveable(budget.amountMinor, previousAmountMinor) {
        mutableStateOf((budget.amountMinor.takeIf { it > 0L } ?: previousAmountMinor).takeIf { it > 0L }?.div(100)?.toString().orEmpty())
    }
    var noBudget by rememberSaveable(budget.amountMinor) { mutableStateOf(budget.amountMinor == 0L) }
    var period by rememberSaveable(budget.period) { mutableStateOf(if (budget.period == "Week") "Week" else "Month") }
    var resetDay by rememberSaveable(budget.resetDay, period) { mutableStateOf(budget.resetDay.coerceIn(if (period == "Week") 1..7 else 1..28)) }
    val amountMinor = if (noBudget) 0L else decimalToMinor(amount)
    val draft = budget.copy(
        amountMinor = amountMinor,
        period = period,
        resetDay = resetDay.coerceIn(if (period == "Week") 1..7 else 1..28),
        repeats = true,
        carryOver = false,
        startEpochDay = null,
        endEpochDay = null,
    )
    val range = budgetRange(draft)
    // Declared obligations come off the top of the budget, so the per-day figure has to be read
    // against what's actually left — otherwise this sheet promises a daily allowance the rest of
    // the app will never agree with.
    val obligationsMinor = draft.obligationsMinor(range)
    val spendableMinor = (amountMinor - obligationsMinor).coerceAtLeast(0L)

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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                            SelectChip("Monthly", period == "Month", Modifier.weight(1f)) {
                                period = "Month"
                                resetDay = resetDay.coerceIn(1, 28)
                            }
                            SelectChip("Weekly", period == "Week", Modifier.weight(1f)) {
                                period = "Week"
                                resetDay = resetDay.coerceIn(1, 7)
                            }
                        }
                        if (period == "Week") WeekdayRow(resetDay) { resetDay = it }
                        else StepperRow("Monthly reset day", resetDay.toString()) { resetDay = (resetDay + it).coerceIn(1, 28) }
                        Text("${receiptLongDate(range.start)} to ${receiptLongDate(range.endInclusive)} · ${range.days} days", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
            item {
                ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Amount")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                            SelectChip("Set amount", !noBudget, Modifier.weight(1f)) { noBudget = false }
                            SelectChip("No budget", noBudget, Modifier.weight(1f)) { noBudget = true }
                        }
                        if (!noBudget) {
                            ReceiptTextField(
                                value = amount,
                                onValueChange = { amount = sanitizeDecimal(it) },
                                placeholder = "Amount in rupees",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.semantics { contentDescription = "Budget amount" },
                            )
                            if (obligationsMinor > 0L) {
                                Text(
                                    "− ${receiptMoney(obligationsMinor)} fixed obligations = ${receiptMoney(spendableMinor)} to spend",
                                    color = receiptsColors.ink,
                                    style = ReceiptsType.meta,
                                )
                            }
                            Text("${receiptMoney(spendableMinor / range.days)} per day", color = receiptsColors.fade, style = ReceiptsType.meta)
                        } else {
                            Text("Home will show spending without a budget rail.", color = receiptsColors.fade, style = ReceiptsType.meta)
                        }
                    }
                }
            }
            item {
                ReceiptButton(
                    "Save budget",
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = noBudget || amountMinor > 0L,
                    style = ReceiptButtonStyle.INK,
                )
            }
        }
    }
}

@Composable
private fun WeekdayRow(selected: Int, onSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                row.forEachIndexed { index, label ->
                    val day = rowIndex * 4 + index + 1
                    SelectChip(label, selected == day, Modifier.weight(1f)) { onSelected(day) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.body)
        StepButton("−", "Decrease $label") { onStep(-1) }
        Text(value, Modifier.padding(horizontal = ReceiptsSpace.x3), color = receiptsColors.ink, style = ReceiptsType.amount)
        StepButton("+", "Increase $label") { onStep(1) }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minWidth = ReceiptsSpace.x12, minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.paper)
            .border(1.dp, receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = receiptsColors.ink, style = ReceiptsType.heading) }
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

private fun sanitizeDecimal(value: String): String {
    val clean = value.filter { it.isDigit() || it == '.' }
    val before = clean.substringBefore('.').take(8)
    val after = clean.substringAfter('.', missingDelimiterValue = "").take(2)
    return if ('.' in clean) "$before.$after" else before
}
