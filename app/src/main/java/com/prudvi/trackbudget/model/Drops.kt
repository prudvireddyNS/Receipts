package com.prudvi.trackbudget.model

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class Drop(
    val key: String,
    val ruleKey: String,
    val kicker: String,
    val figure: String,
    val line: String,
    val score: Int,
    val shareable: Boolean = true,
    val categoryId: String? = null,
)

interface DropRule {
    val ruleKey: String
    fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop?
}

object DropEngine {
    val rules: List<DropRule> = listOf(
        OneAmTaxDrop,
        SmallStuffDrop,
        PaceCheckDrop,
        CategorySpikeDrop,
        MerchantObsessionDrop,
        ConcentrationDrop,
        ExpensivePersonalityDrop,
        BeforeYouTouchItDrop,
        QuietRunDrop,
        NewPlacesDrop,
        FlexibleHalfDrop,
        RefundLandedDrop,
    )

    fun evaluate(
        transactions: List<Transaction>,
        analytics: SpendingAnalytics,
        dismissedRuleKeys: Set<String> = emptySet(),
    ): List<Drop> = rules.mapNotNull { rule ->
        if (rule.ruleKey in dismissedRuleKeys) null else rule.evaluate(transactions, analytics)
    }.sortedByDescending { it.score }
}

private object OneAmTaxDrop : DropRule {
    override val ruleKey = "1am-tax"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val night = tx.activeDebits(a).filter { it.localHour() in 0..4 }
        val total = night.sumOf { it.amountMinor }
        if (total < 300_00 || night.size < 3) return null
        val top = night.groupBy { it.merchant }.maxByOrNull { it.value.size }
        val detail = top?.let { " ${it.key} took ${it.value.size} of them." }.orEmpty()
        return drop(a, ruleKey, "The 1am tax", rupees(total), "of your period happened after midnight. ${night.size} orders.$detail", 92)
    }
}

private object SmallStuffDrop : DropRule {
    override val ruleKey = "small-stuff"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val small = tx.activeDebits(a).filter { it.amountMinor < 100_00 }
        if (small.size < 15) return null
        val total = small.sumOf { it.amountMinor }
        val largestCategory = a.snapshot.categoryTotals.values.maxOrNull() ?: 0L
        val ending = if (total >= largestCategory) "That's your biggest category and it doesn't have a name." else "That is ${small.size} separate taps."
        return drop(a, ruleKey, "Small stuff", "${small.size} receipts under ₹100", "add up to ${rupees(total)}. $ending", 86)
    }
}

private object PaceCheckDrop : DropRule {
    override val ruleKey = "pace-check"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        if (a.snapshot.dayOfPeriod < 7) return null
        val budgetMinor = a.snapshot.spentMinor + a.snapshot.remainingMinor
        if (budgetMinor <= 0) return null
        return drop(a, ruleKey, "Pace check", rupees(a.projectedMinor), "At this rate ${periodLabel(a.snapshot.range)} closes at ${rupees(a.projectedMinor)}. Budget's ${rupees(budgetMinor)}.", 72)
    }
}

private object CategorySpikeDrop : DropRule {
    override val ruleKey = "category-spike"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val zone = ZoneId.systemDefault()
        val previousStart = a.snapshot.range.start.minusDays(a.snapshot.range.days.toLong())
        val previousEnd = a.snapshot.range.start
        val current = tx.activeDebits(a).groupBy { it.categoryId.orEmpty() }.mapValues { it.value.sumOf(Transaction::amountMinor) }
        val previous = tx.filter { it.isSpendingDebit() }
            .filter { it.localDate(zone) >= previousStart && it.localDate(zone) < previousEnd }
            .groupBy { it.categoryId.orEmpty() }
            .mapValues { it.value.sumOf(Transaction::amountMinor) }
        val spike = current.mapNotNull { (id, amount) ->
            val before = previous[id] ?: return@mapNotNull null
            val delta = amount - before
            if (delta >= 500_00 && delta * 100 >= before * 40) Triple(id, delta, amount.toFloat() / before) else null
        }.maxByOrNull { it.second } ?: return null
        val name = category(spike.first)?.name ?: "A category"
        val comparison = if (spike.third >= 2f) " Roughly ${spike.third.roundToInt()}×." else ""
        return dropWithSuffix(a, ruleKey, spike.first, "Category spike", rupees(spike.second), "$name is up ${rupees(spike.second)} on last period.$comparison", 78, categoryId = spike.first)
    }
}

private object MerchantObsessionDrop : DropRule {
    override val ruleKey = "merchant-obsession"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val top = tx.activeDebits(a).groupBy { it.merchant.trim() }
            .map { (merchant, items) -> MerchantVisit(merchant, items.size, items.sumOf(Transaction::amountMinor)) }
            .filter { it.count >= 6 }
            .maxWithOrNull(compareBy<MerchantVisit> { it.count }.thenBy { it.amountMinor }) ?: return null
        return dropWithSuffix(a, ruleKey, top.merchant.stablePart(), "Merchant obsession", "${top.count} times", "You've paid ${top.merchant} ${top.count} times this period. ${rupees(top.amountMinor)}.", 84)
    }
}

private object ConcentrationDrop : DropRule {
    override val ruleKey = "concentration"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        if (a.topThreeShare < 0.55f || a.snapshot.spentMinor <= 0) return null
        val pct = (a.topThreeShare * 100).roundToInt()
        return drop(a, ruleKey, "Concentration", "$pct%", "Three places took $pct% of your period.", 70)
    }
}

private object ExpensivePersonalityDrop : DropRule {
    override val ruleKey = "expensive-personality"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val zone = ZoneId.systemDefault()
        val end = a.snapshot.range.endInclusive
        val start = end.minusDays(89)
        val totals = tx.filter { it.isSpendingDebit() }
            .mapNotNull { item -> item.localDate(zone).takeIf { it in start..end }?.let { it to item.amountMinor } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
        val byDay = totals.entries.groupBy { it.key.dayOfWeek }.mapValues { (_, rows) -> rows.map { it.value }.average().toLong() }
        val top = byDay.maxByOrNull { it.value } ?: return null
        val med = median(byDay.values.toList()).takeIf { it > 0 } ?: return null
        if (top.value < med * 2) return null
        val low = byDay.minByOrNull { it.value } ?: return null
        return drop(a, ruleKey, top.key.label(), "is your most expensive personality", "${rupees(top.value)} average. ${low.key.label()} manages ${rupees(low.value)}.", 76)
    }
}

private object BeforeYouTouchItDrop : DropRule {
    override val ruleKey = "before-you-touch-it"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        if (a.recurring.size < 2) return null
        val total = a.recurring.sumOf { it.amountMinor }
        return drop(a, ruleKey, "Before you touch it", rupees(total), "leaves every month before you touch it. ${a.recurring.size} subscriptions.", 68)
    }
}

private object QuietRunDrop : DropRule {
    override val ruleKey = "quiet-run"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val days = underPaceDays(a.snapshot)
        if (days < 3) return null
        return drop(a, ruleKey, "Quiet run", "$days days", "under pace.", 66)
    }
}

private object NewPlacesDrop : DropRule {
    override val ruleKey = "new-places"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val zone = ZoneId.systemDefault()
        val current = tx.activeDebits(a).map { it.merchant.normalizedMerchant() }.toSet()
        val oldStart = a.snapshot.range.start.minusDays(180)
        val seen = tx.filter { it.isSpendingDebit() }
            .filter { it.localDate(zone) >= oldStart && it.localDate(zone) < a.snapshot.range.start }
            .map { it.merchant.normalizedMerchant() }
            .toSet()
        val count = (current - seen).size
        if (count < 4) return null
        return drop(a, ruleKey, "New places", "$count places", "you'd never paid before.", 62)
    }
}

private object FlexibleHalfDrop : DropRule {
    override val ruleKey = "flexible-half"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        if (a.committedMinor <= 0) return null
        return drop(a, ruleKey, "The flexible half", rupees(a.committedMinor), "of your period was already decided before it started.", 58)
    }
}

private object RefundLandedDrop : DropRule {
    override val ruleKey = "refund-landed"
    override fun evaluate(tx: List<Transaction>, a: SpendingAnalytics): Drop? {
        val refund = tx.activeCredits(a).filter { it.categoryId == "refund" && it.amountMinor >= 200_00 }.maxByOrNull { it.amountMinor } ?: return null
        return drop(a, ruleKey, "Refund landed", rupees(refund.amountMinor), "came back from ${refund.merchant}. Your period just got shorter.", 88)
    }
}

private data class MerchantVisit(val merchant: String, val count: Int, val amountMinor: Long)

private fun drop(a: SpendingAnalytics, rule: String, kicker: String, figure: String, line: String, score: Int): Drop = Drop(
    key = "$rule-${periodKey(a.snapshot.range)}",
    ruleKey = rule,
    kicker = kicker,
    figure = figure,
    line = line,
    score = score.coerceIn(0, 100),
)

private fun dropWithSuffix(a: SpendingAnalytics, rule: String, suffix: String, kicker: String, figure: String, line: String, score: Int, categoryId: String? = null): Drop = Drop(
    key = "$rule-$suffix-${periodKey(a.snapshot.range)}",
    ruleKey = rule,
    kicker = kicker,
    figure = figure,
    line = line,
    score = score.coerceIn(0, 100),
    categoryId = categoryId,
)

private fun List<Transaction>.activeDebits(a: SpendingAnalytics): List<Transaction> = filter { item ->
    item.isSpendingDebit() && item.localDate() in a.snapshot.range.start..a.snapshot.range.endInclusive
}

private fun List<Transaction>.activeCredits(a: SpendingAnalytics): List<Transaction> = filter { item ->
    item.direction == Direction.CREDIT &&
        item.status == TransactionStatus.CONFIRMED &&
        item.localDate() in a.snapshot.range.start..a.snapshot.range.endInclusive
}

private fun Transaction.isSpendingDebit(): Boolean = direction == Direction.DEBIT &&
    status == TransactionStatus.CONFIRMED &&
    category(categoryId)?.notSpending != true

private fun Transaction.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
private fun Transaction.localHour(zone: ZoneId = ZoneId.systemDefault()): Int = Instant.ofEpochMilli(occurredAt).atZone(zone).hour
private fun String.normalizedMerchant(): String = trim().lowercase(Locale.US)
private fun String.stablePart(): String = normalizedMerchant().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "merchant" }

private fun underPaceDays(snapshot: DashboardSnapshot): Int {
    val budgetMinor = snapshot.spentMinor + snapshot.remainingMinor
    if (budgetMinor <= 0) return 0
    var running = 0L
    var streak = 0
    repeat(snapshot.dayOfPeriod) { index ->
        val date = snapshot.range.start.plusDays(index.toLong())
        running += snapshot.dailyTotals[date]?.coerceAtLeast(0) ?: 0L
        val evenPace = budgetMinor * (index + 1) / snapshot.daysInPeriod
        streak = if (running < evenPace) streak + 1 else 0
    }
    return streak
}

private fun median(values: List<Long>): Long {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

private fun DayOfWeek.label(): String = name.lowercase(Locale.US).replaceFirstChar(Char::titlecase)

private fun rupees(minor: Long): String = "₹" + NumberFormat.getIntegerInstance(
    Locale.Builder().setLanguage("en").setRegion("IN").build(),
).format(kotlin.math.abs(minor) / 100)
