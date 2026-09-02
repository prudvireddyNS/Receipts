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
    /**
     * Skipped when working out the daily pace. Shown as "SKIP" on a receipt, and set by the
     * "Skip in daily pace" checkbox.
     *
     * The payment still counts in full towards what was spent — it is only held out of
     * [DashboardSnapshot.dailyTotals], so a single lumpy purchase cannot crater "safe to spend
     * today" or pose as a typical day in the projection.
     *
     * Distinct from [Commitment], the declared monthly obligation taken off the budget up front.
     * The column keeps its original `committed` name so existing rows keep working.
     */
    val committed: Boolean = false,
)

/**
 * Auto-detects whether a new transaction is a "committed" spend (rent, a lump transfer, an
 * annual premium): tracked as spending, but excluded from day-by-day pacing so it doesn't
 * crater "safe to spend today". Used for SMS-parsed transactions, which have no user checkbox.
 */
fun autoDetectCommitted(
    categoryId: String?,
    merchant: String,
    amountMinor: Long,
    direction: Direction,
    history: List<Transaction>,
    committedCategoryIds: Set<String>,
): Boolean {
    if (direction != Direction.DEBIT) return false
    if (categoryId in committedCategoryIds) return true
    val normalizedMerchant = merchant.trim().lowercase()
    // A standing charge repeats in *different months* at *roughly the same amount*. Merely having
    // paid a merchant before is not enough: a favourite lunch place is visited far more often than
    // rent is paid, and treating it as fixed quietly dropped everyday food out of daily pacing.
    if (normalizedMerchant.isNotBlank() && amountMinor >= StandingChargeFloorMinor) {
        val sameMerchant = history.filter {
            it.direction == Direction.DEBIT &&
                it.status != TransactionStatus.EXCLUDED &&
                it.merchant.trim().lowercase() == normalizedMerchant
        }
        val steady = sameMerchant.filter { withinTolerance(it.amountMinor, amountMinor) }
        val distinctMonths = steady.map {
            Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)
        }.distinct().size
        if (distinctMonths >= 2) return true
    }
    val recentDebits = history.filter { it.direction == Direction.DEBIT && it.status != TransactionStatus.EXCLUDED }
    if (recentDebits.size >= 5) {
        val amounts = recentDebits.map { it.amountMinor }.sorted()
        val median = amounts[amounts.size / 2]
        if (median > 0 && amountMinor >= median * 4 && amountMinor >= 300_000L) return true
    }
    return false
}

/** Below this, a repeating merchant is a habit, not an obligation. */
private const val StandingChargeFloorMinor = 100_000L

/** Two charges count as the same standing amount when they differ by no more than an eighth. */
private fun withinTolerance(a: Long, b: Long): Boolean {
    val larger = maxOf(a, b)
    return larger > 0 && kotlin.math.abs(a - b) * 8 <= larger
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
 */
/**
 * Note: an obligation is a *declared* amount, so if the matching debit is also captured (a rent SMS,
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
    /** Committed *transactions* that have actually landed (rent debit, a lump transfer). Part of [spentMinor]. */
    val committedMinor: Long = 0,
    /**
     * Committed spend keyed by the day it actually landed. [dailyTotals] leaves it out so one lump
     * payment can't crater daily pacing; a burn-up needs it back, on the right day, or the curve
     * tells a story that never happened.
     */
    val committedDailyTotals: Map<LocalDate, Long> = emptyMap(),
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
    val includedDebits = active.filter { transaction ->
        transaction.direction == Direction.DEBIT &&
            (budget.countInvestmentsAsSpending || transaction.categoryId != "investment")
    }
    val debitCategories = transactions.filter { it.direction == Direction.DEBIT }.associate { it.id to it.categoryId.orEmpty() }
    val includedCredits = active.filter { it.direction == Direction.CREDIT }
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
    val committedMinor = includedDebits.filter { it.committed }.sumOf { it.amountMinor }
    val committedDaily = includedDebits.filter { it.committed }
        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        .mapValues { (_, items) -> items.sumOf { it.amountMinor } }

    // Day-by-day pacing excludes committed spend (rent, lump transfers, annual premiums) so a
    // single big payment doesn't crater "safe to spend today" — it still counts in `spent` above.
    val daily = active.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }.mapValues { (_, items) ->
        items.sumOf { transaction ->
            when {
                transaction.direction == Direction.DEBIT && !transaction.committed && (budget.countInvestmentsAsSpending || transaction.categoryId != "investment") -> transaction.amountMinor
                transaction.direction == Direction.CREDIT -> -transaction.amountMinor
                else -> 0L
            }
        }
    }.filterValues { it != 0L }.toMutableMap()

    // A skipped receipt counts towards what was spent and nothing else. So today's allowance is
    // worked out from the balance as it stood this morning with only *paced* spending added back:
    // the skipped money is gone from the pool like any other payment, it simply never charges
    // itself to a particular day. Adding it back (as this once did) handed today a bigger allowance
    // than an average day, immediately after a large payment.
    val pacedSpentToday = daily[current] ?: 0L
    val remainingBeforeToday = spendable - (spent - pacedSpentToday)
    val safeToday = remainingBeforeToday / daysRemaining - pacedSpentToday
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
        committedMinor = committedMinor,
        committedDailyTotals = committedDaily,
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
