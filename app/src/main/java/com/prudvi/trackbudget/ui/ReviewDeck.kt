package com.prudvi.trackbudget.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prudvi.trackbudget.model.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
private val ReviewStatuses = setOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE)
private enum class ReviewAction { IGNORE, REFUND, KEEP }
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewDeck(transactions: List<Transaction>, onClose: () -> Unit, onSave: (Transaction) -> Unit, onLearn: (String, String, Direction) -> Unit, modifier: Modifier = Modifier) {
    val queue = remember(transactions) { transactions.filter { it.status in ReviewStatuses } }
    val top = queue.firstOrNull()
    var showCategories by rememberSaveable(top?.id) { mutableStateOf(false) }
    var learnRule by rememberSaveable(top?.id) { mutableStateOf(top?.let { canLearnMerchant(it.merchant) } == true) }
    var showAllCategories by rememberSaveable(top?.id) { mutableStateOf(false) }
    var amountDigits by rememberSaveable(top?.id) { mutableStateOf(top?.amountMinor?.takeIf { it > 0 }?.div(100)?.toString().orEmpty()) }
    var resolvedDirection by rememberSaveable(top?.id) { mutableStateOf(top?.let(::defaultDirection) ?: Direction.DEBIT) }
    var selectedCategory by rememberSaveable(top?.id) { mutableStateOf(top?.let(::defaultCategoryId) ?: "misc") }
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val xAnim = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(top?.id) {
        xAnim.snapTo(0f)
        dragging = false
        dragX = 0f
    }
    fun currentAmountMinor(): Long = if (top?.status == TransactionStatus.UNPARSEABLE) integerRupeesToMinor(amountDigits) else top?.amountMinor ?: 0L
    fun canResolve(action: ReviewAction): Boolean {
        if (top == null) return false
        if (action == ReviewAction.IGNORE) return true
        return currentAmountMinor() > 0L && (action != ReviewAction.REFUND || top.direction == Direction.CREDIT || top.status == TransactionStatus.NEEDS_RESOLUTION || top.status == TransactionStatus.UNPARSEABLE)
    }
    fun apply(action: ReviewAction) {
        val transaction = top ?: return
        val amountMinor = currentAmountMinor()
        when (action) {
            ReviewAction.IGNORE -> onSave(transaction.copy(status = TransactionStatus.EXCLUDED))
            ReviewAction.REFUND -> {
                if (amountMinor <= 0L) return; if (learnRule && canLearnMerchant(transaction.merchant)) onLearn(transaction.merchant, "refund", Direction.CREDIT)
                val resolved = transaction.copy(amountMinor = amountMinor, direction = Direction.CREDIT, categoryId = "refund")
                val match = findRefundCandidate(transactions, resolved)
                onSave(
                    resolved.copy(
                        refundOfId = match?.id,
                        status = TransactionStatus.CONFIRMED,
                    ),
                )
            }
            ReviewAction.KEEP -> {
                if (amountMinor <= 0L) return
                val categoryId = selectedCategory.ifBlank { if (resolvedDirection == Direction.CREDIT) "income" else "misc" }
                if (learnRule && canLearnMerchant(transaction.merchant)) onLearn(transaction.merchant, categoryId, resolvedDirection)
                onSave(
                    transaction.copy(
                        amountMinor = amountMinor,
                        direction = resolvedDirection,
                        categoryId = categoryId,
                        status = TransactionStatus.CONFIRMED,
                    ),
                )
            }
        }
    }
    fun settle(action: ReviewAction?, width: Float, start: Float) {
        scope.launch {
            xAnim.stop()
            xAnim.snapTo(start)
            dragging = false
            dragX = 0f
            if (action == null || !canResolve(action)) {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                if (motionEnabled) xAnim.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 600f)) else xAnim.snapTo(0f)
                return@launch
            }
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            val target = if (action == ReviewAction.IGNORE) -width * 1.2f else width * 1.2f
            if (motionEnabled) xAnim.animateTo(target, tween(240, easing = LinearOutSlowInEasing)) else xAnim.snapTo(target)
            apply(action)
            xAnim.snapTo(0f)
        }
    }
    Column(
        modifier.fillMaxSize()
            .background(receiptsColors.paper)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .imePadding()
            .padding(horizontal = ReceiptsSpace.screen, vertical = ReceiptsSpace.x4),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Review", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
            if (queue.isNotEmpty()) Text("${queue.size} left", color = receiptsColors.fade, style = ReceiptsType.label)
            CloseTextButton("Close review", onClose)
        }
        if (top == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing needs you. Clean.", color = receiptsColors.ink, style = ReceiptsType.heading)
            }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x4)) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val offsetX = if (dragging) dragX else xAnim.value
                if (queue.size > 2) StackPaper(Modifier.padding(start = ReceiptsSpace.x4, top = ReceiptsSpace.x4))
                if (queue.size > 1) StackPaper(Modifier.padding(start = ReceiptsSpace.x2, top = ReceiptsSpace.x2))
                ReviewCard(
                    transaction = top,
                    amountMinor = currentAmountMinor(),
                    direction = resolvedDirection,
                    selectedCategory = selectedCategory,
                    showCategories = showCategories,
                    showAllCategories = showAllCategories,
                    learnRule = learnRule,
                    onCategoryTap = { showCategories = !showCategories },
                    onCategory = { selectedCategory = it },
                    onMoreCategories = { showAllCategories = true },
                    onLearnRule = { learnRule = it },
                    onDirection = {
                        resolvedDirection = it
                        selectedCategory = if (it == Direction.CREDIT) "income" else "misc"
                    },
                    modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
                        .semantics {
                            customActions = buildList {
                                add(CustomAccessibilityAction("Keep receipt") { settle(ReviewAction.KEEP, widthPx, offsetX); true })
                                add(CustomAccessibilityAction("Ignore receipt") { settle(ReviewAction.IGNORE, widthPx, offsetX); true })
                                if (canResolve(ReviewAction.REFUND)) add(CustomAccessibilityAction("Mark as refund") { settle(ReviewAction.REFUND, widthPx, offsetX); true })
                            }
                        }
                        .pointerInput(top.id, widthPx, amountDigits, selectedCategory, resolvedDirection) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragX = xAnim.value
                                    scope.launch { xAnim.stop() }
                                },
                                onDragCancel = { settle(null, widthPx, dragX) },
                                onDragEnd = {
                                    val threshold = widthPx * 0.32f
                                    val action = when {
                                        dragX > threshold -> ReviewAction.KEEP
                                        dragX < -threshold -> ReviewAction.IGNORE
                                        else -> null
                                    }
                                    settle(action, widthPx, dragX)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragX += dragAmount.x
                                },
                            )
                        },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x4), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                ReviewActionButton("← Ignore", ReviewAction.IGNORE, enabled = true, modifier = Modifier.weight(1f)) { settle(ReviewAction.IGNORE, 1_000f, 0f) }
                ReviewActionButton("↑ Refund", ReviewAction.REFUND, enabled = canResolve(ReviewAction.REFUND), modifier = Modifier.weight(1f)) { settle(ReviewAction.REFUND, 1_000f, 0f) }
                ReviewActionButton("Keep →", ReviewAction.KEEP, enabled = canResolve(ReviewAction.KEEP), modifier = Modifier.weight(1f)) { settle(ReviewAction.KEEP, 1_000f, 0f) }
            }
            Text(
                "Change the category by tapping it",
                Modifier.fillMaxWidth().padding(top = ReceiptsSpace.x3),
                color = receiptsColors.fade,
                style = ReceiptsType.meta,
            )
        }
        if (top.status == TransactionStatus.UNPARSEABLE) {
            ReceiptAmountPad(
                onKey = { key ->
                    when {
                        key == "⌫" -> amountDigits = amountDigits.dropLast(1)
                        key.all(Char::isDigit) && amountDigits.length < 8 -> amountDigits = (amountDigits + key).trimStart('0')
                        else -> {
                            resolvedDirection = if (resolvedDirection == Direction.DEBIT) Direction.CREDIT else Direction.DEBIT
                            selectedCategory = if (resolvedDirection == Direction.CREDIT) "income" else "misc"
                        }
                    }
                },
                noteLabel = if (resolvedDirection == Direction.DEBIT) "in" else "out",
                specialContentDescription = if (resolvedDirection == Direction.DEBIT) "Switch to money in" else "Switch to spent",
                modifier = Modifier.padding(top = ReceiptsSpace.x3),
            )
        }
    }
}
@Composable
private fun StackPaper(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = ReceiptsSpace.x16 * 5f)
            .background(receiptsColors.paperRaised, RoundedCornerShape(ReceiptsRadius.small))
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small)),
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    transaction: Transaction,
    amountMinor: Long,
    direction: Direction,
    selectedCategory: String,
    showCategories: Boolean,
    showAllCategories: Boolean,
    learnRule: Boolean,
    onCategoryTap: () -> Unit,
    onCategory: (String) -> Unit,
    onMoreCategories: () -> Unit,
    onLearnRule: (Boolean) -> Unit,
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = ReceiptsSpace.x16 * 5f)
            .background(receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.small))
            .border(1.dp, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.small))
            .padding(ReceiptsSpace.x4),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        ReceiptLabel(reviewLabel(transaction), color = if (transaction.status == TransactionStatus.NEEDS_RESOLUTION) receiptsColors.ultramarine else receiptsColors.fade)
        Text(
            if (amountMinor > 0L) receiptMoney(amountMinor) else "₹0",
            color = if (amountMinor > 0L) receiptsColors.ink else receiptsColors.fade,
            style = ReceiptsType.display,
        )
        Text(receiptMerchant(transaction), color = receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(receiptMeta(transaction), color = receiptsColors.fade, style = ReceiptsType.meta, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (transaction.status == TransactionStatus.UNPARSEABLE) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                MiniChoice("Spent", direction == Direction.DEBIT, Modifier.weight(1f)) { onDirection(Direction.DEBIT) }
                MiniChoice("Money in", direction == Direction.CREDIT, Modifier.weight(1f)) { onDirection(Direction.CREDIT) }
            }
            transaction.rawMessage?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().background(receiptsColors.sunk, RoundedCornerShape(ReceiptsRadius.small))
                        .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
                        .padding(ReceiptsSpace.x3),
                    color = receiptsColors.inkSoft,
                    style = ReceiptsType.meta.copy(fontFamily = ReceiptsFonts.splineSansMono),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ReceiptPerforation()
        ReceiptLabel(if (transaction.categoryId == null) "Pick category" else "Best guess")
        CategoryLine(selectedCategory, onCategoryTap)
        if (showCategories) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2), verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                reviewCategoryOptions(direction, showAllCategories).forEach { item ->
                    CategoryOption(item, selectedCategory == item.id) { onCategory(item.id) }
                }
                if (!showAllCategories && direction == Direction.DEBIT) MiniChoice("More", false, onClick = onMoreCategories)
            }
        }
        if (canLearnMerchant(transaction.merchant)) LearnToggle("Always use this for ${transaction.merchant}", learnRule, onLearnRule)
        Text("Swipe right to keep · left to ignore", color = receiptsColors.fade, style = ReceiptsType.meta)
    }
}
@Composable
private fun CategoryLine(categoryId: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .border(1.dp, receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(ReceiptsSpace.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
    ) {
        ReceiptCategoryMark(categoryId)
        Text(categoryName(categoryId), Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.bodyStrong)
        Text("CHANGE", color = receiptsColors.fade, style = ReceiptsType.label)
    }
}
@Composable
private fun CategoryOption(category: Category, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .background(if (selected) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.pill))
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptCategoryMark(category.id)
        Text(category.name, color = if (selected) receiptsColors.paper else receiptsColors.ink, style = ReceiptsType.label, maxLines = 1)
    }
}
@Composable
private fun LearnToggle(text: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ReceiptsSpace.x12)
            .clickable(role = Role.Button) { onChecked(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = ReceiptsSpace.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
    ) {
        Box(
            Modifier.size(ReceiptsSpace.x4)
                .background(if (checked) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.small))
                .border(1.dp, if (checked) receiptsColors.ink else receiptsColors.ruleHard, RoundedCornerShape(ReceiptsRadius.small)),
        )
        Text(text, color = if (checked) receiptsColors.ink else receiptsColors.fade, style = ReceiptsType.meta, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun ReviewActionButton(text: String, action: ReviewAction, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val filled = action == ReviewAction.KEEP
    val border = if (action == ReviewAction.REFUND) receiptsColors.ultramarine else receiptsColors.ruleHard
    val content = when (action) {
        ReviewAction.REFUND -> receiptsColors.ultramarine
        ReviewAction.KEEP -> receiptsColors.paper
        ReviewAction.IGNORE -> receiptsColors.fade
    }
    Box(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (filled) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.small))
            .border(1.dp, if (filled) receiptsColors.ink else border, RoundedCornerShape(ReceiptsRadius.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x2, vertical = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = content, style = ReceiptsType.label, maxLines = 1)
    }
}
@Composable
private fun MiniChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(minHeight = ReceiptsSpace.x12)
            .background(if (selected) receiptsColors.ink else receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.pill))
            .border(1.dp, if (selected) receiptsColors.ink else receiptsColors.rule, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ReceiptsSpace.x3, vertical = ReceiptsSpace.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = if (selected) receiptsColors.paper else receiptsColors.fade, style = ReceiptsType.label, maxLines = 1)
    }
}
@Composable
private fun CloseTextButton(description: String, onClick: () -> Unit) {
    ReceiptIconButton(Icons.Default.Close, description, onClick)
}
private fun reviewLabel(transaction: Transaction): String = when (transaction.status) { TransactionStatus.NEEDS_RESOLUTION -> "Money landed"; TransactionStatus.UNPARSEABLE -> "Needs amount"; else -> "Needs review" }
private fun defaultDirection(transaction: Transaction): Direction = if (transaction.status == TransactionStatus.NEEDS_RESOLUTION) Direction.CREDIT else transaction.direction
private fun defaultCategoryId(transaction: Transaction): String = transaction.categoryId ?: if (defaultDirection(transaction) == Direction.CREDIT) "income" else "misc"
private fun reviewCategoryOptions(direction: Direction, showAll: Boolean): List<Category> {
    if (direction == Direction.CREDIT) return Categories.filter { it.id in listOf("income", "repayments", "transfers") }
    val priority = listOf("food", "groceries", "transport", "shopping", "bills", "misc"); val spend = Categories.filter { !it.notSpending && it.id != "refund" && it.id != "income" }
    return if (!showAll) priority.mapNotNull { id -> spend.firstOrNull { it.id == id } } else spend.sortedBy { item -> priority.indexOf(item.id).let { if (it == -1) Int.MAX_VALUE else it } }
}
