package com.prudvi.trackbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateWidget(context, manager, id)
    }

    companion object {
        private enum class WidgetSize { Small, Medium, Large }
        private data class RecentRow(val merchant: String, val amount: String, val color: Int)
        private data class WidgetData(
            val period: String,
            val days: String,
            val label: String,
            val amount: String,
            val summary: String,
            val color: Int,
            val progress: Int,
            val showProgress: Boolean,
            val rows: List<RecentRow>,
            val reviewCount: Int,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java)).forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val size = chooseSize(manager.getAppWidgetOptions(id))
            val data = loadData(context.applicationContext)
            val views = RemoteViews(context.packageName, layoutFor(size)).apply {
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, id))
                when (size) {
                    WidgetSize.Small -> bindSmall(data)
                    WidgetSize.Medium -> bindMedium(data)
                    WidgetSize.Large -> bindLarge(data)
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun chooseSize(options: Bundle): WidgetSize {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (width == 0 && height == 0) return WidgetSize.Medium
            return when {
                width >= 220 && height >= 180 -> WidgetSize.Large
                width >= 180 || height >= 150 -> WidgetSize.Medium
                else -> WidgetSize.Small
            }
        }

        private fun layoutFor(size: WidgetSize): Int = when (size) {
            WidgetSize.Small -> R.layout.widget_budget_small
            WidgetSize.Medium -> R.layout.widget_budget_medium
            WidgetSize.Large -> R.layout.widget_budget_large
        }

        private fun RemoteViews.bindSmall(data: WidgetData) {
            setTextViewText(R.id.widget_small_label, data.label)
            setTextViewText(R.id.widget_amount, data.amount)
            setTextColor(R.id.widget_amount, data.color)
            setViewVisibility(R.id.widget_progress, if (data.showProgress) View.VISIBLE else View.GONE)
            setProgressBar(R.id.widget_progress, 1000, data.progress, false)
        }

        private fun RemoteViews.bindMedium(data: WidgetData) {
            setTextViewText(R.id.widget_period_label, data.period)
            setTextViewText(R.id.widget_days_label, data.days)
            setTextViewText(R.id.widget_hero_line, data.amount)
            setTextColor(R.id.widget_hero_line, data.color)
            setTextViewText(R.id.widget_summary, data.summary)
            setViewVisibility(R.id.widget_progress, if (data.showProgress) View.VISIBLE else View.GONE)
            setProgressBar(R.id.widget_progress, 1000, data.progress, false)
        }

        private fun RemoteViews.bindLarge(data: WidgetData) {
            bindMedium(data)
            val rowIds = intArrayOf(R.id.widget_recent_row_1, R.id.widget_recent_row_2, R.id.widget_recent_row_3)
            val merchantIds = intArrayOf(R.id.widget_recent_merchant_1, R.id.widget_recent_merchant_2, R.id.widget_recent_merchant_3)
            val amountIds = intArrayOf(R.id.widget_recent_amount_1, R.id.widget_recent_amount_2, R.id.widget_recent_amount_3)
            setViewVisibility(R.id.widget_empty_recent, if (data.rows.isEmpty()) View.VISIBLE else View.GONE)
            rowIds.forEachIndexed { index, rowId ->
                val row = data.rows.getOrNull(index)
                setViewVisibility(rowId, if (row == null) View.GONE else View.VISIBLE)
                if (row != null) {
                    setTextViewText(merchantIds[index], row.merchant)
                    setTextViewText(amountIds[index], row.amount)
                    setTextColor(amountIds[index], row.color)
                }
            }
            setViewVisibility(R.id.widget_review_warning, if (data.reviewCount > 0) View.VISIBLE else View.GONE)
            if (data.reviewCount > 0) {
                setTextViewText(R.id.widget_review_warning, "${data.reviewCount} ${if (data.reviewCount == 1) "receipt" else "receipts"} need you")
            }
        }

        private fun loadData(context: Context): WidgetData {
            val repository = TrackRepository(context)
            val transactions = repository.transactions.value
            val budget = repository.budget
            val snapshot = dashboard(transactions, budget)
            val preferences = repository.receipts.preferencesFlow.value
            val goals = repository.receipts.goals.value
            val ink = ContextCompat.getColor(context, R.color.receipt_ink)
            val ultra = ContextCompat.getColor(context, R.color.receipt_ultramarine)
            val daysLeft = (snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(1)
            val rolling = budget.period == "Rolling"
            val rollingDaily = if (rolling) rollingDailyMedian(transactions, snapshot.range.start) else 0L
            val paceBudget = if (rolling) rollingDaily * snapshot.daysInPeriod else budget.amountMinor
            val marker = if (rolling) paceBudget.toFloat() / maxOf(snapshot.spentMinor, paceBudget, 1L)
            else snapshot.dayOfPeriod.toFloat() / snapshot.daysInPeriod.coerceAtLeast(1)
            val target = if (rolling) paceBudget.coerceAtLeast(1L).toFloat() else (budget.amountMinor * marker).coerceAtLeast(1f)
            val ratio = snapshot.spentMinor / target
            val state = when {
                ratio < .92f -> "under pace"
                ratio > 1.08f -> "ahead of pace"
                else -> "on pace"
            }
            val goal = goals.filterNot { it.isComplete }
                .minWithOrNull(compareBy<com.prudvi.trackbudget.model.Goal> { it.targetEpochDay ?: Long.MAX_VALUE }.thenBy { it.createdAt })
            val spentLabel = when (budget.period) {
                "Week" -> "SPENT THIS WEEK"
                "Rolling" -> "SPENT IN 30 DAYS"
                "Custom" -> "SPENT THIS PERIOD"
                else -> "SPENT THIS MONTH"
            }
            val paceDetail = when {
                rolling -> "${money(rollingDaily)}/day is usual"
                snapshot.remainingMinor >= 0 -> "${money(snapshot.remainingMinor / daysLeft)}/day holds this"
                else -> "${money(-snapshot.remainingMinor)} past this budget"
            }
            val modeData = when (preferences.mode) {
                AppMode.CHILL -> Triple(spentLabel, money(snapshot.spentMinor), "Receipts collected: ${transactions.count { it.status == TransactionStatus.CONFIRMED }}")
                AppMode.PACE -> Triple(spentLabel, money(snapshot.spentMinor), "$state · $paceDetail")
                AppMode.STACK -> Triple(goal?.name?.uppercase() ?: "TOP GOAL", goal?.let { money(it.savedMinor) } ?: money(0), goal?.let { "${(it.progress * 100).roundToInt()}% of ${money(it.targetMinor)}" } ?: "Add a goal in Receipts")
            }
            val rows = transactions.filter { it.status == TransactionStatus.CONFIRMED }.take(3).map {
                val credit = it.direction == Direction.CREDIT
                RecentRow(it.merchant, (if (credit) "+" else "−") + money(it.amountMinor), if (credit) ultra else ink)
            }
            return WidgetData(
                period = periodLabel(snapshot.range).uppercase(Locale.ENGLISH),
                days = if (rolling) "30-day view" else "$daysLeft ${if (daysLeft == 1) "day" else "days"} left",
                label = modeData.first,
                amount = modeData.second,
                summary = modeData.third,
                color = if (preferences.mode == AppMode.STACK) ContextCompat.getColor(context, R.color.receipt_chrome) else ink,
                progress = when (preferences.mode) {
                    AppMode.STACK -> ((goal?.progress ?: 0f) * 1000).roundToInt()
                    else -> if (paceBudget <= 0) 0 else (snapshot.spentMinor.toDouble() / paceBudget * 1000).roundToInt().coerceIn(0, 1000)
                },
                showProgress = when (preferences.mode) {
                    AppMode.CHILL -> false
                    AppMode.PACE -> paceBudget > 0
                    AppMode.STACK -> goal != null
                },
                rows = rows,
                reviewCount = transactions.count { it.status in setOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.NEEDS_RESOLUTION, TransactionStatus.UNPARSEABLE) },
            )
        }

        private fun rollingDailyMedian(transactions: List<Transaction>, currentStart: LocalDate): Long {
            val zone = ZoneId.systemDefault()
            val start = currentStart.minusDays(60)
            val totals = transactions.asSequence()
                .filter { it.direction == Direction.DEBIT && it.status == TransactionStatus.CONFIRMED && category(it.categoryId)?.notSpending != true }
                .map { it to Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
                .filter { (_, date) -> !date.isBefore(start) && date.isBefore(currentStart) }
                .groupBy({ it.second }, { it.first.amountMinor })
                .mapValues { it.value.sum() }
            val daily = List(60) { totals[start.plusDays(it.toLong())] ?: 0L }.sorted()
            return (daily[29] + daily[30]) / 2
        }

        private fun money(minor: Long): String {
            val formatter = NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = if (abs(minor) % 100 == 0L) 0 else 2
            }
            return "₹${formatter.format(abs(minor) / 100.0)}"
        }

        private fun openAppIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
