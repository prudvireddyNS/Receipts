package com.prudvi.trackbudget.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class Direction { DEBIT, CREDIT }
enum class TransactionStatus { CONFIRMED, CATEGORY_REVIEW, NEEDS_REVIEW, NEEDS_RESOLUTION, UNPARSEABLE, EXCLUDED }
enum class TransactionSource { SMS, MANUAL, SAMPLE, LEGACY }

data class Category(
    val id: String,
    val name: String,
    val color: Long,
    val notSpending: Boolean = false,
)

data class Transaction(
    val id: String,
    val amountMinor: Long,
    val direction: Direction,
    val occurredAt: Long,
    val merchant: String,
    val categoryId: String?,
    val note: String = "",
    val status: TransactionStatus,
    val source: TransactionSource,
    val accountTail: String? = null,
    val sender: String? = null,
    val refId: String? = null,
    val sourceKey: String? = null,
    val refundOfId: String? = null,
    val rawMessage: String? = null,
    val recurring: Boolean = false,
)

/** Whether this receipt is money spent: a debit outside the "not spending" categories. */
fun Transaction.countsAsSpend(countInvestments: Boolean): Boolean =
    direction == Direction.DEBIT && when (categoryId) {
        "investment" -> countInvestments
        else -> category(categoryId)?.notSpending != true
    }

/** Only a refund gives money back to the budget; salary, top-ups and reimbursed transfers do not. */
fun Transaction.isSpendRefund(): Boolean = direction == Direction.CREDIT && categoryId == "refund"

/** What this receipt does to "spent": positive for spending, negative for a refund, zero otherwise. */
fun Transaction.netSpend(countInvestments: Boolean): Long = when {
    countsAsSpend(countInvestments) -> amountMinor
    isSpendRefund() -> -amountMinor
    else -> 0L
}

data class Budget(
    val amountMinor: Long = 0,
    val period: String = "Month",
    val repeats: Boolean = true,
    val carryOver: Boolean = false,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val categoryLimits: Map<String, Long> = emptyMap(),
    val resetDay: Int = 1,
    val countInvestmentsAsSpending: Boolean = false,
    val commitments: List<Commitment> = emptyList(),
)

/**
 * A fixed obligation (rent, family transfer, a standing investment) the user declares up front
 * rather than one detected from actual transactions. Its monthly amount is prorated to the active
 * budget period and taken off the top of the budget: it never counts as money you spent, it is
 * money you never had. What's left after obligations is [DashboardSnapshot.spendableMinor], and
 * every ceiling in the app — pacing, the burn-up, category limits, alerts — reads that instead of
 * the raw budget figure.
 */
data class Commitment(
    val id: String,
    val name: String,
    val monthlyAmountMinor: Long,
    val enabled: Boolean = true,
)

/**
 * What one obligation costs over [range]. A month-long period charges the declared monthly figure
 * exactly as it was typed — prorating by days/30 turned a ₹20,000 rent into ₹20,666.66 in a 31-day
 * month, which reads as a bug rather than as arithmetic. Only genuinely shorter periods (weekly,
 * custom) take a share.
 *
 * An obligation is a *declared* amount, so if the matching debit is also captured (a rent SMS,
 * say) the same money is counted twice — once off the budget here, once as real spend. Nothing
 * reconciles the two; the payment has to be deleted or excluded by hand.
 */
fun Commitment.shareMinor(range: BudgetRange): Long {
    if (!enabled || monthlyAmountMinor <= 0L) return 0L
    val wholeMonth = range.start.plusMonths(1).minusDays(1) == range.endInclusive
    return if (wholeMonth) monthlyAmountMinor else monthlyAmountMinor * range.days / 30
}

/** Summed over [Commitment.shareMinor] so the total always equals the rows the UI lists. */
fun Budget.obligationsMinor(range: BudgetRange): Long = commitments.sumOf { it.shareMinor(range) }

/**
 * The same budget expressed for another period. Switching Monthly ⇄ Weekly used to keep the number,
 * so ₹30,000 a month quietly became ₹30,000 a week. The amount is scaled by 7/30 (or back) and
 * rounded to the nearest ₹10 so it stays a figure someone would have typed.
 */
fun Budget.amountForPeriod(newPeriod: String): Long {
    if (amountMinor <= 0L || newPeriod == period) return amountMinor
    val scaled = if (newPeriod == "Week") amountMinor * 7 / 30 else amountMinor * 30 / 7
    return ((scaled + 500) / 1_000) * 1_000
}

data class BudgetRange(val start: LocalDate, val endInclusive: LocalDate) {
    val days: Int = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
}

enum class CategoryLimitLevel { NORMAL, WARNING, EXCEEDED }

data class CategoryLimitStatus(
    val categoryId: String,
    val spentMinor: Long,
    val limitMinor: Long,
    val level: CategoryLimitLevel,
) {
    val fraction: Float = if (limitMinor <= 0) 0f else spentMinor.toFloat() / limitMinor
    val remainingMinor: Long = limitMinor - spentMinor
}

data class LearnedRule(
    val merchant: String,
    val categoryId: String,
    val learnedAtEpochDay: Long = LocalDate.now().toEpochDay(),
    val direction: Direction? = null,
)

data class ParsedTransaction(
    val amountMinor: Long,
    val direction: Direction,
    val merchant: String,
    val accountTail: String?,
    val refId: String?,
    val occurredAt: Long,
    val confidence: Float,
    val suggestedCategoryId: String? = null,
    val isExplicitRefund: Boolean = false,
    val excludeByDefault: Boolean = false,
)

data class DashboardSnapshot(
    val spentMinor: Long,
    val refundedMinor: Long,
    val remainingMinor: Long,
    val safeTodayMinor: Long,
    val dayOfPeriod: Int,
    val daysInPeriod: Int,
    val categoryTotals: Map<String, Long>,
    val range: BudgetRange,
    val dailyTotals: Map<LocalDate, Long>,
    /** User-declared fixed obligations prorated to this period. Taken off the budget, never added to [spentMinor]. */
    val obligationsMinor: Long = 0,
    /** The budget minus [obligationsMinor] — what's actually available to spend. 0 when no budget is set. */
    val spendableMinor: Long = 0,
)

val Categories = listOf(
    Category("shopping", "Shopping", 0xFFC88AA4),
    Category("groceries", "Groceries", 0xFF80A875),
    Category("food", "Food & dining", 0xFFD9A63C),
    Category("transport", "Transport", 0xFF78A6C8),
    Category("bills", "Bills & recharges", 0xFFA58BC4),
    Category("transfers", "Transfers", 0xFF657180, true),
    Category("medical", "Medical", 0xFFE0705A),
    Category("travel", "Travel", 0xFF70A6A0),
    Category("repayments", "Repayments", 0xFF3FA894, true),
    Category("personal", "Personal", 0xFFB88DA6),
    Category("services", "Services", 0xFF8CA0C5),
    Category("insurance", "Insurance", 0xFF9D91BE),
    Category("entertainment", "Entertainment", 0xFFB59A69),
    Category("gaming", "Gaming", 0xFF7E9CC2),
    Category("smallshops", "Small shops", 0xFFC39470),
    Category("rent", "Rent", 0xFFCF8C64),
    Category("logistics", "Logistics", 0xFF77A0A8),
    Category("subscription", "Subscription", 0xFF8A98BA),
    Category("investment", "Investment", 0xFF6C8F82, true),
    Category("fitness", "Fitness", 0xFF78A888),
    Category("pet", "Pet", 0xFFC28D78),
    Category("cash", "Cash", 0xFFA4A88A),
    Category("misc", "Miscellaneous", 0xFF8F9AA8),
    Category("refund", "Refund", 0xFF3FA894, true),
    Category("income", "Income", 0xFF3FA894, true),
)

fun category(id: String?): Category? = Categories.firstOrNull { it.id == id }

fun budgetRange(budget: Budget, today: LocalDate = LocalDate.now()): BudgetRange = when (budget.period) {
    "Week" -> {
        val startDay = DayOfWeek.of(budget.resetDay.coerceIn(1, 7))
        val start = today.with(TemporalAdjusters.previousOrSame(startDay))
        BudgetRange(start, start.plusDays(6))
    }
    "Rolling" -> BudgetRange(today.minusDays(29), today)
    "Custom" -> {
        val start = budget.startEpochDay?.let(LocalDate::ofEpochDay) ?: today
        val end = budget.endEpochDay?.let(LocalDate::ofEpochDay)?.coerceAtLeast(start) ?: start.plusDays(30)
        BudgetRange(start, end)
    }
    else -> {
        val resetDay = budget.resetDay.coerceIn(1, 28)
        val candidate = today.withDayOfMonth(resetDay)
        val start = if (today.isBefore(candidate)) candidate.minusMonths(1) else candidate
        BudgetRange(start, start.plusMonths(1).minusDays(1))
    }
}

fun dashboard(transactions: List<Transaction>, budget: Budget, now: Long = System.currentTimeMillis()): DashboardSnapshot {
    val zone = ZoneId.systemDefault()
    val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val range = budgetRange(budget, current)
    val start = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endExclusive = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val active = transactions.filter {
        it.status in IncludedStatuses && it.occurredAt in start until endExclusive
    }
    val countInvestments = budget.countInvestmentsAsSpending
    val includedDebits = active.filter { it.countsAsSpend(countInvestments) }
    val debitCategories = transactions.filter { it.direction == Direction.DEBIT }.associate { it.id to it.categoryId.orEmpty() }
    val includedCredits = active.filter { it.isSpendRefund() }
    val debitTotal = includedDebits.sumOf { it.amountMinor }
    val creditTotal = includedCredits.sumOf { it.amountMinor }
    // Declared obligations come off the budget rather than going onto spend: `spentMinor` stays a
    // faithful record of what actually left the account, and `spendableMinor` is what's left to play
    // with. `remaining` works out identically either way — only the story the numbers tell changes.
    val obligationsMinor = budget.obligationsMinor(range)
    val spendable = if (budget.amountMinor > 0L) (budget.amountMinor - obligationsMinor).coerceAtLeast(0L) else 0L
    val spent = debitTotal - creditTotal
    val remaining = spendable - spent
    val day = (current.toEpochDay() - range.start.toEpochDay() + 1).toInt().coerceIn(1, range.days)
    val daysRemaining = (range.days - day + 1).coerceAtLeast(1)

    val daily = active.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }.mapValues { (_, items) -> items.sumOf { it.netSpend(countInvestments) } }
        .filterValues { it != 0L }

    val spentToday = daily[current] ?: 0L
    val remainingBeforeToday = spendable - (spent - spentToday)
    val safeToday = remainingBeforeToday / daysRemaining - spentToday
    val categoryTotals = mutableMapOf<String, Long>()
    includedDebits.forEach { transaction ->
        val key = transaction.categoryId.orEmpty()
        categoryTotals[key] = (categoryTotals[key] ?: 0L) + transaction.amountMinor
    }
    includedCredits.forEach { credit ->
        val key = credit.refundOfId?.let(debitCategories::get) ?: credit.categoryId.orEmpty()
        categoryTotals[key] = (categoryTotals[key] ?: 0L) - credit.amountMinor
    }

    return DashboardSnapshot(
        spentMinor = spent,
        refundedMinor = creditTotal,
        remainingMinor = remaining,
        safeTodayMinor = safeToday,
        dayOfPeriod = day,
        daysInPeriod = range.days,
        categoryTotals = categoryTotals,
        range = range,
        dailyTotals = daily,
        obligationsMinor = obligationsMinor,
        spendableMinor = spendable,
    )
}

private val IncludedStatuses = setOf(TransactionStatus.CONFIRMED, TransactionStatus.CATEGORY_REVIEW)

fun categoryLimitStatuses(
    transactions: List<Transaction>,
    budget: Budget,
    now: Long = System.currentTimeMillis(),
): List<CategoryLimitStatus> {
    val totals = dashboard(transactions, budget, now).categoryTotals
    return budget.categoryLimits.mapNotNull { (categoryId, limit) ->
        if (limit <= 0 || category(categoryId)?.notSpending != false) return@mapNotNull null
        val spent = totals[categoryId] ?: 0L
        CategoryLimitStatus(
            categoryId = categoryId,
            spentMinor = spent,
            limitMinor = limit,
            level = when {
                spent >= limit -> CategoryLimitLevel.EXCEEDED
                spent * 100 >= limit * 80 -> CategoryLimitLevel.WARNING
                else -> CategoryLimitLevel.NORMAL
            },
        )
    }.sortedWith(compareByDescending<CategoryLimitStatus> { it.level }.thenByDescending { it.fraction })
}
