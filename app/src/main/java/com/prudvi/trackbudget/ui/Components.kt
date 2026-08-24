package com.prudvi.trackbudget.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
@Composable
fun ReceiptLabel(text: String, modifier: Modifier = Modifier, color: Color = receiptsColors.fade) {
    Text(text.uppercase(), modifier, color = color, style = ReceiptsType.label)
}
@Composable
fun ReceiptDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(receiptsColors.rule))
}
fun Modifier.receiptPerforation(color: Color): Modifier = drawWithCache {
    val stroke = 1.5.dp.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    onDrawBehind {
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, pathEffect = effect)
    }
}
@Composable
fun ReceiptPerforation(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(ReceiptsSpace.x4).receiptPerforation(receiptsColors.ruleHard))
}

@Composable
fun ReceiptCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paperRaised)
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .padding(ReceiptsSpace.x4),
    ) { content() }
}

enum class ReceiptButtonStyle { INK, ULTRAMARINE, CHILLI, CHROME, OUTLINE, QUIET }

@Composable
fun ReceiptButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ReceiptButtonStyle = ReceiptButtonStyle.INK,
) {
    val colors = receiptsColors
    val background = when (style) {
        ReceiptButtonStyle.INK -> colors.ink
        ReceiptButtonStyle.ULTRAMARINE -> colors.ultramarine
        ReceiptButtonStyle.CHILLI -> colors.chilli
        ReceiptButtonStyle.CHROME -> colors.chrome
        ReceiptButtonStyle.OUTLINE, ReceiptButtonStyle.QUIET -> colors.paper
    }
    val foreground = when (style) {
        ReceiptButtonStyle.INK -> colors.paper
        ReceiptButtonStyle.ULTRAMARINE -> colors.ultramarineOn
        ReceiptButtonStyle.CHILLI -> colors.chilliOn
        ReceiptButtonStyle.CHROME -> colors.chromeOn
        ReceiptButtonStyle.OUTLINE -> colors.ink
        ReceiptButtonStyle.QUIET -> colors.fade
    }
    Box(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(if (enabled) background else colors.sunk)
            .then(if (style == ReceiptButtonStyle.OUTLINE) Modifier.border(1.dp, colors.ruleHard, RoundedCornerShape(ReceiptsRadius.small)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x4, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) foreground else colors.fade, style = ReceiptsType.heading, maxLines = 1)
    }
}

@Composable
fun ReceiptIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(ReceiptsSpace.x12).clip(RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = receiptsColors.fade, modifier = Modifier.size(ReceiptsSpace.x6))
    }
}

@Composable
fun ReceiptPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: ReceiptButtonStyle = ReceiptButtonStyle.OUTLINE,
    onClick: (() -> Unit)? = null,
) {
    val colors = receiptsColors
    val fill = when {
        selected -> colors.ink
        style == ReceiptButtonStyle.CHILLI -> colors.chilli
        style == ReceiptButtonStyle.ULTRAMARINE -> colors.ultramarine
        style == ReceiptButtonStyle.CHROME -> colors.chrome
        else -> colors.paper
    }
    val ink = when {
        selected -> colors.paper
        style == ReceiptButtonStyle.CHILLI -> colors.chilliOn
        style == ReceiptButtonStyle.ULTRAMARINE -> colors.ultramarineOn
        style == ReceiptButtonStyle.CHROME -> colors.chromeOn
        style == ReceiptButtonStyle.QUIET -> colors.fade
        else -> colors.ink
    }
    Text(
        text.uppercase(),
        modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(fill)
            .then(if (!selected && style in listOf(ReceiptButtonStyle.OUTLINE, ReceiptButtonStyle.QUIET)) Modifier.border(1.dp, colors.rule, RoundedCornerShape(ReceiptsRadius.pill)) else Modifier)
            .then(if (onClick == null) Modifier else Modifier.defaultMinSize(minHeight = ReceiptsSpace.x12).clickable(role = Role.Button, onClick = onClick))
            .semantics { this.selected = selected }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        color = ink,
        style = ReceiptsType.label,
        maxLines = 1,
    )
}

@Composable
fun ReceiptPaceRail(
    progress: Float,
    marker: Float,
    fill: Color,
    modifier: Modifier = Modifier,
    description: String,
) {
    Box(
        modifier.fillMaxWidth().height(ReceiptsSpace.x2)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.sunk)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(fill))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(marker.coerceIn(0.001f, 0.999f)))
            Box(Modifier.width(1.5.dp).fillMaxHeight().background(receiptsColors.chilli))
            Spacer(Modifier.weight((1f - marker).coerceIn(0.001f, 0.999f)))
        }
    }
}

@Composable
fun ReceiptCategoryMark(categoryId: String?, modifier: Modifier = Modifier) {
    val visual = categoryVisual(categoryId)
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(RoundedCornerShape(ReceiptsRadius.small)).background(visual.color)
            .semantics { contentDescription = "${categoryName(categoryId)} category" },
        contentAlignment = Alignment.Center,
    ) {
        Text(visual.mark, color = categoryMarkTextColor(visual.color), style = ReceiptsType.stamp.copy(fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 0.18.sp))
    }
}

@Composable
fun ReceiptRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val excluded = transaction.status == TransactionStatus.EXCLUDED
    val amountColor = if (transaction.direction == Direction.CREDIT) receiptsColors.ultramarine else receiptsColors.ink
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .alpha(if (excluded) 0.48f else 1f)
            .padding(vertical = ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        ReceiptCategoryMark(transaction.categoryId)
        Column(Modifier.weight(1f)) {
            Text(receiptMerchant(transaction), color = receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (excluded) TextDecoration.LineThrough else null)
            Text("${categoryName(transaction.categoryId)} · ${receiptTime(transaction.occurredAt)}", color = receiptsColors.fade, style = ReceiptsType.meta, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing == null) {
            Text(receiptSignedMoney(transaction), color = amountColor, style = ReceiptsType.amount, maxLines = 1)
        } else trailing()
    }
}

@Composable
fun ReceiptHeroAmount(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = receiptsColors.ink,
    textAlign: TextAlign = TextAlign.Start,
    loud: Boolean = true,
) {
    val styles = if (loud) listOf(ReceiptsType.hero, ReceiptsType.display, ReceiptsType.title) else listOf(ReceiptsType.display, ReceiptsType.title)
    var styleIndex by remember(text) { mutableIntStateOf(0) }
    Text(
        text,
        modifier,
        color = color,
        style = styles[styleIndex],
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && styleIndex < styles.lastIndex) styleIndex++
        },
    )
}

@Composable
fun ReceiptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.paperRaised)
            .border(1.dp, if (focused) receiptsColors.ultramarine else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x3),
        singleLine = singleLine,
        textStyle = ReceiptsType.body.copy(color = receiptsColors.ink),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(receiptsColors.ultramarine),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(placeholder, color = receiptsColors.fade, style = ReceiptsType.body)
                inner()
            }
        },
    )
}

@Composable
fun ReceiptEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = ReceiptsSpace.x8), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
        Text(title, color = receiptsColors.ink, style = ReceiptsType.heading)
        Text(body, color = receiptsColors.inkSoft, style = ReceiptsType.body)
    }
}

@Composable
fun ReceiptAmountPad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    noteLabel: String = "note",
    specialContentDescription: String = "Add note",
) {
    val haptic = LocalHapticFeedback.current
    val motionEnabled = rememberMotionEnabled()
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(noteLabel, "0", "⌫"))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x1)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x1)) {
                row.forEach { key ->
                    var pressed by remember { mutableStateOf(false) }
                    val scale = remember { Animatable(1f) }
                    LaunchedEffect(pressed, motionEnabled) {
                        if (pressed) {
                            if (motionEnabled) {
                                scale.animateTo(0.94f, tween(ReceiptsMotion.KEY / 2))
                                scale.animateTo(1f, tween(ReceiptsMotion.KEY / 2))
                            } else {
                                scale.snapTo(1f)
                            }
                            pressed = false
                        }
                    }
                    Box(
                        Modifier.weight(1f).height(ReceiptsSpace.x12).scale(scale.value)
                            .clip(RoundedCornerShape(ReceiptsRadius.small)).background(receiptsColors.paperRaised)
                            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                pressed = true
                                onKey(key)
                            }
                            .semantics { contentDescription = if (key == "⌫") "Backspace" else if (key == noteLabel) specialContentDescription else key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = if (key == noteLabel) receiptsColors.fade else receiptsColors.ink, style = if (key == noteLabel) ReceiptsType.label else ReceiptsType.heading)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

fun categoryName(id: String?): String = when (id) {
    "food" -> "Food"
    "groceries" -> "Groceries"
    "shopping" -> "Shopping"
    "transport" -> "Transport"
    "bills" -> "Bills"
    "transfers" -> "Transfers"
    "medical" -> "Medical"
    "travel" -> "Travel"
    "repayments" -> "Money in"
    "personal" -> "Personal"
    "services" -> "Services"
    "insurance" -> "Insurance"
    "entertainment" -> "Entertainment"
    "gaming" -> "Gaming"
    "smallshops" -> "Small shops"
    "rent" -> "Rent"
    "logistics" -> "Logistics"
    "subscription" -> "Subscription"
    "investment" -> "Investment"
    "fitness" -> "Fitness"
    "pet" -> "Pet"
    "cash" -> "Cash"
    "refund" -> "Refund"
    "income" -> "Money in"
    "misc" -> "Miscellaneous"
    else -> "Uncategorised"
}
