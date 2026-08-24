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
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.ReceiptsPreferences
import java.util.UUID

@Composable
fun OnboardingScreen(
    smsGranted: Boolean,
    onRequestSms: () -> Unit,
    onFinish: (ReceiptsPreferences, Budget, Goal?) -> Unit,
    modifier: Modifier = Modifier,
    initialPreferences: ReceiptsPreferences = ReceiptsPreferences(),
    initialBudget: Budget = Budget(),
    scanning: Boolean = false,
    importedCount: Int? = null,
) {
    var mode by rememberSaveable { mutableStateOf(initialPreferences.mode) }
    var rhythm by rememberSaveable { mutableStateOf(initialPreferences.rhythm) }
    var paceAmount by rememberSaveable { mutableStateOf((initialBudget.amountMinor / 100).toString()) }
    var goalName by rememberSaveable { mutableStateOf("") }
    var goalTarget by rememberSaveable { mutableStateOf("") }
    val amountIsValid = mode == AppMode.PACE && decimalToMinor(paceAmount) > 0
    val goalIsValid = mode == AppMode.STACK && goalName.isNotBlank() && decimalToMinor(goalTarget) > 0
    val canFinish = when (mode) {
        AppMode.CHILL -> true
        AppMode.PACE -> amountIsValid
        AppMode.STACK -> goalIsValid
    }

    LazyColumn(
        modifier.fillMaxSize().background(receiptsColors.paper).imePadding(),
        contentPadding = PaddingValues(
            start = ReceiptsSpace.screen,
            end = ReceiptsSpace.screen,
            top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + ReceiptsSpace.x6,
            bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + ReceiptsSpace.x8,
        ),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
    ) {
        item {
            Text("RECEIPTS", color = receiptsColors.ink, style = ReceiptsType.label)
            Spacer(Modifier.height(ReceiptsSpace.x3))
            Text("Start with manual entry. Add SMS later if it helps.", color = receiptsColors.ink, style = ReceiptsType.display)
        }
        item {
            PreferenceGroup("How should Receipts feel?") {
                ModeOption(AppMode.CHILL, "Chill", "Shows what happened. No pace rail.", mode) { mode = it }
                ModeOption(AppMode.PACE, "Pace", "Shows budget pace and category limits.", mode) { mode = it }
                ModeOption(AppMode.STACK, "Stack", "Keeps one saving goal in front.", mode) { mode = it }
            }
        }
        item {
            PreferenceGroup("What rhythm should it use?") {
                RhythmOption(MoneyRhythm.MONTHLY, "Monthly", "Calendar-style month.", rhythm) { rhythm = it }
                RhythmOption(MoneyRhythm.WEEKLY, "Weekly", "Monday through Sunday.", rhythm) { rhythm = it }
                RhythmOption(MoneyRhythm.ROLLING, "Irregular", "Rolling 30-day view.", rhythm) { rhythm = it }
            }
        }
        item {
            when (mode) {
                AppMode.CHILL -> ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                        ReceiptLabel("Setup")
                        Text("No budget needed for Chill. You can add one later in Settings.", color = receiptsColors.inkSoft, style = ReceiptsType.body)
                    }
                }
                AppMode.PACE -> ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("Budget")
                        ReceiptTextField(
                            value = paceAmount,
                            onValueChange = { paceAmount = it.filter(Char::isDigit).take(10) },
                            placeholder = "Monthly budget in rupees",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("This sets the pace rail and daily hold number.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
                AppMode.STACK -> ReceiptCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                        ReceiptLabel("First goal")
                        ReceiptTextField(goalName, { goalName = it.take(40) }, "Goal name")
                        ReceiptTextField(
                            value = goalTarget,
                            onValueChange = { goalTarget = it.filter(Char::isDigit).take(10) },
                            placeholder = "Target amount in rupees",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("Receipts will show this goal on Today.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
        }
        item {
            ReceiptCard {
                Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
                    ReceiptLabel("SMS import")
                    Text(
                        if (smsGranted) "SMS reading is on." else "Receipts can read payment messages on this phone. It has no network permission.",
                        color = receiptsColors.inkSoft,
                        style = ReceiptsType.body,
                    )
                    importedCount?.let { Text("Last scan added $it receipts.", color = receiptsColors.fade, style = ReceiptsType.meta) }
                    if (scanning) Text("Scanning inbox.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    if (smsGranted.not()) {
                        ReceiptButton("Allow SMS reading", onRequestSms, Modifier.fillMaxWidth(), style = ReceiptButtonStyle.OUTLINE)
                        Text("Without SMS you'll add receipts by hand. That works, it's just slower.", color = receiptsColors.fade, style = ReceiptsType.meta)
                    }
                }
            }
        }
        item {
            ReceiptButton(
                text = "Start",
                onClick = {
                    val preferences = initialPreferences.copy(mode = mode, rhythm = rhythm)
                    val budget = initialBudget.copy(
                        amountMinor = if (mode == AppMode.PACE) decimalToMinor(paceAmount) else initialBudget.amountMinor,
                        period = rhythm.budgetPeriod(),
                        resetDay = preferences.resetDay.coerceIn(1, 28),
                        startEpochDay = null,
                        endEpochDay = null,
                    )
                    val target = decimalToMinor(goalTarget)
                    val goal = if (mode == AppMode.STACK && goalName.isNotBlank() && target > 0) {
                        Goal(UUID.randomUUID().toString(), goalName.trim(), target)
                    } else null
                    onFinish(preferences, budget, goal)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canFinish,
                style = ReceiptButtonStyle.INK,
            )
        }
    }
}

@Composable
private fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        Text(title, color = receiptsColors.ink, style = ReceiptsType.heading)
        content()
    }
}

@Composable
private fun ModeOption(mode: AppMode, title: String, body: String, selected: AppMode, onSelect: (AppMode) -> Unit) {
    PreferenceOption(title, body, selected == mode) { onSelect(mode) }
}

@Composable
private fun RhythmOption(rhythm: MoneyRhythm, title: String, body: String, selected: MoneyRhythm, onSelect: (MoneyRhythm) -> Unit) {
    PreferenceOption(title, body, selected == rhythm) { onSelect(rhythm) }
}

@Composable
private fun PreferenceOption(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(if (selected) receiptsColors.ultramarineTint else receiptsColors.paperRaised)
            .border(1.dp, if (selected) receiptsColors.ultramarine else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = "$title. $body"
            }
            .padding(ReceiptsSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = ReceiptsSpace.x6, minHeight = ReceiptsSpace.x6)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(if (selected) receiptsColors.ultramarine else receiptsColors.sunk),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("ON", color = receiptsColors.ultramarineOn, style = ReceiptsType.label)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = receiptsColors.ink, style = ReceiptsType.bodyStrong)
            Text(body, color = receiptsColors.fade, style = ReceiptsType.meta)
        }
    }
}

private fun MoneyRhythm.budgetPeriod(): String = when (this) {
    MoneyRhythm.MONTHLY -> "Month"
    MoneyRhythm.WEEKLY -> "Week"
    MoneyRhythm.ROLLING -> "Rolling"
}
