package com.prudvi.trackbudget.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.amountForPeriod
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.obligationsMinor
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Commitment
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.ReceiptsPreferences
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource

@Composable
fun SettingsScreen(
    preferences: ReceiptsPreferences,
    budget: Budget,
    transactions: List<Transaction>,
    learnedRules: List<LearnedRule>,
    smsLiveGranted: Boolean,
    notificationsGranted: Boolean,
    biometricAvailable: Boolean,
    onClose: () -> Unit,
    onPreferencesChange: (ReceiptsPreferences) -> Unit,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onEditBudget: () -> Unit,
    onRemoveRule: (String) -> Unit,
    onPinWidget: () -> Unit,
    onOpenRecap: () -> Unit,
    onBudgetChange: (Budget) -> Unit = {},
    onNoBudget: () -> Unit = {},
    onRerunOnboarding: () -> Unit,
    onClearAll: () -> Unit,
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier,
    widgetInstalled: Boolean = false,
    widgetPinSupported: Boolean = true,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmOnboarding by rememberSaveable { mutableStateOf(false) }
    val rhythm = if (preferences.rhythm == MoneyRhythm.WEEKLY) MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
    val hasBudget = budget.amountMinor > 0L
    val smsCount = transactions.count { it.source == TransactionSource.SMS }
    val smsAccounts = transactions.filter { it.source == TransactionSource.SMS && (it.accountTail != null || it.sender != null) }
        .distinctBy { it.accountTail to it.sender }

    Box(modifier.fillMaxSize().background(receiptsColors.paper)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 22.dp,
                    bottom = 14.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                ClosePill(onClose)
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
            item {
                CollapsibleSection("Budget & period", background = receiptsColors.cyan, labelColor = ColorCyanLabel) {
                    Text(periodBlurb(rhythm), color = receiptsColors.inkSoft, style = ReceiptsType.meta.copy(fontSize = 12.sp), modifier = Modifier.padding(bottom = 9.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PeriodChip("Monthly", rhythm == MoneyRhythm.MONTHLY) {
                            onPreferencesChange(preferences.copy(rhythm = MoneyRhythm.MONTHLY, resetDay = preferences.resetDay.coerceIn(1, 28)))
                            onBudgetChange(budget.copy(period = "Month", resetDay = budget.resetDay.coerceIn(1, 28), amountMinor = budget.amountForPeriod("Month")))
                        }
                        PeriodChip("Weekly", rhythm == MoneyRhythm.WEEKLY) {
                            onPreferencesChange(preferences.copy(rhythm = MoneyRhythm.WEEKLY, resetDay = preferences.resetDay.coerceIn(1, 7)))
                            onBudgetChange(budget.copy(period = "Week", resetDay = budget.resetDay.coerceIn(1, 7), amountMinor = budget.amountForPeriod("Week")))
                        }
                    }
                    if (rhythm == MoneyRhythm.WEEKLY) {
                        WeekdayStepper(preferences.resetDay.coerceIn(1, 7), Modifier.padding(top = 11.dp)) { day ->
                            onPreferencesChange(preferences.copy(resetDay = day))
                            onBudgetChange(budget.copy(period = "Week", resetDay = day))
                        }
                    } else {
                        NumberStepper("Resets on day", preferences.resetDay.coerceIn(1, 28).toString(), Modifier.padding(top = 11.dp)) { delta ->
                            val next = (preferences.resetDay + delta).coerceIn(1, 28)
                            onPreferencesChange(preferences.copy(resetDay = next))
                            onBudgetChange(budget.copy(period = "Month", resetDay = next))
                        }
                    }
                    ReceiptDivider(Modifier.padding(top = 13.dp, bottom = 11.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (hasBudget) "Budget per ${if (rhythm == MoneyRhythm.WEEKLY) "week" else "month"}" else "No budget set", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 12.sp))
                        TogglePill(hasBudget) { if (hasBudget) onNoBudget() else onBudgetChange(budget.copy(amountMinor = if (rhythm == MoneyRhythm.WEEKLY) 250_000L else 800_000L)) }
                    }
                    if (hasBudget) {
                        var budgetAmountText by rememberSaveable(hasBudget) { mutableStateOf((budget.amountMinor / 100).toString()) }
                        // Commit once typing settles. Saving on every keystroke turned 30000 → 25000
                        // into budgets of 3, 30, 300 and 3000 along the way, each one alerting and
                        // re-confirming the period.
                        val latestBudget by rememberUpdatedState(budget)
                        LaunchedEffect(budgetAmountText) {
                            delay(700)
                            val minor = decimalToMinor(budgetAmountText)
                            if (minor > 0L && minor != latestBudget.amountMinor) onBudgetChange(latestBudget.copy(amountMinor = minor))
                        }
                        ReceiptLabel("Amount", Modifier.padding(top = 9.dp))
                        ReceiptTextField(
                            value = budgetAmountText,
                            onValueChange = { next ->
                                budgetAmountText = sanitizeAmountText(next)
                            },
                            placeholder = "Amount in rupees",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        val perDayRange = budgetRange(budget)
                        val perDaySpendable = (budget.amountMinor - budget.obligationsMinor(perDayRange)).coerceAtLeast(0L)
                        Text("${receiptMoney(perDaySpendable / perDayRange.days)} per day at an even pace", color = receiptsColors.fade, style = ReceiptsType.meta, modifier = Modifier.padding(top = 7.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Investments count as spending", Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.meta)
                        TogglePill(preferences.countInvestmentsAsSpending) { onPreferencesChange(preferences.copy(countInvestmentsAsSpending = !preferences.countInvestmentsAsSpending)) }
                    }
                    ReceiptDivider(Modifier.padding(top = 13.dp, bottom = 11.dp))
                    Text("Fixed obligations", color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 12.sp))
                    Text(
                        "Rent, family, investments — taken off your budget before the period starts, so they never show up as spending.",
                        color = receiptsColors.fade,
                        style = ReceiptsType.meta,
                        modifier = Modifier.padding(top = 3.dp, bottom = 9.dp),
                    )
                    var newCommitmentName by rememberSaveable { mutableStateOf("") }
                    budget.commitments.forEach { commitment ->
                        CommitmentRow(
                            commitment = commitment,
                            onToggle = { onBudgetChange(budget.copy(commitments = budget.commitments.map { if (it.id == commitment.id) it.copy(enabled = !it.enabled) else it })) },
                            onStep = { delta ->
                                val next = (commitment.monthlyAmountMinor + delta).coerceAtLeast(0L)
                                onBudgetChange(budget.copy(commitments = budget.commitments.map { if (it.id == commitment.id) it.copy(monthlyAmountMinor = next) else it }))
                            },
                            onRemove = { onBudgetChange(budget.copy(commitments = budget.commitments.filter { it.id != commitment.id })) },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ReceiptTextField(
                            value = newCommitmentName,
                            onValueChange = { newCommitmentName = it },
                            placeholder = "New obligation",
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.size(48.dp).clickable(role = Role.Button, enabled = newCommitmentName.isNotBlank()) {
                                onBudgetChange(
                                    budget.copy(
                                        commitments = budget.commitments + Commitment(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = newCommitmentName.trim(),
                                            monthlyAmountMinor = 100_000L,
                                        ),
                                    ),
                                )
                                newCommitmentName = ""
                            }.semantics { contentDescription = "Add obligation" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(ReceiptsRadius.small))
                                    .background(if (newCommitmentName.isNotBlank()) receiptsColors.yellow else receiptsColors.paper)
                                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.small)),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = receiptsColors.ink, style = ReceiptsType.heading.copy(fontSize = 15.sp)) }
                        }
                    }
                    val activeObligationsMinor = budget.commitments.filter { it.enabled }.sumOf { it.monthlyAmountMinor }
                    Text(
                        when {
                            activeObligationsMinor <= 0L -> "Nothing set aside yet."
                            hasBudget -> "${receiptMoney(activeObligationsMinor)}/month off the top — ${receiptMoney((budget.amountMinor - activeObligationsMinor).coerceAtLeast(0L))} of your ${receiptMoney(budget.amountMinor)} left to spend."
                            else -> "${receiptMoney(activeObligationsMinor)}/month set aside. Set a budget and this comes off the top of it."
                        },
                        color = receiptsColors.fade,
                        style = ReceiptsType.meta,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
            item {
                CollapsibleSection("Appearance", initiallyExpanded = false) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ThemeChip("Colorful", preferences.theme == AppThemePreference.COLORFUL) { onPreferencesChange(preferences.copy(theme = AppThemePreference.COLORFUL)) }
                        ThemeChip("Subtle", preferences.theme == AppThemePreference.SUBTLE) { onPreferencesChange(preferences.copy(theme = AppThemePreference.SUBTLE)) }
                        ThemeChip("Light", preferences.theme == AppThemePreference.LIGHT) { onPreferencesChange(preferences.copy(theme = AppThemePreference.LIGHT)) }
                        ThemeChip("Dark", preferences.theme == AppThemePreference.DARK) { onPreferencesChange(preferences.copy(theme = AppThemePreference.DARK)) }
                    }
                }
            }
            item {
                CollapsibleSection("Automation", initiallyExpanded = false) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Reading payment texts", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        TogglePill(preferences.smsTrackingEnabled && smsLiveGranted) {
                            val next = !(preferences.smsTrackingEnabled && smsLiveGranted)
                            onPreferencesChange(preferences.copy(smsTrackingEnabled = next))
                            if (next && !smsLiveGranted) onRequestSms()
                        }
                    }
                    if (smsAccounts.isEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("No payment texts yet", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                            Text(if (preferences.smsTrackingEnabled && smsLiveGranted) "0 added" else "paused", color = receiptsColors.inkSoft, style = ReceiptsType.amount.copy(fontSize = 11.5.sp))
                        }
                    } else {
                        smsAccounts.forEach { account ->
                            val accountCount = transactions.count { it.source == TransactionSource.SMS && it.accountTail == account.accountTail && it.sender == account.sender }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("••${account.accountTail ?: "—"} · ${account.sender ?: "SMS"}", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                                Text(if (preferences.smsTrackingEnabled && smsLiveGranted) "$accountCount added" else "paused", color = receiptsColors.inkSoft, style = ReceiptsType.amount.copy(fontSize = 11.5.sp))
                            }
                        }
                    }
                    if (learnedRules.isEmpty()) {
                        Text("No merchant rules learned yet", color = receiptsColors.fade, style = ReceiptsType.meta, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        learnedRules.forEach { rule ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${rule.merchant} → ${categoryName(rule.categoryId)}", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                                Text("Remove", color = receiptsColors.fade, style = ReceiptsType.amount.copy(fontSize = 11.sp), modifier = Modifier.clickable { onRemoveRule(rule.merchant) })
                            }
                        }
                    }
                }
            }
            item {
                CollapsibleSection("Alerts & widget", initiallyExpanded = false) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Budget alerts", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        TogglePill(notificationsGranted) { if (!notificationsGranted) onRequestNotifications() }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Home screen widget", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        Text(if (widgetInstalled) "Added" else if (widgetPinSupported) "Pin" else "Unavailable", color = receiptsColors.inkSoft, style = ReceiptsType.amount.copy(fontSize = 11.5.sp), modifier = Modifier.clickable(enabled = widgetPinSupported && !widgetInstalled, onClick = onPinWidget))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Biometric lock", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        TogglePill(preferences.biometricLockEnabled && biometricAvailable, enabled = biometricAvailable) { onPreferencesChange(preferences.copy(biometricLockEnabled = !preferences.biometricLockEnabled)) }
                    }
                }
            }
            item {
                CollapsibleSection("Reports", initiallyExpanded = false) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(ReceiptsRadius.medium))
                            .background(receiptsColors.purple)
                            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
                            .clickable(role = Role.Button, onClick = onOpenRecap)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (rhythm == MoneyRhythm.WEEKLY) "Open this week's recap" else "Open this month's recap", color = receiptsColors.ultramarineOn, style = ReceiptsType.bodyStrong.copy(fontSize = 12.5.sp)) }
                }
            }
            item {
                CollapsibleSection("Data", background = receiptsColors.pinkTint, labelColor = DataLabel, initiallyExpanded = false) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${transactions.size} stored · no network · not backed up", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        Text("Clear", color = DataLabel, style = ReceiptsType.amount.copy(fontSize = 11.5.sp), modifier = Modifier.clickable(role = Role.Button) { confirmClear = true })
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = Role.Button, onClick = onExport), verticalAlignment = Alignment.CenterVertically) {
                        Text("Export receipts as CSV", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        Text("Export", color = DataLabel, style = ReceiptsType.amount.copy(fontSize = 11.5.sp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = Role.Button) { confirmOnboarding = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Run first-time setup again", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body.copy(fontSize = 12.sp))
                        Text("Start", color = DataLabel, style = ReceiptsType.amount.copy(fontSize = 11.5.sp))
                    }
                }
            }
        }
        }
        if (confirmClear) ConfirmDialog(
            title = "Clear receipts?",
            body = "This removes stored transactions and review state. Budget and settings stay.",
            confirm = "Clear",
            danger = true,
            onCancel = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
        )
        if (confirmOnboarding) ConfirmDialog(
            title = "Run setup again?",
            body = "This changes setup preferences but keeps existing transactions.",
            confirm = "Start",
            onCancel = { confirmOnboarding = false },
            onConfirm = {
                confirmOnboarding = false
                onRerunOnboarding()
            },
        )
    }
}

private val ColorCyanLabel: androidx.compose.ui.graphics.Color
    @Composable get() = if (receiptsColors.monochrome) receiptsColors.ink else androidx.compose.ui.graphics.Color(0xFF0E7C86)
private val DataLabel: androidx.compose.ui.graphics.Color
    @Composable get() = if (receiptsColors.monochrome) receiptsColors.ink else androidx.compose.ui.graphics.Color(0xFFC21B57)

@Composable
private fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    background: androidx.compose.ui.graphics.Color = receiptsColors.paper,
    labelColor: androidx.compose.ui.graphics.Color = receiptsColors.fade,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(ReceiptsRadius.large))
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.large)),
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { role = Role.Button; stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReceiptLabel(title, Modifier.weight(1f), color = labelColor)
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = receiptsColors.fade,
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, bottom = 13.dp), content = content)
        }
    }
}

@Composable
private fun ClosePill(onClick: () -> Unit) {
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(receiptsColors.paper)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Close".uppercase(), color = receiptsColors.ink, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp)) }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ReceiptPill(label, selected = selected, onClick = onClick)
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ReceiptPill(label, selected = selected, onClick = onClick)
}

@Composable
private fun TogglePill(checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    ReceiptTogglePill(checked, enabled = enabled, onClick = onClick)
}

@Composable
private fun NumberStepper(label: String, value: String, modifier: Modifier = Modifier, onStep: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.bodyStrong.copy(fontSize = 12.sp))
        StepBox("−", "Decrease $label") { onStep(-1) }
        Text(value, color = receiptsColors.ink, style = ReceiptsType.amount.copy(fontSize = 13.sp), modifier = Modifier.padding(horizontal = 4.dp))
        StepBox("+", "Increase $label") { onStep(1) }
    }
}

@Composable
private fun WeekdayStepper(selected: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    NumberStepper("Week starts", days[(selected - 1).coerceIn(0, 6)], modifier) { delta -> onSelected(((selected - 1 + delta + 7) % 7) + 1) }
}

@Composable
private fun CommitmentRow(commitment: Commitment, onToggle: () -> Unit, onStep: (Long) -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TogglePill(commitment.enabled, onClick = onToggle)
        Text(
            commitment.name,
            Modifier.weight(1f, fill = true),
            color = receiptsColors.ink,
            style = ReceiptsType.body.copy(fontSize = 12.sp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        StepBox("−", "Decrease ${commitment.name}", size = 24.dp) { onStep(-50_000L) }
        Text(
            "${receiptMoney(commitment.monthlyAmountMinor)}/mo",
            color = receiptsColors.ink,
            style = ReceiptsType.amount.copy(fontSize = 10.5.sp),
            modifier = Modifier.defaultMinSize(minWidth = 64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepBox("+", "Increase ${commitment.name}", size = 24.dp) { onStep(50_000L) }
        Text(
            "×",
            color = androidx.compose.ui.graphics.Color(0xFFC21B57),
            style = ReceiptsType.heading.copy(fontSize = 15.sp),
            modifier = Modifier.size(20.dp).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${commitment.name}" },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun StepBox(text: String, description: String, size: androidx.compose.ui.unit.Dp = 28.dp, onClick: () -> Unit) {
    val touchSize = maxOf(size + 20.dp, 40.dp)
    Box(Modifier.size(touchSize).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.paper).border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.small)),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = receiptsColors.ink, style = ReceiptsType.heading.copy(fontSize = if (size < 28.dp) 12.sp else 14.sp)) }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, danger: Boolean = false, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(Modifier.fillMaxSize().background(receiptsColors.scrim).padding(20.dp), contentAlignment = Alignment.Center) {
        ReceiptCard(background = if (danger) receiptsColors.pinkTint else receiptsColors.paper, radius = ReceiptsRadius.hero, shadow = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, color = receiptsColors.ink, style = ReceiptsType.heading)
                Text(body, color = receiptsColors.inkSoft, style = ReceiptsType.body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptButton("Cancel", onCancel, Modifier.weight(1f), style = ReceiptButtonStyle.OUTLINE)
                    ReceiptButton(confirm, onConfirm, Modifier.weight(1f), style = if (danger) ReceiptButtonStyle.CHILLI else ReceiptButtonStyle.CHROME)
                }
            }
        }
    }
}

private fun periodBlurb(rhythm: MoneyRhythm): String = if (rhythm == MoneyRhythm.WEEKLY) "Resets on a weekday you pick." else "Resets on a day you pick each month."

private fun sanitizeAmountText(value: String): String {
    val clean = value.filter { it.isDigit() || it == '.' }
    val before = clean.substringBefore('.').take(8)
    val after = clean.substringAfter('.', missingDelimiterValue = "").take(2)
    return if ('.' in clean) "$before.$after" else before
}
