package com.prudvi.trackbudget.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus

fun Modifier.mockShadow(
    radius: Dp,
    offsetX: Dp = 3.dp,
    offsetY: Dp = 3.dp,
    color: Color = Color(0xFF14121F),
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
        size = size,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
    )
}

@Composable
fun ReceiptLabel(text: String, modifier: Modifier = Modifier, color: Color = receiptsColors.fade) {
    Text(text.uppercase(), modifier, color = color, style = ReceiptsType.label)
}

@Composable
fun ReceiptDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(ReceiptsStroke.width).background(receiptsColors.ink))
}

fun Modifier.receiptPerforation(color: Color): Modifier = drawBehind {
    val stroke = ReceiptsStroke.width.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
    drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, pathEffect = effect)
}

@Composable
fun ReceiptPerforation(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(16.dp).receiptPerforation(receiptsColors.ink))
}

@Composable
fun ReceiptCard(
    modifier: Modifier = Modifier,
    background: Color = receiptsColors.paper,
    radius: Dp = ReceiptsRadius.large,
    shadow: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (shadow) Modifier.mockShadow(radius, 3.dp, 3.dp, receiptsColors.ink) else Modifier)
            .clip(shape)
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, shape)
            .padding(13.dp),
    ) { content() }
}

enum class ReceiptButtonStyle { INK, ULTRAMARINE, CHILLI, CHROME, OUTLINE, QUIET }

@Composable
fun ReceiptButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ReceiptButtonStyle = ReceiptButtonStyle.CHROME,
    radius: Dp = ReceiptsRadius.medium,
    shadowOffset: Dp = 3.dp,
    textStyle: androidx.compose.ui.text.TextStyle = ReceiptsType.button,
) {
    val colors = receiptsColors
    val background = when (style) {
        ReceiptButtonStyle.INK -> colors.ink
        ReceiptButtonStyle.ULTRAMARINE -> colors.purple
        ReceiptButtonStyle.CHILLI -> colors.pink
        ReceiptButtonStyle.CHROME -> colors.yellow
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
    val shape = RoundedCornerShape(radius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "button-press",
    )
    Box(
        modifier
            .scale(scale)
            .defaultMinSize(minHeight = 48.dp)
            .then(if (style in listOf(ReceiptButtonStyle.CHROME, ReceiptButtonStyle.ULTRAMARINE, ReceiptButtonStyle.CHILLI)) Modifier.mockShadow(radius, shadowOffset, shadowOffset, colors.ink) else Modifier)
            .clip(shape)
            .background(if (enabled) background else colors.warm)
            .border(ReceiptsStroke.width, colors.ink, shape)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) foreground else colors.fade,
            style = textStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ReceiptIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = receiptsColors.cyan,
    size: Dp = 44.dp,
) {
    val visualSize = minOf(size, 32.dp)
    Box(
        modifier.size(size).clip(RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(visualSize)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(background)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = receiptsColors.ink, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
fun centeredTrackingOffset(letterSpacing: androidx.compose.ui.unit.TextUnit): Dp {
    if (letterSpacing.isUnspecified) return 0.dp
    val density = LocalDensity.current
    return with(density) { (letterSpacing.toPx() / 2f).toDp() }
}

@Composable
fun ReceiptPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: ReceiptButtonStyle = ReceiptButtonStyle.OUTLINE,
    onClick: (() -> Unit)? = null,
) {
    val fill = when {
        selected -> receiptsColors.yellow
        style == ReceiptButtonStyle.CHILLI -> receiptsColors.pink
        style == ReceiptButtonStyle.ULTRAMARINE -> receiptsColors.purple
        style == ReceiptButtonStyle.CHROME -> receiptsColors.yellow
        else -> receiptsColors.paper
    }
    val ink = when {
        selected -> receiptsColors.chromeOn
        style == ReceiptButtonStyle.CHILLI -> receiptsColors.chilliOn
        style == ReceiptButtonStyle.ULTRAMARINE -> receiptsColors.ultramarineOn
        style == ReceiptButtonStyle.CHROME -> receiptsColors.chromeOn
        else -> receiptsColors.fade
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pill-press",
    )
    Box(
        modifier
            .then(if (onClick == null) Modifier else Modifier.scale(scale).defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, interactionSource = interactionSource, indication = null, onClick = onClick))
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(fill)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text.uppercase(), color = ink, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1)
        }
    }
}

@Composable
fun ReceiptPaceRail(
    progress: Float,
    fill: Color,
    modifier: Modifier = Modifier,
    description: String,
) {
    val motionEnabled = rememberMotionEnabled()
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (motionEnabled) spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessHigh),
        label = "pace-rail",
    )
    Box(
        modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(
            Modifier.fillMaxWidth(animatedProgress).fillMaxHeight()
                .background(fill)
                .border(BorderStroke(ReceiptsStroke.width, receiptsColors.ink)),
        )
    }
}

@Composable
fun ReceiptCategoryMark(categoryId: String?, modifier: Modifier = Modifier, size: Dp = 28.dp, radius: Dp = 9.dp) {
    val visual = categoryVisual(categoryId)
    Box(
        modifier.size(size)
            .clip(RoundedCornerShape(radius))
            .background(visual.color)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(radius))
            .semantics { contentDescription = "${categoryName(categoryId)} category" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(categoryId), contentDescription = null, tint = categoryMarkTextColor(visual.color), modifier = Modifier.size(size * 0.52f))
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
    val amountColor = if (transaction.direction == Direction.CREDIT) receiptsColors.purple else receiptsColors.ink
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "row-press",
    )
    Row(
        modifier.fillMaxWidth().scale(scale).defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.medium))
            .background(if (transaction.direction == Direction.CREDIT) receiptsColors.ultramarineTint else receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, interactionSource = interactionSource, indication = null, onClick = onClick))
            .alpha(if (excluded) 0.48f else 1f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReceiptCategoryMark(transaction.categoryId, size = 26.dp, radius = 8.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(receiptMerchant(transaction), color = receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (excluded) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f, fill = false))
                if (transaction.committed) ReceiptCommittedTag()
            }
            Text("${categoryName(transaction.categoryId)} · ${receiptTime(transaction.occurredAt)}", color = receiptsColors.fade, style = ReceiptsType.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing == null) {
            Text(receiptSignedMoney(transaction), color = amountColor, style = ReceiptsType.amount, maxLines = 1)
        } else trailing()
    }
}

@Composable
fun ReceiptCommittedTag(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(ReceiptsRadius.tiny))
            .background(receiptsColors.warm)
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.tiny))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("FIXED", color = receiptsColors.fade, style = ReceiptsType.stamp.copy(fontSize = 8.sp, letterSpacing = 0.8.sp))
    }
}

@Composable
fun ReceiptTogglePill(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val motionEnabled = rememberMotionEnabled()
    val spec: androidx.compose.animation.core.AnimationSpec<Color> = if (motionEnabled) tween(ReceiptsMotion.KEY) else tween(0)
    val fill by animateColorAsState(if (checked) receiptsColors.mint else receiptsColors.paper, spec, label = "toggle-fill")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toggle-press",
    )
    Box(
        modifier.scale(scale).defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Switch, interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { role = Role.Switch; stateDescription = if (checked) "On" else "Off" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp).clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(fill)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (checked) "On" else "Off", color = if (checked) receiptsColors.ink else receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 0.sp)) }
    }
}

/** A plain square checkbox — ink border, filled with a check mark when ticked. */
@Composable
fun ReceiptCheckbox(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val motionEnabled = rememberMotionEnabled()
    val fill by animateColorAsState(if (checked) receiptsColors.ink else receiptsColors.paper, label = "checkbox-fill")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkbox-press",
    )
    Box(
        modifier.scale(scale).size(44.dp)
            .clickable(role = Role.Checkbox, interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
            .semantics { role = Role.Checkbox; stateDescription = if (checked) "Checked" else "Unchecked" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.tiny))
                .background(fill)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.tiny)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = receiptsColors.paper, modifier = Modifier.size(17.dp))
        }
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
        onTextLayout = { result -> if (result.hasVisualOverflow && styleIndex < styles.lastIndex) styleIndex++ },
    )
}

/** Hero amount that counts up/down toward [targetMinor] instead of snapping, for a livelier feel. */
@Composable
fun ReceiptAnimatedHeroAmount(
    targetMinor: Long,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = receiptsColors.ink,
    textAlign: TextAlign = TextAlign.Start,
    loud: Boolean = true,
) {
    val motionEnabled = rememberMotionEnabled()
    val animated = remember { Animatable(targetMinor.toFloat()) }
    LaunchedEffect(targetMinor, motionEnabled) {
        if (motionEnabled) {
            animated.animateTo(targetMinor.toFloat(), tween(ReceiptsMotion.HERO))
        } else {
            animated.snapTo(targetMinor.toFloat())
        }
    }
    ReceiptHeroAmount(format(animated.value.toLong()), modifier, color, textAlign, loud)
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
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.medium))
            .background(receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = placeholder }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        singleLine = singleLine,
        textStyle = ReceiptsType.body.copy(color = receiptsColors.ink),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(if (focused) receiptsColors.purple else receiptsColors.ink),
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
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(ReceiptsRadius.hero))
            .background(receiptsColors.cyan)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.hero))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = receiptsColors.ink, style = ReceiptsType.heading, textAlign = TextAlign.Center)
        Text(body, color = receiptsColors.inkSoft, style = ReceiptsType.body, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptAmountPad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    noteLabel: String = "to",
    specialContentDescription: String = "Edit merchant or payee",
) {
    val haptic = LocalHapticFeedback.current
    val motionEnabled = rememberMotionEnabled()
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", noteLabel, "0", "del")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { key ->
                    var pressed by remember { mutableStateOf(false) }
                    val scale = remember { Animatable(1f) }
                    LaunchedEffect(pressed, motionEnabled) {
                        if (pressed) {
                            if (motionEnabled) {
                                scale.animateTo(0.94f, tween(ReceiptsMotion.KEY / 2))
                                scale.animateTo(1f, tween(ReceiptsMotion.KEY / 2))
                            } else scale.snapTo(1f)
                            pressed = false
                        }
                    }
                    val display = if (key == "del") "⌫" else key
                    val special = key == noteLabel || key == "del"
                    Box(
                        Modifier.weight(1f).height(50.dp).scale(scale.value)
                            .clip(RoundedCornerShape(ReceiptsRadius.medium))
                            .background(if (special) receiptsColors.warm else receiptsColors.paper)
                            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
                            .combinedClickable(
                                role = Role.Button,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    pressed = true
                                    onKey(key)
                                },
                                onLongClick = if (key == "0") ({
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pressed = true
                                    onKey(".")
                                }) else null,
                            )
                            .semantics {
                                contentDescription = when (key) {
                                    "del" -> "Delete last digit"
                                    noteLabel -> specialContentDescription
                                    "0" -> "0. Long press for decimal point"
                                    else -> display
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(display, color = if (key == noteLabel) receiptsColors.fade else receiptsColors.ink, style = if (key == noteLabel) ReceiptsType.label.copy(fontSize = 13.sp, letterSpacing = 0.sp) else ReceiptsType.heading.copy(fontSize = 19.sp))
                    }
                }
            }
        }
    }
}

/**
 * Staggered entrance: the element fades up into place, [index] steps behind its siblings.
 * Re-runs whenever [key] changes, so a screen re-entry replays the motion.
 */
@Composable
fun Modifier.receiptEnter(index: Int = 0, key: Any? = Unit, lift: androidx.compose.ui.unit.Dp = 26.dp): Modifier {
    val motionEnabled = rememberMotionEnabled()
    val progress = remember(key, motionEnabled) { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(key, motionEnabled) {
        if (!motionEnabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(index.toLong() * ReceiptsMotion.STAGGER)
        progress.animateTo(1f, tween(ReceiptsMotion.ENTER, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift.toPx()
    }
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

fun categoryName(id: String?): String = when (id) {
    "food" -> "Food"
    "groceries" -> "Groceries"
    "shopping" -> "Shopping"
    "transport" -> "Transport"
    "travel" -> "Travel"
    "bills" -> "Bills"
    "transfers" -> "Transfers"
    "repayments" -> "Repayments"
    "income" -> "Income"
    "medical" -> "Medical"
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
    "misc" -> "Misc"
    else -> "Uncategorised"
}

fun categoryIcon(id: String?): ImageVector = when (id) {
    "food" -> PhosphorBold.Hamburger
    "groceries" -> PhosphorBold.ShoppingCart
    "shopping" -> PhosphorBold.ShoppingBag
    "transport" -> PhosphorBold.Bus
    "travel" -> Icons.Filled.Flight
    "bills" -> Icons.Filled.ReceiptLong
    "transfers" -> Icons.Filled.SwapHoriz
    "medical" -> Icons.Filled.LocalHospital
    "personal" -> Icons.Filled.Person
    "services" -> Icons.Filled.Build
    "insurance" -> Icons.Filled.Shield
    "entertainment" -> Icons.Filled.Movie
    "gaming" -> Icons.Filled.SportsEsports
    "smallshops" -> Icons.Filled.Storefront
    "rent" -> Icons.Filled.Home
    "logistics" -> Icons.Filled.LocalShipping
    "subscription" -> Icons.Filled.Autorenew
    "investment" -> Icons.Filled.TrendingUp
    "fitness" -> Icons.Filled.FitnessCenter
    "pet" -> Icons.Filled.Pets
    "cash" -> Icons.Filled.Payments
    "repayments" -> Icons.Filled.Replay
    "refund" -> Icons.Filled.Undo
    "income" -> PhosphorBold.ArrowDownLeft
    else -> Icons.Filled.Category
}

private object PhosphorBold {
    val Hamburger = icon("M227.9,152.72l-39.7,14.44-35.74-14.3a12,12,0,0,0-8.92,0L108,167.08,72.46,152.86a12,12,0,0,0-8.56-.14l-44,16a12,12,0,0,0,8.2,22.56l8.12-2.95A44.06,44.06,0,0,0,80,228h96a44.05,44.05,0,0,0,44-44v-2.87l16.1-5.85a12,12,0,0,0-8.2-22.56ZM176,204H80a20,20,0,0,1-20-20v-4.32l7.8-2.84,35.74,14.3a12,12,0,0,0,8.92,0L148,176.92l35.54,14.22a12,12,0,0,0,8.56.14l2.89-1.06A20,20,0,0,1,176,204ZM12,128a12,12,0,0,1,12-12H232a12,12,0,0,1,0,24H24A12,12,0,0,1,12,128Zm36.2-24H207.8a20.36,20.36,0,0,0,16.38-8.29,19.59,19.59,0,0,0,2.88-17.65C216.12,43.88,175.39,20,128,20S39.89,43.87,28.94,78.05A19.56,19.56,0,0,0,31.82,95.7,20.32,20.32,0,0,0,48.2,104ZM128,44c33.7,0,63.61,14.85,74,36H54C64.4,58.85,94.31,44,128,44Z")
    val ShoppingCart = icon("M233.21,56.31A12,12,0,0,0,224,52H66L60.53,21.85A12,12,0,0,0,48.73,12H24a12,12,0,0,0,0,24H38.71L63.62,173a28,28,0,0,0,4.07,10.21A32,32,0,1,0,123,196h34a32,32,0,1,0,31-24H91.17a4,4,0,0,1-3.93-3.28L84.92,156H196.1a28,28,0,0,0,27.55-23l12.16-66.86A12,12,0,0,0,233.21,56.31ZM100,204a8,8,0,1,1-8-8A8,8,0,0,1,100,204Zm88,8a8,8,0,1,1,8-8A8,8,0,0,1,188,212Zm12-83.28A4,4,0,0,1,196.1,132H80.56L70.38,76H209.62Z")
    val ShoppingBag = icon("M216,36H40A20,20,0,0,0,20,56V200a20,20,0,0,0,20,20H216a20,20,0,0,0,20-20V56A20,20,0,0,0,216,36Zm-4,160H44V60H212ZM76,88a12,12,0,0,1,24,0,28,28,0,0,0,56,0,12,12,0,0,1,24,0A52,52,0,0,1,76,88Z")
    val Bus = icon("M184,28H72A36,36,0,0,0,36,64V208a20,20,0,0,0,20,20H84a20,20,0,0,0,20-20V192h48v16a20,20,0,0,0,20,20h28a20,20,0,0,0,20-20V64A36,36,0,0,0,184,28ZM60,168V112H196v56ZM72,52H184a12,12,0,0,1,12,12V88H60V64A12,12,0,0,1,72,52Zm8,152H60V192H80Zm96,0V192h20v12Zm-68-64a16,16,0,1,1-16-16A16,16,0,0,1,108,140Zm72,0a16,16,0,1,1-16-16A16,16,0,0,1,180,140Zm76-60v24a12,12,0,0,1-24,0V80a12,12,0,0,1,24,0ZM24,80v24a12,12,0,0,1-24,0V80a12,12,0,0,1,24,0Z")
    val ArrowDownLeft = icon("M200.49,72.48,93,180h75a12,12,0,0,1,0,24H64a12,12,0,0,1-12-12V88a12,12,0,0,1,24,0v75L183.51,55.51a12,12,0,0,1,17,17Z")

    private fun icon(path: String): ImageVector = ImageVector.Builder(
        name = path.take(16),
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()
}
