package com.prudvi.trackbudget.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.findRefundCandidate
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ReviewStatuses = setOf(TransactionStatus.CATEGORY_REVIEW, TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE)
private enum class ReviewAction { IGNORE, REFUND, KEEP }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewDeck(
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onSave: (Transaction) -> Unit,
    onLearn: (String, String, Direction) -> Unit,
    modifier: Modifier = Modifier,
    initialTransactionId: String? = null,
    onGoHome: () -> Unit = onClose,
    onGoAdd: () -> Unit = {},
    onGoHistory: () -> Unit = onClose,
) {
    val queue = remember(transactions, initialTransactionId) {
        transactions.filter { it.status in ReviewStatuses }
            .sortedWith(compareByDescending<Transaction> { it.id == initialTransactionId }.thenByDescending { it.occurredAt })
    }
    val top = queue.firstOrNull()
    var showCategories by rememberSaveable(top?.id) { mutableStateOf(false) }
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

    fun currentAmountMinor(): Long = if (top?.status == TransactionStatus.UNPARSEABLE) decimalToMinor(amountDigits) else top?.amountMinor ?: 0L
    fun canResolve(action: ReviewAction): Boolean {
        if (top == null) return false
        if (action == ReviewAction.IGNORE) return true
        return currentAmountMinor() > 0L && (action != ReviewAction.REFUND || top.direction == Direction.CREDIT)
    }
    fun apply(action: ReviewAction) {
        val transaction = top ?: return
        val amountMinor = currentAmountMinor()
        when (action) {
            ReviewAction.IGNORE -> onSave(transaction.copy(status = TransactionStatus.EXCLUDED))
            ReviewAction.REFUND -> {
                if (amountMinor <= 0L || transaction.direction != Direction.CREDIT) return
                val resolved = transaction.copy(amountMinor = amountMinor, direction = Direction.CREDIT, categoryId = "refund")
                val match = findRefundCandidate(transactions, resolved)
                onSave(resolved.copy(refundOfId = match?.id, status = TransactionStatus.CONFIRMED))
            }
            ReviewAction.KEEP -> {
                if (amountMinor <= 0L) return
                val categoryId = selectedCategory.ifBlank { if (resolvedDirection == Direction.CREDIT) "income" else "misc" }
                // Only a category the user chose themselves becomes a rule. Swiping Keep on the app's own
                // best guess used to teach it back to itself, so one wrong guess skipped review forever.
                val choseCategory = selectedCategory != defaultCategoryId(transaction)
                if (choseCategory && resolvedDirection == Direction.DEBIT && canLearnMerchant(transaction.merchant)) onLearn(transaction.merchant, categoryId, Direction.DEBIT)
                onSave(transaction.copy(amountMinor = amountMinor, direction = resolvedDirection, categoryId = categoryId, status = TransactionStatus.CONFIRMED))
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
            .padding(top = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Review", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
            Box(
                Modifier.clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .background(receiptsColors.pink)
                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                    .padding(horizontal = 11.dp, vertical = 3.dp),
            ) { Text("${queue.size} left".uppercase(), color = receiptsColors.chilliOn, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp)) }
        }

        if (top == null) {
            Box(Modifier.weight(1f).padding(horizontal = 18.dp), contentAlignment = Alignment.TopCenter) {
                ReceiptEmpty(Modifier.padding(top = 30.dp), onGoHome)
            }
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val offsetX = if (dragging) dragX else xAnim.value
                    Box(
                        Modifier.fillMaxSize()
                            .padding(start = 9.dp, top = 12.dp, end = 0.dp, bottom = 0.dp)
                            .clip(RoundedCornerShape(ReceiptsRadius.hero))
                            .background(receiptsColors.cyan)
                            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.hero)),
                    )
                    ReviewCard(
                        transaction = top,
                        amountMinor = currentAmountMinor(),
                        direction = resolvedDirection,
                        selectedCategory = selectedCategory,
                        showCategories = showCategories,
                        showAllCategories = showAllCategories,
                        onCategoryTap = { showCategories = !showCategories },
                        onCategory = { selectedCategory = it },
                        onMoreCategories = { showAllCategories = true },
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
                            // Horizontal-only: nothing is claimed until the pointer clears touch slop
                            // *horizontally*, so a vertical drag on the card reaches the surrounding
                            // scroll container untouched. `detectDragGestures` would swallow it, which
                            // stranded the card whenever the expanded category grid made the page
                            // taller than the screen.
                            .pointerInput(top.id, widthPx, amountDigits, selectedCategory, resolvedDirection) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val past = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                        change.consume()
                                        dragging = true
                                        dragX = xAnim.value + over
                                        scope.launch { xAnim.stop() }
                                    } ?: return@awaitEachGesture
                                    var cancelled = false
                                    horizontalDrag(past.id) { change ->
                                        dragX += change.positionChange().x
                                        change.consume()
                                    }.also { completed -> cancelled = !completed }
                                    if (cancelled) {
                                        settle(null, widthPx, dragX)
                                    } else {
                                        val threshold = widthPx * 0.32f
                                        val action = when {
                                            dragX > threshold -> ReviewAction.KEEP
                                            dragX < -threshold -> ReviewAction.IGNORE
                                            else -> null
                                        }
                                        settle(action, widthPx, dragX)
                                    }
                                }
                            },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ReviewActionButton("← Ignore", ReviewAction.IGNORE, true, Modifier.weight(1f)) { settle(ReviewAction.IGNORE, 1_000f, 0f) }
                    ReviewActionButton("↑ Refund", ReviewAction.REFUND, canResolve(ReviewAction.REFUND), Modifier.weight(1f)) { settle(ReviewAction.REFUND, 1_000f, 0f) }
                    ReviewActionButton("Keep →", ReviewAction.KEEP, canResolve(ReviewAction.KEEP), Modifier.weight(1f)) { settle(ReviewAction.KEEP, 1_000f, 0f) }
                }
                if (top.status == TransactionStatus.UNPARSEABLE) {
                    ReceiptAmountPad(
                        onKey = { key ->
                            when {
                                key == "del" -> amountDigits = amountDigits.dropLast(1)
                                key == "." && '.' !in amountDigits -> amountDigits = if (amountDigits.isBlank()) "0." else "$amountDigits."
                                key.all(Char::isDigit) && amountDigits.length < 11 -> amountDigits = sanitizeReviewAmount(amountDigits + key)
                                else -> {
                                    resolvedDirection = if (resolvedDirection == Direction.DEBIT) Direction.CREDIT else Direction.DEBIT
                                    selectedCategory = if (resolvedDirection == Direction.CREDIT) "income" else "misc"
                                }
                            }
                        },
                        noteLabel = if (resolvedDirection == Direction.DEBIT) "in" else "out",
                        specialContentDescription = if (resolvedDirection == Direction.DEBIT) "Switch to money in" else "Switch to spent",
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        ReviewBottomNav(onGoHome, onGoAdd, onGoHistory)
    }
}

@Composable
private fun ReviewCard(
    transaction: Transaction,
    amountMinor: Long,
    direction: Direction,
    selectedCategory: String,
    showCategories: Boolean,
    showAllCategories: Boolean,
    onCategoryTap: () -> Unit,
    onCategory: (String) -> Unit,
    onMoreCategories: () -> Unit,
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .mockShadow(ReceiptsRadius.hero, 4.dp, 4.dp, receiptsColors.ink)
            .clip(RoundedCornerShape(ReceiptsRadius.hero))
            .background(receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.hero))
            .padding(18.dp),
    ) {
        ReceiptLabel("${transaction.sender ?: transaction.source.name} · ${receiptShortDate(receiptDate(transaction.occurredAt))}, ${receiptTime(transaction.occurredAt)}")
        Text(if (amountMinor > 0L) receiptMoney(amountMinor) else "₹0", color = if (amountMinor > 0L) receiptsColors.ink else receiptsColors.fade, style = ReceiptsType.hero.copy(fontSize = 42.sp, letterSpacing = (-1.2).sp), modifier = Modifier.padding(top = 9.dp))
        Text(receiptMerchant(transaction), color = receiptsColors.ink, style = ReceiptsType.bodyStrong.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (transaction.status == TransactionStatus.UNPARSEABLE) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniChoice("Spent", direction == Direction.DEBIT, Modifier.weight(1f)) { onDirection(Direction.DEBIT) }
                MiniChoice("Money in", direction == Direction.CREDIT, Modifier.weight(1f)) { onDirection(Direction.CREDIT) }
            }
        }
        Box(Modifier.fillMaxWidth().height(31.dp).receiptPerforation(receiptsColors.ink))
        ReceiptLabel("Best guess", modifier = Modifier.padding(bottom = 9.dp))
        CategoryLine(selectedCategory, onCategoryTap)
        if (showCategories) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                reviewCategoryOptions(direction, showAllCategories).forEach { item ->
                    CategoryOption(item, selectedCategory == item.id) { onCategory(item.id) }
                }
                if (!showAllCategories) MiniChoice("More", false, onClick = onMoreCategories)
            }
        }
        Text("Swipe right to keep · left to ignore", color = receiptsColors.fade, style = ReceiptsType.meta, modifier = Modifier.padding(top = 13.dp))
    }
}

@Composable
private fun CategoryLine(categoryId: String, onClick: () -> Unit) {
    Row(
        Modifier.defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.mint)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptCategoryMark(categoryId, size = 24.dp, radius = 7.dp)
        Text(categoryName(categoryId), color = receiptsColors.ink, style = ReceiptsType.bodyStrong, maxLines = 1)
    }
}

@Composable
private fun CategoryOption(category: Category, selected: Boolean, onClick: () -> Unit) {
    ReceiptPill(categoryName(category.id), selected = selected, onClick = onClick)
}

@Composable
private fun MiniChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(if (selected) receiptsColors.yellow else receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), color = if (selected) receiptsColors.chromeOn else receiptsColors.fade, style = ReceiptsType.label) }
}

@Composable
private fun ReviewActionButton(text: String, action: ReviewAction, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = when (action) {
        ReviewAction.IGNORE -> receiptsColors.paper
        ReviewAction.REFUND -> receiptsColors.purple
        ReviewAction.KEEP -> receiptsColors.yellow
    }
    val content = when (action) {
        ReviewAction.REFUND -> receiptsColors.ultramarineOn
        ReviewAction.KEEP -> receiptsColors.chromeOn
        ReviewAction.IGNORE -> receiptsColors.inkSoft
    }
    Box(
        modifier.defaultMinSize(minHeight = 48.dp)
            .then(if (action == ReviewAction.KEEP && enabled) Modifier.mockShadow(ReceiptsRadius.medium, 3.dp, 3.dp, receiptsColors.ink) else Modifier)
            .clip(RoundedCornerShape(ReceiptsRadius.medium))
            .background(background)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.medium))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (action == ReviewAction.REFUND || enabled) content else receiptsColors.fade, style = ReceiptsType.bodyStrong.copy(fontSize = 11.5.sp), maxLines = 1) }
}

@Composable
private fun ReceiptEmpty(modifier: Modifier = Modifier, onHome: () -> Unit) {
    ReceiptCard(modifier.fillMaxWidth(), background = receiptsColors.cyan, radius = ReceiptsRadius.hero) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing needs you.", color = receiptsColors.ink, style = ReceiptsType.heading.copy(fontSize = 20.sp))
            Text("Clean. New texts land here as they arrive.", color = receiptsColors.inkSoft, style = ReceiptsType.body, modifier = Modifier.padding(top = 6.dp))
            ReceiptButton("Back to home", onHome, Modifier.padding(top = 16.dp), style = ReceiptButtonStyle.CHROME)
        }
    }
}

@Composable
private fun ReviewBottomNav(onHome: () -> Unit, onAdd: () -> Unit, onHistory: () -> Unit) {
    val colors = receiptsColors
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .background(colors.warm)
            .drawBehind { drawLine(colors.ink, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = ReceiptsStroke.width.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BottomItem("Home", active = true, Modifier.weight(1f), onHome)
        Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "Add receipt" }, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).mockShadow(ReceiptsRadius.pill, 3.dp, 3.dp, receiptsColors.ink)
                    .clip(RoundedCornerShape(ReceiptsRadius.pill))
                    .background(receiptsColors.yellow)
                    .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = receiptsColors.chromeOn, style = ReceiptsType.heading.copy(fontSize = 23.sp)) }
        }
        BottomItem("History", active = false, Modifier.weight(1f), onHistory)
    }
}

@Composable
private fun BottomItem(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text.uppercase(), color = if (active) receiptsColors.ink else receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 9.5.sp, letterSpacing = 1.sp), maxLines = 1)
        if (active) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(3.dp).clip(RoundedCornerShape(ReceiptsRadius.pill)).background(receiptsColors.pink))
    }
}

private fun sanitizeReviewAmount(value: String): String {
    val clean = value.filter { it.isDigit() || it == '.' }
    val before = clean.substringBefore('.').take(8)
    val after = clean.substringAfter('.', missingDelimiterValue = "").take(2)
    return if ('.' in clean) "$before.$after" else before
}

private fun defaultDirection(transaction: Transaction): Direction = if (transaction.status == TransactionStatus.NEEDS_RESOLUTION) Direction.CREDIT else transaction.direction
private fun defaultCategoryId(transaction: Transaction): String = transaction.categoryId ?: if (defaultDirection(transaction) == Direction.CREDIT) "income" else "misc"
private fun reviewCategoryOptions(direction: Direction, showAll: Boolean): List<Category> {
    if (direction == Direction.CREDIT) return Categories.filter { it.id in listOf("income", "refund", "transfers") }.sortedBy { it.name }
    if (showAll) return Categories.filter { it.id !in setOf("income", "refund", "repayments") }.sortedBy { it.name }
    val priority = listOf("food", "groceries", "transport", "shopping", "bills", "misc")
    val spend = Categories.filter { !it.notSpending && it.id != "refund" && it.id != "income" }
    return priority.mapNotNull { id -> spend.firstOrNull { it.id == id } }
}
