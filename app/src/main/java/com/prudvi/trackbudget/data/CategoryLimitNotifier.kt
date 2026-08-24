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
import com.prudvi.trackbudget.model.CategoryLimitLevel
import com.prudvi.trackbudget.model.Transaction
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
        val defaultMode = if (onboardingComplete) "PACE" else "CHILL"
        if (preferences.getString("mode", defaultMode) != "PACE") return
        val range = budgetRange(budget)
        val periodPrefix = "${range.start.toEpochDay()}:${range.endInclusive.toEpochDay()}:"
        val allSent = preferences.getStringSet("category_limit_alerts", emptySet()).orEmpty()
        val sent = allSent.filterTo(mutableSetOf()) { it.startsWith(periodPrefix) }
        var changed = sent.size != allSent.size
        val canNotify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val suppressInitial = !preferences.getBoolean("receipts_initialized", false)

        categoryLimitStatuses(transactions, budget).forEach { status ->
            val warningKey = "$periodPrefix${status.categoryId}:80"
            val exceededKey = "$periodPrefix${status.categoryId}:100"
            when {
                status.level == CategoryLimitLevel.EXCEEDED && exceededKey !in sent && canNotify -> {
                    if (!suppressInitial) show(status.categoryId, status.spentMinor, status.limitMinor, true, exceededKey.hashCode())
                    sent += warningKey
                    sent += exceededKey
                    changed = true
                }
                status.level == CategoryLimitLevel.WARNING && warningKey !in sent && canNotify -> {
                    if (!suppressInitial) show(status.categoryId, status.spentMinor, status.limitMinor, false, warningKey.hashCode())
                    sent += warningKey
                    changed = true
                }
            }
        }
        val snapshot = dashboard(transactions, budget)
        val paceTarget = budget.amountMinor * snapshot.dayOfPeriod / snapshot.daysInPeriod.coerceAtLeast(1)
        val paceKey = "${periodPrefix}pace:108"
        if (budget.period != "Rolling" && budget.amountMinor > 0 && snapshot.spentMinor * 100 > paceTarget * 108 && paceKey !in sent && canNotify) {
            if (!suppressInitial) showPace(periodLabel(snapshot.range), snapshot.spentMinor, snapshot.dayOfPeriod, snapshot.daysInPeriod, paceKey.hashCode())
            sent += paceKey
            changed = true
        }
        if (changed) preferences.edit().putStringSet("category_limit_alerts", sent).apply()
    }

    private fun show(categoryId: String, spent: Long, limit: Long, exceeded: Boolean, notificationId: Int) {
        val name = category(categoryId)?.name ?: "Category"
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (exceeded) "${rupees(spent)} used · ${rupees(limit)} limit"
        else "${rupees(spent)} of ${rupees(limit)} used · ${rupees(limit - spent)} left"
        val notification = Notification.Builder(context, "category_limits")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (exceeded) "$name is ahead of its limit" else "$name limit is at 80%")
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun showPace(period: String, spent: Long, day: Int, days: Int, notificationId: Int) {
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, "category_limits")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$period is ahead of pace")
            .setContentText("${rupees(spent)} recorded by day $day of $days")
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
