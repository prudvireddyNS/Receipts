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
import com.prudvi.trackbudget.model.budgetRange
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
        val periodPrefix = "${range.start.toEpochDay()}:${range.endInclusive.toEpochDay()}:budget:"
        val allSent = preferences.getStringSet("category_limit_alerts", emptySet()).orEmpty()
        val sent = allSent.filterTo(mutableSetOf()) { it.startsWith(periodPrefix) }
        val snapshot = dashboard(transactions, budget)
        // Declared obligations are off the top of the budget, so the alert fires against what was
        // actually left to spend — otherwise a big rent line would never trip the 80% warning.
        val ceiling = snapshot.spendableMinor
        if (ceiling <= 0) return
        val percent = snapshot.spentMinor * 100 / ceiling
        var changed = sent.size != allSent.size

        val exceededKey = "${periodPrefix}100"
        val warningKey = "${periodPrefix}80"
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
        if (changed) preferences.edit().putStringSet("category_limit_alerts", sent).apply()
    }

    private fun showBudget(period: String, spent: Long, limit: Long, exceeded: Boolean, notificationId: Int) {
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (exceeded) "${rupees(spent)} used · ${rupees(limit)} budget"
        else "${rupees(spent)} of ${rupees(limit)} used · ${rupees(limit - spent)} left"
        val notification = Notification.Builder(context, "budget_alerts")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (exceeded) "$period budget reached" else "$period budget is at 80%")
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
