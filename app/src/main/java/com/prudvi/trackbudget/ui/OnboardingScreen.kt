package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.ReceiptsPreferences

@Composable
fun OnboardingScreen(
    smsGranted: Boolean,
    onRequestSms: () -> Unit,
    onFinish: (ReceiptsPreferences, Budget) -> Unit,
    modifier: Modifier = Modifier,
    initialPreferences: ReceiptsPreferences = ReceiptsPreferences(),
    initialBudget: Budget = Budget(),
) {
    var rhythm by rememberSaveable { mutableStateOf(if (initialPreferences.rhythm == MoneyRhythm.WEEKLY) MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY) }
    var resetDay by rememberSaveable { mutableStateOf(initialPreferences.resetDay.coerceIn(1, if (rhythm == MoneyRhythm.WEEKLY) 7 else 28)) }
    var budgetEnabled by rememberSaveable { mutableStateOf(true) }
    var budgetMinor by rememberSaveable {
        mutableStateOf(
            initialBudget.amountMinor.takeIf { it > 0L } ?: if (initialPreferences.rhythm == MoneyRhythm.WEEKLY) 250_000L else 800_000L,
        )
    }
    var smsWanted by rememberSaveable { mutableStateOf(initialPreferences.smsTrackingEnabled && smsGranted) }

    LazyColumn(
        modifier.fillMaxSize().background(receiptsColors.paper).imePadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            ReceiptLabel("RECEIPTS", color = receiptsColors.ink)
            Text(
                "Start with manual entry. Add SMS later if it helps.",
                color = receiptsColors.ink,
                style = ReceiptsType.display,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        item {
            ReceiptLabel("How do you want to track?", modifier = Modifier.padding(top = 22.dp))
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodOption(
                    name = "Monthly",
                    blurb = "Resets on a day you pick each month.",
                    selected = rhythm == MoneyRhythm.MONTHLY,
                ) {
                    rhythm = MoneyRhythm.MONTHLY
                    resetDay = resetDay.coerceIn(1, 28)
                    if (initialBudget.amountMinor == 0L) budgetMinor = 800_000L
                }
                PeriodOption(
                    name = "Weekly",
                    blurb = "Resets on a weekday you pick.",
                    selected = rhythm == MoneyRhythm.WEEKLY,
                ) {
                    rhythm = MoneyRhythm.WEEKLY
                    resetDay = resetDay.coerceIn(1, 7)
                    if (initialBudget.amountMinor == 0L) budgetMinor = 250_000L
                }
            }
        }
        item {
            ReceiptCard(Modifier.fillMaxWidth().padding(top = 14.dp), background = receiptsColors.cyan, radius = ReceiptsRadius.large) {
                Column {
                    ReceiptLabel("Budget", color = ColorCyanLabel)
                    Text("Optional — add it now or later in Settings.", color = receiptsColors.inkSoft, style = ReceiptsType.body, modifier = Modifier.padding(top = 5.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BudgetPick(
                            text = "${receiptMoney(budgetMinor)} / ${if (rhythm == MoneyRhythm.WEEKLY) "week" else "month"}",
                            selected = budgetEnabled,
                            modifier = Modifier.weight(1f),
                        ) { budgetEnabled = true }
                        BudgetPick("No budget", selected = !budgetEnabled) { budgetEnabled = false }
                    }
                }
            }
        }
        item {
            ReceiptCard(Modifier.fillMaxWidth().padding(top = 14.dp), background = receiptsColors.paper, radius = ReceiptsRadius.large) {
                Column {
                    ReceiptLabel("Messages")
                    Text(
                        "Receipts can read payment texts on this phone. It has no network permission.",
                        color = receiptsColors.inkSoft,
                        style = ReceiptsType.body,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    ReceiptButton(
                        "Allow reading texts",
                        {
                            smsWanted = true
                            if (!smsGranted) onRequestSms()
                        },
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        style = ReceiptButtonStyle.ULTRAMARINE,
                        textStyle = ReceiptsType.button.copy(fontSize = 14.sp),
                    )
                }
            }
        }
        item {
            ReceiptButton(
                text = "Start tracking",
                onClick = {
                    val cleanRhythm = if (rhythm == MoneyRhythm.WEEKLY) MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
                    val preferences = initialPreferences.copy(
                        rhythm = cleanRhythm,
                        resetDay = resetDay.coerceIn(if (cleanRhythm == MoneyRhythm.WEEKLY) 1..7 else 1..28),
                        smsTrackingEnabled = smsWanted,
                    )
                    val budget = initialBudget.copy(
                        amountMinor = if (budgetEnabled) budgetMinor else 0L,
                        period = if (cleanRhythm == MoneyRhythm.WEEKLY) "Week" else "Month",
                        resetDay = preferences.resetDay,
                        repeats = true,
                        carryOver = false,
                        startEpochDay = null,
                        endEpochDay = null,
                    )
                    onFinish(preferences, budget)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 6.dp),
                style = ReceiptButtonStyle.CHROME,
            )
        }
    }
}

private val ColorCyanLabel: androidx.compose.ui.graphics.Color
    @Composable get() = if (receiptsColors.monochrome) receiptsColors.ink else androidx.compose.ui.graphics.Color(0xFF0E7C86)

@Composable
private fun PeriodOption(name: String, blurb: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .then(if (selected) Modifier.mockShadow(ReceiptsRadius.card, 3.dp, 3.dp, receiptsColors.ink) else Modifier)
            .clip(RoundedCornerShape(ReceiptsRadius.card))
            .background(if (selected) receiptsColors.yellow else receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.card))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "$name. $blurb" }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = if (selected) receiptsColors.chromeOn else receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 13.5.sp))
            Text(blurb, color = if (selected) receiptsColors.chromeOn else receiptsColors.inkSoft, style = ReceiptsType.meta, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(18.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(if (selected) receiptsColors.pink else receiptsColors.paper)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
        )
    }
}

@Composable
private fun BudgetPick(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.medium))
            .background(if (selected) receiptsColors.yellow else receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = text }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) receiptsColors.chromeOn else receiptsColors.inkSoft, style = if (text.startsWith("₹")) ReceiptsType.amount.copy(fontSize = 13.sp) else ReceiptsType.bodyStrong.copy(fontSize = 12.sp), maxLines = 1) }
}
