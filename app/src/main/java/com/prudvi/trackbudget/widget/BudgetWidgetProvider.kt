package com.prudvi.trackbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.TransactionStatus
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import com.prudvi.trackbudget.ui.receiptMoney
import kotlin.math.roundToInt

class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateWidget(context, manager, id)
    }

    companion object {
        private enum class WidgetSize { Medium, Large }
        private data class WidgetTheme(val backgroundResource: Int, val ink: Int, val fade: Int, val accent: Int)
        private data class WidgetData(
            val kicker: String,
            val hero: String,
            val support: String,
            val rail: Int?,
            val reviewCount: Int,
            val theme: WidgetTheme,
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
                setContentDescription(R.id.widget_root, "Receipts home. ${data.kicker}, ${data.hero}")
                setInt(R.id.widget_root, "setBackgroundResource", data.theme.backgroundResource)
                setTextViewText(R.id.widget_kicker, data.kicker)
                setTextViewText(R.id.widget_hero, data.hero)
                setTextColor(R.id.widget_kicker, data.theme.fade)
                setTextColor(R.id.widget_hero, data.theme.ink)
                setTextViewText(R.id.widget_support, data.support)
                setTextColor(R.id.widget_support, data.theme.fade)
                setViewVisibility(R.id.widget_rail, if (data.rail != null) View.VISIBLE else View.GONE)
                if (data.rail != null) {
                    setProgressBar(R.id.widget_rail, 1000, data.rail, false)
                    setContentDescription(R.id.widget_rail, "Budget ${data.rail / 10}% used")
                    if (Build.VERSION.SDK_INT >= 31) {
                        setColorStateList(R.id.widget_rail, "setProgressTintList", ColorStateList.valueOf(data.theme.accent))
                    }
                }
                setViewVisibility(R.id.widget_review, if (data.reviewCount > 0) View.VISIBLE else View.GONE)
                if (data.reviewCount > 0) {
                    val text = "${data.reviewCount} ${if (data.reviewCount == 1) "receipt" else "receipts"} need review"
                    setTextViewText(R.id.widget_review, text)
                    setContentDescription(R.id.widget_review, text)
                    setOnClickPendingIntent(R.id.widget_review, reviewIntent(context, id))
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun chooseSize(options: Bundle): WidgetSize {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (width == 0 && height == 0) return WidgetSize.Medium
            return if (width >= 220 && height >= 180) WidgetSize.Large else WidgetSize.Medium
        }

        private fun layoutFor(size: WidgetSize): Int = when (size) {
            WidgetSize.Medium -> R.layout.widget_budget_medium
            WidgetSize.Large -> R.layout.widget_budget_large
        }

        private fun loadData(context: Context): WidgetData {
            val repository = TrackRepository(context)
            val transactions = repository.transactions.value
            val activeBudget = if (repository.currentPeriodBudgetConfirmed) repository.budget else repository.budget.copy(amountMinor = 0L)
            val snapshot = dashboard(transactions, activeBudget)
            val reviewCount = transactions.count {
                it.status in setOf(
                    TransactionStatus.CATEGORY_REVIEW,
                    TransactionStatus.NEEDS_REVIEW,
                    TransactionStatus.NEEDS_RESOLUTION,
                    TransactionStatus.UNPARSEABLE,
                )
            }
            val rail = if (activeBudget.amountMinor > 0L) {
                (snapshot.spentMinor.toDouble() / activeBudget.amountMinor.coerceAtLeast(1L) * 1000).roundToInt().coerceIn(0, 1000)
            } else null
            return WidgetData(
                kicker = "${periodLabel(snapshot.range).uppercase()} NET SPENT",
                hero = if (snapshot.spentMinor < 0) "−${receiptMoney(kotlin.math.abs(snapshot.spentMinor))}" else receiptMoney(snapshot.spentMinor),
                support = if (activeBudget.amountMinor > 0L) {
                    if (snapshot.remainingMinor < 0L) "${receiptMoney(kotlin.math.abs(snapshot.remainingMinor))} over ${receiptMoney(activeBudget.amountMinor)}"
                    else "${receiptMoney(snapshot.remainingMinor)} left of ${receiptMoney(activeBudget.amountMinor)}"
                } else "No active budget",
                rail = rail,
                reviewCount = reviewCount,
                theme = widgetTheme(repository.receipts.preferencesFlow.value.theme),
            )
        }

        private fun widgetTheme(theme: AppThemePreference): WidgetTheme = when (theme) {
            AppThemePreference.DARK -> WidgetTheme(R.drawable.widget_background_dark, 0xFFF2F2F2.toInt(), 0xFF8F8F8F.toInt(), 0xFFC7C7C7.toInt())
            AppThemePreference.LIGHT -> WidgetTheme(R.drawable.widget_background_light, 0xFF121212.toInt(), 0xFF6E6E6E.toInt(), 0xFF383838.toInt())
            AppThemePreference.SUBTLE -> WidgetTheme(R.drawable.widget_background_receipts, 0xFF2E2B36.toInt(), 0xFF8B8696.toInt(), 0xFFE4C77E.toInt())
            AppThemePreference.COLORFUL -> WidgetTheme(R.drawable.widget_background_receipts, 0xFF14121F.toInt(), 0xFF6E6880.toInt(), 0xFF7B4DFF.toInt())
        }

        private fun openAppIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun reviewIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
            context,
            id + 500_000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
