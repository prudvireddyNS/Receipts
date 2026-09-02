package com.prudvi.trackbudget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.EarnedStamp
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.StampDefinition
import com.prudvi.trackbudget.model.StampEngine
import java.time.LocalDate
import kotlin.math.ceil

private sealed interface GoalOverlay {
    data object AddGoal : GoalOverlay
    data class AddProgress(val goal: Goal) : GoalOverlay
    data class Delete(val goal: Goal) : GoalOverlay
    data class StampInfo(val stamp: StampDefinition, val earned: Boolean) : GoalOverlay
}

@Composable
fun GoalsScreen(
    goals: List<Goal>,
    earnedStamps: List<EarnedStamp>,
    receiptCount: Int,
    onAddGoal: (String, Long, Long?) -> Unit,
    onAddProgress: (String, Long) -> Unit,
    onDeleteGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlay by remember { mutableStateOf<GoalOverlay?>(null) }
    BackHandler(overlay is GoalOverlay) { overlay = null }

    Box(modifier.fillMaxSize().background(receiptsColors.paper)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ReceiptsSpace.screen,
                top = ReceiptsSpace.x6,
                end = ReceiptsSpace.screen,
                bottom = ReceiptsSpace.x16,
            ),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Goals",
                        Modifier.weight(1f).semantics { heading() },
                        color = receiptsColors.ink,
                        style = ReceiptsType.title,
                    )
                    ReceiptButton("Add goal", onClick = { overlay = GoalOverlay.AddGoal }, style = ReceiptButtonStyle.OUTLINE)
                }
            }
            if (goals.isEmpty()) {
                item {
                    ReceiptEmptyState(
                        "Nothing saved for yet. What are you after?",
                        "Save toward something specific — add a target and this fills in like a ticket, a little at a time.",
                    )
                }
            } else {
                itemsIndexed(goals, key = { _, goal -> goal.id }) { index, goal ->
                    GoalTicketStub(
                        goal,
                        perforated = index == 0,
                        onAddProgress = { overlay = GoalOverlay.AddProgress(goal) },
                        onDelete = { overlay = GoalOverlay.Delete(goal) },
                    )
                }
            }
            item {
                ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x2))
                StampsGrid(earnedStamps, receiptCount) { stamp, earned -> overlay = GoalOverlay.StampInfo(stamp, earned) }
            }
        }

        when (val current = overlay) {
            GoalOverlay.AddGoal -> AddGoalOverlay(onClose = { overlay = null }) { name, target, targetDay ->
                onAddGoal(name, target, targetDay)
                overlay = null
            }
            is GoalOverlay.AddProgress -> AddProgressOverlay(current.goal, onClose = { overlay = null }) { amount ->
                onAddProgress(current.goal.id, amount)
                overlay = null
            }
            is GoalOverlay.Delete -> DeleteGoalOverlay(current.goal, onClose = { overlay = null }) {
                onDeleteGoal(current.goal.id)
                overlay = null
            }
            is GoalOverlay.StampInfo -> StampInfoOverlay(current.stamp, current.earned, onClose = { overlay = null })
            null -> Unit
        }
    }
}

@Composable
fun GoalTicketStub(goal: Goal, perforated: Boolean, onAddProgress: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val progressText = receiptPercent(goal.progress)
    val guidance = monthlyGuidance(goal)
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val animatedProgress = remember(goal.id) { Animatable(goal.progress) }
    var previousProgress by remember(goal.id) { mutableStateOf(goal.progress) }
    LaunchedEffect(goal.progress, motionEnabled) {
        val milestone = listOf(.25f, .5f, .75f, 1f).any { previousProgress < it && goal.progress >= it }
        if (!motionEnabled) {
            animatedProgress.snapTo(goal.progress)
        } else if (milestone) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            animatedProgress.animateTo((goal.progress + .04f).coerceAtMost(1.04f), tween(ReceiptsMotion.GOAL / 2))
            animatedProgress.animateTo(goal.progress, tween(ReceiptsMotion.GOAL / 2))
        } else {
            animatedProgress.animateTo(goal.progress, tween(ReceiptsMotion.GOAL))
        }
        previousProgress = goal.progress
    }
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paperRaised)
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .semantics {
                contentDescription = "Goal ${goal.name}, ${receiptMoney(goal.savedMinor)} saved of ${receiptMoney(goal.targetMinor)}, $progressText saved. $guidance"
            },
    ) {
        Column(Modifier.padding(ReceiptsSpace.x4), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(goal.name, Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ReceiptLabel(progressText)
            }
            Text(receiptMoney(goal.savedMinor), color = receiptsColors.ink, style = ReceiptsType.display, maxLines = 1)
            Text("of ${receiptMoney(goal.targetMinor)}", color = receiptsColors.fade, style = ReceiptsType.label)
            GoalProgressRail(animatedProgress.value, "${goal.name} progress, $progressText")
            goal.targetEpochDay?.let { day ->
                Text("by ${receiptShortDate(LocalDate.ofEpochDay(day))}", color = receiptsColors.fade, style = ReceiptsType.meta)
            }
        }
        if (perforated) ReceiptPerforation(Modifier.padding(horizontal = ReceiptsSpace.x4))
        else ReceiptDivider(Modifier.padding(horizontal = ReceiptsSpace.x4))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ReceiptsSpace.x4, vertical = ReceiptsSpace.x2),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
        ) {
            Text(guidance, color = receiptsColors.fade, style = ReceiptsType.meta)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                ReceiptPill("Remove", Modifier.weight(1f), style = ReceiptButtonStyle.QUIET, onClick = onDelete)
                ReceiptButton("Add ₹", onAddProgress, Modifier.weight(1f), style = ReceiptButtonStyle.OUTLINE)
            }
        }
    }
}

@Composable
private fun GoalProgressRail(progress: Float, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(ReceiptsSpace.x2)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.sunk)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(receiptsColors.chrome))
    }
}

@Composable
private fun StampsGrid(earnedStamps: List<EarnedStamp>, receiptCount: Int, onStampClick: (StampDefinition, Boolean) -> Unit) {
    val earnedIds = earnedStamps.mapTo(mutableSetOf()) { it.id }
    Column(Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x4), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReceiptLabel("Stamps", Modifier.weight(1f))
            ReceiptLabel("${StampEngine.definitions.count { it.id in earnedIds }} / ${StampEngine.definitions.size}")
        }
        Text("$receiptCount receipts collected", color = receiptsColors.fade, style = ReceiptsType.meta)
        Text("Tap a stamp to see what it's for.", color = receiptsColors.fade, style = ReceiptsType.meta)
        StampEngine.definitions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                row.forEach { stamp ->
                    val earned = stamp.id in earnedIds
                    StampFace(stamp, earned = earned, modifier = Modifier.weight(1f), onClick = { onStampClick(stamp, earned) })
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StampFace(stamp: StampDefinition, earned: Boolean, modifier: Modifier = Modifier, large: Boolean = false, onClick: (() -> Unit)? = null) {
    val borderColor = if (earned) receiptsColors.chilli else receiptsColors.ruleHard
    val textColor = if (earned) receiptsColors.chilli else receiptsColors.fade
    val shape = RoundedCornerShape(ReceiptsRadius.small)
    Column(
        modifier.graphicsLayer(rotationZ = stampRotationDegrees(stamp.id))
            .heightIn(min = if (large) ReceiptsSpace.x16 + ReceiptsSpace.x8 else ReceiptsSpace.x16)
            .clip(shape)
            .background(receiptsColors.paper)
            .then(if (earned) Modifier.border(1.5.dp, borderColor, shape) else Modifier.dashedStampBorder(borderColor))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(ReceiptsSpace.x2)
            .semantics {
                contentDescription = if (earned) {
                    "Earned stamp ${stamp.title}. ${stamp.description}"
                } else {
                    "Locked stamp ${stamp.title}. ${stamp.description}"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val centeredLineHeight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
        Text(
            stamp.title.replace(' ', '\n'),
            Modifier.offset(x = centeredTrackingOffset(ReceiptsType.stamp.letterSpacing)),
            color = textColor,
            style = ReceiptsType.stamp.copy(lineHeightStyle = centeredLineHeight),
            textAlign = TextAlign.Center,
            maxLines = if (large) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (large) {
            Text(
                stamp.description,
                Modifier.offset(x = centeredTrackingOffset(ReceiptsType.label.letterSpacing)),
                color = textColor,
                style = ReceiptsType.label.copy(lineHeightStyle = centeredLineHeight),
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun stampRotationDegrees(id: String): Float {
    val positiveHash = id.fold(0) { acc, char -> acc * 31 + char.code } and Int.MAX_VALUE
    return (positiveHash % 9 - 4).toFloat()
}

@Composable
private fun AddGoalOverlay(onClose: () -> Unit, onSave: (String, Long, Long?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("4") }
    val targetMinor = integerRupeesToMinor(target)
    val monthsOut = months.toLongOrNull()
    val canSave = name.isBlank() == false && targetMinor > 0
    GoalFormOverlay("New goal", onClose) {
        ReceiptTextField(name, { name = it }, "Name", Modifier.semantics { contentDescription = "Goal name" })
        ReceiptTextField(target, { target = it.filter(Char::isDigit) }, "Target ₹", Modifier.semantics { contentDescription = "Goal target rupees" }, KeyboardOptions(keyboardType = KeyboardType.Number))
        ReceiptTextField(months, { months = it.filter(Char::isDigit) }, "Months to target", Modifier.semantics { contentDescription = "Months to target" }, KeyboardOptions(keyboardType = KeyboardType.Number))
        ReceiptButton("Save", enabled = canSave, onClick = {
            onSave(name, targetMinor, monthsOut?.let { LocalDate.now().plusMonths(it).toEpochDay() })
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddProgressOverlay(goal: Goal, onClose: () -> Unit, onSave: (Long) -> Unit) {
    var amount by rememberSaveable(goal.id) { mutableStateOf("") }
    val amountMinor = integerRupeesToMinor(amount)
    GoalFormOverlay("Add ₹", onClose) {
        Text(goal.name, color = receiptsColors.ink, style = ReceiptsType.heading)
        Text("Saved ${receiptMoney(goal.savedMinor)} of ${receiptMoney(goal.targetMinor)}", color = receiptsColors.fade, style = ReceiptsType.meta)
        ReceiptTextField(amount, { amount = it.filter(Char::isDigit) }, "Amount ₹", Modifier.semantics { contentDescription = "Progress amount rupees" }, KeyboardOptions(keyboardType = KeyboardType.Number))
        ReceiptButton("Save", enabled = amountMinor > 0, onClick = { onSave(amountMinor) }, modifier = Modifier.fillMaxWidth(), style = ReceiptButtonStyle.CHROME)
    }
}

@Composable
private fun DeleteGoalOverlay(goal: Goal, onClose: () -> Unit, onDelete: () -> Unit) {
    GoalFormOverlay("Remove goal", onClose) {
        Text("Remove ${goal.name}? Saved progress is removed from this device.", color = receiptsColors.inkSoft, style = ReceiptsType.body)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
            ReceiptButton("Cancel", onClose, Modifier.weight(1f), style = ReceiptButtonStyle.OUTLINE)
            ReceiptButton("Remove", onDelete, Modifier.weight(1f), style = ReceiptButtonStyle.CHILLI)
        }
    }
}

@Composable
private fun StampInfoOverlay(stamp: StampDefinition, earned: Boolean, onClose: () -> Unit) {
    GoalFormOverlay(stamp.title, onClose) {
        StampFace(stamp, earned = earned, modifier = Modifier.fillMaxWidth(), large = true)
        Text(
            if (earned) "Earned" else "Not yet — keep going",
            color = if (earned) receiptsColors.chilli else receiptsColors.fade,
            style = ReceiptsType.label,
        )
    }
}

@Composable
private fun GoalFormOverlay(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(receiptsColors.scrim).imePadding(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(ReceiptsRadius.small))
                .background(receiptsColors.paper)
                .border(1.dp, receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small))
                .padding(ReceiptsSpace.screen),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, color = receiptsColors.ink, style = ReceiptsType.title)
                ReceiptButton("Close", onClose, style = ReceiptButtonStyle.QUIET)
            }
            content()
        }
    }
}

private fun monthlyGuidance(goal: Goal): String {
    val remaining = (goal.targetMinor - goal.savedMinor).coerceAtLeast(0)
    if (remaining == 0L) return "Complete"
    val targetDay = goal.targetEpochDay ?: return "Add a date for monthly guidance"
    val days = (targetDay - LocalDate.now().toEpochDay()).coerceAtLeast(1)
    val months = ceil(days / 30f).toLong().coerceAtLeast(1)
    return "${receiptMoney((remaining + months - 1) / months)}/month to make it"
}

fun Modifier.dashedStampBorder(color: Color): Modifier = drawWithCache {
    val strokeWidth = 1.5.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(ReceiptsSpace.x1.toPx(), ReceiptsSpace.x1.toPx()))
    val corner = CornerRadius(ReceiptsRadius.small.toPx())
    onDrawWithContent {
        drawContent()
        drawRoundRect(color = color, cornerRadius = corner, style = Stroke(width = strokeWidth, pathEffect = dash))
    }
}
