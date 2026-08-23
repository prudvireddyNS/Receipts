package com.prudvi.trackbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.dashboard
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private val textPrimary = Color.parseColor("#E8EBEE")
        private val clay = Color.parseColor("#E0705A")
        private val mint = Color.parseColor("#3FA894")

        private enum class WidgetSize { Small, Medium, Large }

        private data class RecentRow(
            val merchant: String,
            val amount: String,
            val color: Int,
        )

        private data class WidgetData(
            val periodLabel: String,
            val daysLeftLabel: String,
            val label: String,
            val heroAmount: String,
            val heroLine: String,
            val heroColor: Int,
            val spentBudgetLine: String,
            val progress: Int,
            val recentRows: List<RecentRow>,
            val reviewCount: Int,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java)).forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val size = chooseSize(options)
            val data = loadData(context.applicationContext)
            val views = RemoteViews(context.packageName, layoutFor(size)).apply {
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, appWidgetId))
                when (size) {
                    WidgetSize.Small -> bindSmall(data)
                    WidgetSize.Medium -> bindMedium(data)
                    WidgetSize.Large -> bindLarge(data)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun chooseSize(options: Bundle): WidgetSize {
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (minWidth == 0 && minHeight == 0) return WidgetSize.Medium
            return when {
                minWidth >= 220 && minHeight >= 180 -> WidgetSize.Large
                minWidth >= 180 || minHeight >= 150 -> WidgetSize.Medium
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
            setTextViewText(R.id.widget_amount, data.heroAmount)
            setTextColor(R.id.widget_amount, data.heroColor)
            setProgressBar(R.id.widget_progress, 1000, data.progress, false)
        }

        private fun RemoteViews.bindMedium(data: WidgetData) {
            setTextViewText(R.id.widget_period_label, data.periodLabel)
            setTextViewText(R.id.widget_days_label, data.daysLeftLabel)
            setTextViewText(R.id.widget_hero_line, data.heroLine)
            setTextColor(R.id.widget_hero_line, data.heroColor)
            setTextViewText(R.id.widget_summary, data.spentBudgetLine)
            setProgressBar(R.id.widget_progress, 1000, data.progress, false)
        }

        private fun RemoteViews.bindLarge(data: WidgetData) {
            bindMedium(data)
            val rowIds = intArrayOf(R.id.widget_recent_row_1, R.id.widget_recent_row_2, R.id.widget_recent_row_3)
            val merchantIds = intArrayOf(R.id.widget_recent_merchant_1, R.id.widget_recent_merchant_2, R.id.widget_recent_merchant_3)
            val amountIds = intArrayOf(R.id.widget_recent_amount_1, R.id.widget_recent_amount_2, R.id.widget_recent_amount_3)

            setViewVisibility(R.id.widget_empty_recent, if (data.recentRows.isEmpty()) View.VISIBLE else View.GONE)
            rowIds.forEachIndexed { index, rowId ->
                val row = data.recentRows.getOrNull(index)
                setViewVisibility(rowId, if (row == null) View.GONE else View.VISIBLE)
                if (row != null) {
                    setTextViewText(merchantIds[index], row.merchant)
                    setTextViewText(amountIds[index], row.amount)
                    setTextColor(amountIds[index], row.color)
                }
            }

            setViewVisibility(R.id.widget_review_warning, if (data.reviewCount > 0) View.VISIBLE else View.GONE)
            if (data.reviewCount > 0) {
                setTextViewText(
                    R.id.widget_review_warning,
                    "⚠ ${data.reviewCount} ${if (data.reviewCount == 1) "needs" else "need"} a category",
                )
            }
        }

        private fun loadData(context: Context): WidgetData {
            val repository = TrackRepository(context)
            val transactions = repository.transactions.value
            val budget = repository.budget
            val snapshot = dashboard(transactions, budget)
            val today = LocalDate.now()
            val daysLeft = (snapshot.daysInPeriod - snapshot.dayOfPeriod + 1).coerceAtLeast(1)
            val overToday = snapshot.safeTodayMinor < 0
            val label = if (overToday) "OVER TODAY" else "LEFT TODAY"
            val heroAmount = money(abs(snapshot.safeTodayMinor))
            val confirmedRecent = transactions
                .filter { it.status == TransactionStatus.CONFIRMED }
                .take(3)
                .map { it.toRecentRow() }

            return WidgetData(
                periodLabel = periodLabel(budget.period, today),
                daysLeftLabel = "$daysLeft ${if (daysLeft == 1) "day" else "days"} left",
                label = label,
                heroAmount = heroAmount,
                heroLine = "$heroAmount ${if (overToday) "over today" else "left today"}",
                heroColor = if (overToday) clay else textPrimary,
                spentBudgetLine = "${money(snapshot.spentMinor)} of ${money(budget.amountMinor)} spent",
                progress = if (budget.amountMinor <= 0) 0 else ((snapshot.spentMinor.toDouble() / budget.amountMinor) * 1000).roundToInt().coerceIn(0, 1000),
                recentRows = confirmedRecent,
                reviewCount = transactions.count { it.status in listOf(TransactionStatus.NEEDS_REVIEW, TransactionStatus.UNPARSEABLE) },
            )
        }

        private fun Transaction.toRecentRow(): RecentRow {
            val credit = direction == Direction.CREDIT
            return RecentRow(
                merchant = merchant,
                amount = (if (credit) "+" else "−") + money(amountMinor),
                color = if (credit) mint else textPrimary,
            )
        }

        private fun periodLabel(period: String, today: LocalDate): String = when (period) {
            "Week" -> "THIS WEEK"
            "Custom" -> "CUSTOM"
            else -> today.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        }

        private fun money(minor: Long): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
            formatter.maximumFractionDigits = if (minor % 100 == 0L) 0 else 2
            formatter.minimumFractionDigits = 0
            return formatter.format(minor / 100.0)
        }

        private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
