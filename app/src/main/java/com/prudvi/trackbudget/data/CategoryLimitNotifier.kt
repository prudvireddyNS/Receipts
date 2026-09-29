package com.prudvi.trackbudget.data

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.model.Budget
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.CategoryLimitLevel
import com.prudvi.trackbudget.model.CategoryLimitStatus
import com.prudvi.trackbudget.model.budgetRange
import com.prudvi.trackbudget.model.category
import com.prudvi.trackbudget.model.categoryLimitStatuses
import com.prudvi.trackbudget.model.dashboard
import com.prudvi.trackbudget.model.periodLabel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal class CategoryLimitNotifier(
    private val context: Context,
    private val preferences: SharedPreferences,
) {
    fun evaluate(budget: Budget, transactions: List<Transaction>, onboardingComplete: Boolean) {
        if (!onboardingComplete || budget.amountMinor <= 0) return
        val canNotify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val range = budgetRange(budget)
        val periodPrefix = "${range.start.toEpochDay()}:${range.endInclusive.toEpochDay()}:"
        val allSent = preferences.getStringSet("category_limit_alerts", emptySet()).orEmpty()
        val sent = allSent.filterTo(mutableSetOf()) { it.startsWith(periodPrefix) }
        val snapshot = dashboard(transactions, budget)
        // Declared obligations are off the top of the budget, so the alert fires against what was
        // actually left to spend — otherwise a big rent line would never trip the 80% warning.
        val ceiling = snapshot.spendableMinor
        // Obligations that swallow the whole budget leave nothing to spend: any spending is over it.
        val percent = when {
            ceiling > 0 -> snapshot.spentMinor * 100 / ceiling
            snapshot.spentMinor > 0 -> 100L
            else -> 0L
        }
        var changed = sent.size != allSent.size

        // Keyed by the ceiling as well as the period: raising or lowering the budget mid-period is a
        // new budget, and must not inherit (or be silenced by) the alerts of the old one.
        val exceededKey = "${periodPrefix}budget:$ceiling:100"
        val warningKey = "${periodPrefix}budget:$ceiling:80"
        when {
            percent >= 100 && exceededKey !in sent -> {
                showBudget(periodLabel(snapshot.range), snapshot.spentMinor, ceiling, true, exceededKey.hashCode())
                sent += warningKey
                sent += exceededKey
                changed = true
            }
            percent >= 80 && warningKey !in sent -> {
                showBudget(periodLabel(snapshot.range), snapshot.spentMinor, ceiling, false, warningKey.hashCode())
                sent += warningKey
                changed = true
            }
        }

        categoryLimitStatuses(transactions, budget).forEach { status ->
            if (status.level == CategoryLimitLevel.NORMAL) return@forEach
            val exceeded = status.level == CategoryLimitLevel.EXCEEDED
            val base = "${periodPrefix}category:${status.categoryId}:${status.limitMinor}:"
            val key = base + if (exceeded) "100" else "80"
            if (key in sent || (exceeded.not() && base + "100" in sent)) return@forEach
            showCategory(status, exceeded, key.hashCode())
            sent += base + "80"
            if (exceeded) sent += base + "100"
            changed = true
        }
        if (changed) preferences.edit().putStringSet("category_limit_alerts", sent).apply()
    }

    private fun showCategory(status: CategoryLimitStatus, exceeded: Boolean, notificationId: Int) {
        val name = category(status.categoryId)?.name ?: status.categoryId
        val text = if (exceeded) "${rupees(status.spentMinor)} spent · ${rupees(status.limitMinor)} limit"
        else "${rupees(status.spentMinor)} of ${rupees(status.limitMinor)} · ${rupees(status.remainingMinor)} left"
        notify(notificationId, if (exceeded) "$name limit reached" else "$name is at 80%", text)
    }

    private fun showBudget(period: String, spent: Long, limit: Long, exceeded: Boolean, notificationId: Int) {
        val text = if (exceeded) "${rupees(spent)} used · ${rupees(limit)} budget"
        else "${rupees(spent)} of ${rupees(limit)} used · ${rupees(limit - spent)} left"
        notify(notificationId, if (exceeded) "$period budget reached" else "$period budget is at 80%", text)
    }

    private fun notify(notificationId: Int, title: String, text: String) {
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, "budget_alerts")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun rupees(minor: Long): String = "₹" + NumberFormat.getIntegerInstance(
        Locale.Builder().setLanguage("en").setRegion("IN").build(),
    ).format(abs(minor) / 100)
}
