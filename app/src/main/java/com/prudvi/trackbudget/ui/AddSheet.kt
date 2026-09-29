package com.prudvi.trackbudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prudvi.trackbudget.model.Categories
import com.prudvi.trackbudget.model.Category
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.LearnedRule
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSheet(
    onClose: () -> Unit,
    onSave: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    learnedRules: List<LearnedRule> = emptyList(),
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(Direction.DEBIT) }
    var selectedCategory by rememberSaveable { mutableStateOf("food") }
    var categoryOverridden by rememberSaveable { mutableStateOf(false) }
    var showTo by rememberSaveable { mutableStateOf(false) }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }
    var to by rememberSaveable { mutableStateOf("") }
    val toFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val amountMinor = decimalToMinor(amount)
    val suggestedCategory = suggestedCategory(to, direction, learnedRules)

    LaunchedEffect(to, direction, suggestedCategory, categoryOverridden, showTo) {
        if (showTo && !categoryOverridden) selectedCategory = suggestedCategory
    }
    LaunchedEffect(showTo) {
        if (showTo) {
            toFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun setDirection(next: Direction) {
        direction = next
        selectedCategory = if (next == Direction.CREDIT) "income" else suggestedCategory(to, next, learnedRules)
        categoryOverridden = false
        showAllCategories = false
    }

    fun handleKey(key: String) {
        when (key) {
            "to" -> showTo = true
            "del" -> amount = amount.dropLast(1)
            "." -> if ('.' !in amount) amount = if (amount.isBlank()) "0." else "$amount."
            else -> if (key.all(Char::isDigit) && amount.length < 11) amount = sanitizeDecimal(amount + key)
        }
    }

    fun save() {
        if (amountMinor <= 0) return
        val categoryId = selectedCategory.ifBlank { if (direction == Direction.CREDIT) "income" else "misc" }
        onSave(
            Transaction(
                id = UUID.randomUUID().toString(),
                amountMinor = amountMinor,
                direction = direction,
                occurredAt = System.currentTimeMillis(),
                merchant = to.trim().ifBlank { if (direction == Direction.CREDIT) "Money received" else "Manual expense" },
                categoryId = categoryId,
                note = "",
                status = TransactionStatus.CONFIRMED,
                source = TransactionSource.MANUAL,
            ),
        )
    }

    Column(
        modifier.fillMaxSize()
            .background(receiptsColors.paper)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .imePadding()
            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Add", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
            CloseCircle(onClose)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.hero))
                .background(receiptsColors.cyan)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.hero))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = if (amountMinor > 0L) Alignment.CenterEnd else Alignment.Center,
        ) {
            ReceiptHeroAmount(
                if (amountMinor > 0L) receiptMoney(amountMinor) else "₹0",
                color = if (amountMinor > 0L) receiptsColors.ink else receiptsColors.fade,
                textAlign = if (amountMinor > 0L) TextAlign.End else TextAlign.Center,
            )
        }

        DirectionSlider(direction, Modifier.padding(top = 11.dp)) { setDirection(it) }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            categoryOptions(direction, showAllCategories).forEachIndexed { index, item ->
                CategoryChoice(
                    category = item,
                    selected = selectedCategory == item.id,
                    modifier = Modifier.receiptEnter(index.coerceAtMost(10), key = "$direction-$showAllCategories-${item.id}", lift = 14.dp),
                ) {
                    selectedCategory = item.id
                    categoryOverridden = true
                }
            }
            if (direction == Direction.DEBIT) {
                if (!showAllCategories) {
                    ReceiptPill("More", onClick = { showAllCategories = true })
                } else {
                    ReceiptPill("Less", onClick = { showAllCategories = false })
                }
            }
        }

        if (showTo) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ReceiptLabel(if (direction == Direction.DEBIT) "To" else "From")
                ReceiptTextField(
                    value = to,
                    onValueChange = {
                        to = it.take(80)
                        categoryOverridden = false
                    },
                    placeholder = if (direction == Direction.DEBIT) "Merchant or payee" else "Sender or source",
                    modifier = Modifier.focusRequester(toFocusRequester).semantics { contentDescription = if (direction == Direction.DEBIT) "To merchant or payee" else "From sender or source" },
                )
            }
        }

        }

        ReceiptAmountPad(
            onKey = ::handleKey,
            modifier = Modifier.padding(top = 12.dp),
            noteLabel = "to",
            specialContentDescription = "Edit merchant or payee",
        )

        ReceiptButton(
            text = "Save",
            onClick = ::save,
            // save() bails on a zero amount, so leaving the button lit just made it look broken.
            enabled = amountMinor > 0L,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            style = ReceiptButtonStyle.CHROME,
            radius = ReceiptsRadius.card,
            shadowOffset = 4.dp,
            textStyle = ReceiptsType.button.copy(fontSize = 16.sp),
        )
    }
}

@Composable
private fun CloseCircle(onClose: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close add" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp)
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(receiptsColors.paper)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, contentDescription = null, tint = receiptsColors.ink, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun DirectionSlider(direction: Direction, modifier: Modifier = Modifier, onDirectionChange: (Direction) -> Unit) {
    val isCredit = direction == Direction.CREDIT
    val fraction by androidx.compose.animation.core.animateFloatAsState(if (isCredit) 1f else 0f, label = "direction-slider")
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(ReceiptsRadius.pill))
            .background(receiptsColors.paper)
            .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
            .padding(4.dp),
    ) {
        val thumbWidth = maxWidth / 2
        Box(
            Modifier.offset(x = thumbWidth * fraction).width(thumbWidth).fillMaxHeight()
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(if (isCredit) receiptsColors.purple else receiptsColors.yellow)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill)),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(role = Role.RadioButton) { onDirectionChange(Direction.DEBIT) }
                    .semantics { selected = !isCredit },
                contentAlignment = Alignment.Center,
            ) {
                Text("SPENT", color = if (!isCredit) receiptsColors.chromeOn else receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(role = Role.RadioButton) { onDirectionChange(Direction.CREDIT) }
                    .semantics { selected = isCredit },
                contentAlignment = Alignment.Center,
            ) {
                Text("MONEY IN", color = if (isCredit) receiptsColors.ultramarineOn else receiptsColors.fade, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategoryChoice(category: Category, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val target = if (selected) categoryVisual(category.id).color else receiptsColors.paper
    val fill by androidx.compose.animation.animateColorAsState(target, androidx.compose.animation.core.tween(ReceiptsMotion.KEY * 2), label = "chip-fill")
    val foreground by androidx.compose.animation.animateColorAsState(
        if (selected) categoryMarkTextColor(target) else receiptsColors.fade,
        androidx.compose.animation.core.tween(ReceiptsMotion.KEY * 2),
        label = "chip-fg",
    )
    val pop by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "chip-pop",
    )
    Box(
        modifier.defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = category.name },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 40.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(RoundedCornerShape(ReceiptsRadius.pill))
                .background(fill)
                .border(ReceiptsStroke.width, receiptsColors.ink, RoundedCornerShape(ReceiptsRadius.pill))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(category.id), contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
            Text(categoryName(category.id), color = foreground, style = ReceiptsType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun categoryOptions(direction: Direction, showAll: Boolean): List<Category> {
    if (direction == Direction.CREDIT) {
        return Categories.filter { it.id in setOf("income", "refund", "transfers") }.sortedBy { it.name }
    }
    if (showAll) {
        return Categories.filter { it.id !in setOf("income", "refund", "repayments") }.sortedBy { it.name }
    }
    return listOf("food", "groceries", "transport", "shopping").mapNotNull { id -> Categories.firstOrNull { it.id == id } }
}

private fun suggestedCategory(merchant: String, direction: Direction, rules: List<LearnedRule>): String {
    if (direction == Direction.CREDIT) return "income"
    val clean = merchant.trim()
    if (clean.isBlank()) return "food"
    val normalized = clean.uppercase()
    rules.firstOrNull { it.merchant == normalized && it.direction == Direction.DEBIT }?.let { return it.categoryId }
    rules.firstOrNull { it.merchant == normalized && it.direction == null }?.let { return it.categoryId }
    val lower = clean.lowercase()
    return when {
        listOf("swiggy", "zomato", "restaurant", "cafe").any(lower::contains) -> "food"
        listOf("blinkit", "zepto", "bigbasket", "instamart", "grocery").any(lower::contains) -> "groceries"
        listOf("amazon", "flipkart", "myntra").any(lower::contains) -> "shopping"
        listOf("uber", "ola", "rapido", "metro", "fuel").any(lower::contains) -> "transport"
        else -> "food"
    }
}

private fun sanitizeDecimal(value: String): String {
    val clean = value.filter { it.isDigit() || it == '.' }
    val before = clean.substringBefore('.').take(8)
    val after = clean.substringAfter('.', missingDelimiterValue = "").take(2)
    return if ('.' in clean) "$before.$after" else before
}
