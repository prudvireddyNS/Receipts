package com.prudvi.trackbudget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptsRulesTest {
    @Test
    fun monthlyBudgetHonoursResetDayWithoutChangingDefault() {
        val defaultRange = budgetRange(Budget(), LocalDate.of(2026, 8, 19))
        assertEquals(LocalDate.of(2026, 8, 1), defaultRange.start)
        assertEquals(LocalDate.of(2026, 8, 31), defaultRange.endInclusive)

        val shiftedBeforeReset = budgetRange(Budget(resetDay = 20), LocalDate.of(2026, 8, 19))
        assertEquals(LocalDate.of(2026, 7, 20), shiftedBeforeReset.start)
        assertEquals(LocalDate.of(2026, 8, 19), shiftedBeforeReset.endInclusive)

        val shiftedOnReset = budgetRange(Budget(resetDay = 20), LocalDate.of(2026, 8, 20))
        assertEquals(LocalDate.of(2026, 8, 20), shiftedOnReset.start)
        assertEquals(LocalDate.of(2026, 9, 19), shiftedOnReset.endInclusive)
    }

    @Test
    fun rollingBudgetUsesTrailingThirtyDays() {
        val range = budgetRange(Budget(period = "Rolling"), LocalDate.of(2026, 8, 20))

        assertEquals(LocalDate.of(2026, 7, 22), range.start)
        assertEquals(LocalDate.of(2026, 8, 20), range.endInclusive)
        assertEquals(30, range.days)
    }
}
