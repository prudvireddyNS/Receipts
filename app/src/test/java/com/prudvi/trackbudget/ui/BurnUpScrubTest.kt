package com.prudvi.trackbudget.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrub hit-test on the burn-up chart. `burnUpDayAt` is the inverse of the chart's `xOf`, so
 * these check that round trip at both edges of the plot and across a month and a week.
 */
class BurnUpScrubTest {
    private val padLeft = 3f * 2.75f
    private val plotWidth = 349f * 2.75f - padLeft * 2f

    /** The chart's own `xOf`, so the test asserts against the geometry the curve is drawn with. */
    private fun xOf(day: Int, days: Int): Float =
        if (days == 1) padLeft + plotWidth / 2f else padLeft + (day - 1).toFloat() / (days - 1) * plotWidth

    private fun dayAt(x: Float, days: Int, elapsed: Int) = burnUpDayAt(x, padLeft, plotWidth, days, elapsed)

    @Test
    fun everyDrawnPointRoundTripsToItsOwnDay() {
        for ((days, elapsed) in listOf(31 to 31, 31 to 14, 30 to 17, 28 to 28, 7 to 7, 7 to 4)) {
            for (day in 1..elapsed) {
                assertEquals("days=$days elapsed=$elapsed", day, dayAt(xOf(day, days), days, elapsed))
            }
        }
    }

    @Test
    fun bothEdgesAreReachable() {
        for ((days, elapsed) in listOf(31 to 31, 31 to 14, 7 to 7, 7 to 4)) {
            assertEquals(1, dayAt(0f, days, elapsed))
            assertEquals(1, dayAt(padLeft, days, elapsed))
            assertEquals(elapsed, dayAt(xOf(elapsed, days), days, elapsed))
            assertEquals(elapsed, dayAt(padLeft + plotWidth * 2f, days, elapsed))
        }
    }

    @Test
    fun thereIsNoHalfStepBias() {
        val days = 31
        val step = plotWidth / (days - 1)
        for (day in 1 until days) {
            val midpoint = xOf(day, days) + step / 2f
            assertEquals("before midpoint $day", day, dayAt(midpoint - 1f, days, days))
            assertEquals("after midpoint $day", day + 1, dayAt(midpoint + 1f, days, days))
        }
    }

    @Test
    fun scrubbingNeverReachesTheFuture() {
        val days = 31
        val elapsed = 14
        val seen = (0..(padLeft + plotWidth).toInt()).map { dayAt(it.toFloat(), days, elapsed) }.toSet()
        assertEquals((1..elapsed).toSet(), seen)
    }

    @Test
    fun aWeekPeriodExposesEveryElapsedDay() {
        val seen = (0..(padLeft + plotWidth).toInt()).map { dayAt(it.toFloat(), 7, 7) }.toSet()
        assertEquals((1..7).toSet(), seen)
    }

    @Test
    fun aDegenerateSingleDayPeriodAlwaysSelectsDayOne() {
        assertEquals(1, dayAt(0f, 1, 1))
        assertEquals(1, dayAt(xOf(1, 1), 1, 1))
        assertEquals(1, dayAt(padLeft + plotWidth, 1, 1))
        assertEquals(1, dayAt(50f, 0, 0))
    }

    @Test
    fun everyDayOfAMonthIsAUsableTouchTarget() {
        val days = 31
        val hits = (0..(padLeft + plotWidth).toInt()).groupingBy { dayAt(it.toFloat(), days, days) }.eachCount()
        // Interior days get a full step; the two ends get half of one. Nothing may be a sliver.
        for (day in 2 until days) {
            assertTrue("day $day was only ${hits[day]}px wide", (hits[day] ?: 0) >= 20)
        }
    }
}
