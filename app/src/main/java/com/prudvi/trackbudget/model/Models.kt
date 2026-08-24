package com.prudvi.trackbudget.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class Direction { DEBIT, CREDIT }
enum class TransactionStatus { CONFIRMED, NEEDS_REVIEW, NEEDS_RESOLUTION, UNPARSEABLE, EXCLUDED }
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

data class Budget(
    val amountMinor: Long = 30_000_00,
    val period: String = "Month",
    val repeats: Boolean = true,
    val carryOver: Boolean = false,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val categoryLimits: Map<String, Long> = emptyMap(),
    val resetDay: Int = 1,
)

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
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
        it.status == TransactionStatus.CONFIRMED && it.occurredAt in start until endExclusive
    }
    val debits = active.filter {
        it.direction == Direction.DEBIT && category(it.categoryId)?.notSpending != true
    }
    val debitIds = debits.mapTo(mutableSetOf()) { it.id }
    val refunds = active.filter {
        it.direction == Direction.CREDIT && it.categoryId == "refund" && it.refundOfId in debitIds
    }.sumOf { it.amountMinor }
    val gross = debits.sumOf { it.amountMinor }
    val spent = (gross - refunds).coerceAtLeast(0)
    val remaining = budget.amountMinor - spent
    val day = (current.toEpochDay() - range.start.toEpochDay() + 1).toInt().coerceIn(1, range.days)
    val daysRemaining = (range.days - day + 1).coerceAtLeast(1)
    val daily = debits.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }.mapValues { (_, items) -> items.sumOf { it.amountMinor } }.toMutableMap()
    active.filter { it.direction == Direction.CREDIT && it.categoryId == "refund" && it.refundOfId in debitIds }
        .forEach { credit ->
            val date = Instant.ofEpochMilli(credit.occurredAt).atZone(zone).toLocalDate()
            daily[date] = (daily[date] ?: 0L) - credit.amountMinor
        }

    val spentToday = daily[current]?.coerceAtLeast(0) ?: 0L
    val remainingBeforeToday = budget.amountMinor - (spent - spentToday)
    val safeToday = remainingBeforeToday / daysRemaining - spentToday
    val categoryTotals = debits.groupBy { it.categoryId.orEmpty() }
        .mapValues { (_, items) -> items.sumOf { it.amountMinor } }.toMutableMap()
    active.filter { it.direction == Direction.CREDIT && it.categoryId == "refund" && it.refundOfId in debitIds }
        .forEach { refund ->
            val refundedCategory = debits.firstOrNull { it.id == refund.refundOfId }?.categoryId ?: return@forEach
            categoryTotals[refundedCategory] = ((categoryTotals[refundedCategory] ?: 0L) - refund.amountMinor).coerceAtLeast(0)
        }

    return DashboardSnapshot(
        spentMinor = spent,
        refundedMinor = refunds,
        remainingMinor = remaining,
        safeTodayMinor = safeToday,
        dayOfPeriod = day,
        daysInPeriod = range.days,
        categoryTotals = categoryTotals,
        range = range,
        dailyTotals = daily,
    )
}

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
