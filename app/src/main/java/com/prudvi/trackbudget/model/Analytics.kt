package com.prudvi.trackbudget.model

fun periodLabel(range: BudgetRange): String = if (range.start.month == range.endInclusive.month) {
    range.start.month.name.lowercase().replaceFirstChar(Char::titlecase)
} else {
    "${range.start.dayOfMonth} ${range.start.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase)} – " +
        "${range.endInclusive.dayOfMonth} ${range.endInclusive.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase)}"
}
