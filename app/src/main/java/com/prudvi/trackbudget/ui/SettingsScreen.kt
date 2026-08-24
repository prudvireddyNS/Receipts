package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.ReceiptsPreferences
import com.prudvi.trackbudget.model.Transaction

@Composable
fun SettingsScreen(
    preferences: ReceiptsPreferences,
    budget: Budget,
    transactions: List<Transaction>,
    learnedRules: List<LearnedRule>,
    ignoredSources: Set<String>,
    smsInboxGranted: Boolean,
    smsLiveGranted: Boolean,
    notificationsGranted: Boolean,
    scanning: Boolean,
    importedCount: Int?,
    onClose: () -> Unit,
    onPreferencesChange: (ReceiptsPreferences) -> Unit,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRescanSms: () -> Unit,
    onEditBudget: () -> Unit,
    onSourceIgnored: (String, Boolean) -> Unit,
    onRemoveRule: (String) -> Unit,
    onPinWidget: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    widgetInstalled: Boolean = false,
    widgetPinSupported: Boolean = true,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val sourceRows = remember(transactions) {
        buildList {
            transactions.mapNotNull { it.accountTail }.distinct().forEach { add("••$it" to "account:$it") }
            transactions.mapNotNull { it.sender }.distinct()
                .filter { sender -> any { it.second == "sender:$sender" }.not() }
                .forEach { add(it to "sender:$it") }
        }
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
                    Text("Settings", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                    ReceiptButton("Close", onClose, style = ReceiptButtonStyle.QUIET)
                }
                ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x3))
            }
            item {
                SettingsSection("Mode") {
                    ChoiceRow(
                        listOf("Chill" to AppMode.CHILL, "Pace" to AppMode.PACE, "Stack" to AppMode.STACK),
                        preferences.mode,
                    ) { onPreferencesChange(preferences.copy(mode = it)) }
                }
            }
            item {
                SettingsSection("Rhythm") {
                    ChoiceRow(
                        listOf("Monthly" to MoneyRhythm.MONTHLY, "Weekly" to MoneyRhythm.WEEKLY, "Irregular" to MoneyRhythm.ROLLING),
                        preferences.rhythm,
                    ) { onPreferencesChange(preferences.copy(rhythm = it)) }
                    if (preferences.rhythm == MoneyRhythm.MONTHLY) {
                        StepperRow("Monthly reset day", preferences.resetDay.toString()) {
                            onPreferencesChange(preferences.copy(resetDay = (preferences.resetDay + it).coerceIn(1, 28)))
                        }
                    }
                }
            }
            item {
                SettingsSection("Voice and appearance") {
                    ChoiceRow(listOf("Loud" to AppAmplitude.LOUD, "Quiet" to AppAmplitude.QUIET), preferences.amplitude) {
                        onPreferencesChange(preferences.copy(amplitude = it))
                    }
                    ChoiceRow(
                        listOf("Light" to AppThemePreference.LIGHT, "Dark" to AppThemePreference.DARK, "System" to AppThemePreference.SYSTEM),
                        preferences.theme,
                    ) { onPreferencesChange(preferences.copy(theme = it)) }
                }
            }
            item {
                SettingsSection("SMS") {
                    FactRow("Status", smsStatus(smsInboxGranted, smsLiveGranted))
                    ActionRow(if (smsInboxGranted) "Rescan inbox" else "Allow SMS reading", smsScanState(scanning, importedCount)) {
                        if (smsInboxGranted) onRescanSms() else onRequestSms()
                    }
                    Text("Manual entry works with SMS off.", color = receiptsColors.fade, style = ReceiptsType.meta)
                }
            }
            item {
                SettingsSection("Notifications") {
                    ActionRow("Pace and limit alerts", if (notificationsGranted) "On" else "Off") { onRequestNotifications() }
                }
            }
            item {
                SettingsSection("Budget") {
                    ActionRow("Current budget", "${receiptMoney(budget.amountMinor)} · ${budget.period}", onClick = onEditBudget)
                }
            }
            item {
                SettingsSection("Sources") {
                    if (sourceRows.isEmpty()) {
                        Text("No sources stored yet.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                    sourceRows.forEach { (label, key) ->
                        SourceRow(label, ignoredSources.contains(key).not()) { enabled -> onSourceIgnored(key, !enabled) }
                    }
                }
            }
            item {
                SettingsSection("Learned rules") {
                    if (learnedRules.isEmpty()) {
                        Text("Rules saved during review appear here.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                    learnedRules.forEach { rule ->
                        ActionRow("${rule.merchant} → ${categoryName(rule.categoryId)}", "Remove") { onRemoveRule(rule.merchant) }
                    }
                }
            }
            item {
                SettingsSection("Widget") {
                    ActionRow(
                        "Home screen widget",
                        when {
                            widgetInstalled -> "Added"
                            widgetPinSupported -> "Pin"
                            else -> "Unavailable"
                        },
                        enabled = widgetPinSupported && widgetInstalled.not(),
                        onClick = onPinWidget,
                    )
                }
            }
            item {
                SettingsSection("Data") {
                    FactRow("Stored transactions", transactions.size.toString())
                    FactRow("Network access", "None")
                    ActionRow("Clear all transaction data", "Confirm", danger = true) { confirmClear = true }
                }
            }
        }
        if (confirmClear) {
            ConfirmClear(
                onCancel = { confirmClear = false },
                onClear = {
                    confirmClear = false
                    onClearAll()
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ReceiptCard {
        Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
            ReceiptLabel(title)
            content()
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        options.forEach { (label, value) ->
            SelectChip(label, selected == value) { onSelected(value) }
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
            .semantics {
                role = Role.RadioButton
                contentDescription = label
                this.selected = selected
            }
            .padding(horizontal = ReceiptsSpace.x2, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) receiptsColors.paper else receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 1)
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
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = receiptsColors.inkSoft, style = ReceiptsType.body)
        Text(value, color = receiptsColors.ink, style = ReceiptsType.amount)
    }
}

@Composable
private fun ActionRow(label: String, value: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12).alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = if (danger) receiptsColors.chilli else receiptsColors.ink, style = ReceiptsType.bodyStrong)
        Text(value, color = if (danger) receiptsColors.chilli else receiptsColors.fade, style = ReceiptsType.amount)
    }
}

@Composable
private fun SourceRow(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clickable(role = Role.Switch) { onChange(!enabled) }
            .semantics {
                role = Role.Switch
                contentDescription = "$label source"
                stateDescription = if (enabled) "On" else "Off"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.body)
        ReceiptPill(if (enabled) "On" else "Off", selected = enabled, style = ReceiptButtonStyle.QUIET)
    }
}

@Composable
private fun ConfirmClear(onCancel: () -> Unit, onClear: () -> Unit) {
    Box(Modifier.fillMaxSize().background(receiptsColors.scrim).padding(ReceiptsSpace.screen), contentAlignment = Alignment.Center) {
        ReceiptCard {
            Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4)) {
                Text("Clear all transaction data", color = receiptsColors.ink, style = ReceiptsType.heading)
                Text("This removes stored transactions. Budget, mode, and settings stay.", color = receiptsColors.inkSoft, style = ReceiptsType.body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                    ReceiptButton("Cancel", onCancel, Modifier.weight(1f), style = ReceiptButtonStyle.QUIET)
                    ReceiptButton("Clear data", onClear, Modifier.weight(1f), style = ReceiptButtonStyle.CHILLI)
                }
            }
        }
    }
}

private fun smsStatus(inbox: Boolean, live: Boolean): String = when {
    inbox && live -> "On"
    inbox -> "Inbox only"
    else -> "Off"
}

private fun smsScanState(scanning: Boolean, importedCount: Int?): String = when {
    scanning -> "Scanning"
    importedCount == null -> "Scan"
    else -> "$importedCount added"
}
