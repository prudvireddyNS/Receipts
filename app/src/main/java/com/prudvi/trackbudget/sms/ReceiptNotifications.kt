package com.prudvi.trackbudget.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionStatus

private val ReviewStatuses = setOf(
    TransactionStatus.CATEGORY_REVIEW,
    TransactionStatus.NEEDS_REVIEW,
    TransactionStatus.NEEDS_RESOLUTION,
    TransactionStatus.UNPARSEABLE,
)

/** Notifications for live SMS receipts that need user review. */
object ReceiptNotifications {
    fun maybeNotifyReview(context: Context, transaction: Transaction) {
        if (transaction.status !in ReviewStatuses) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = when (transaction.status) {
            TransactionStatus.NEEDS_RESOLUTION -> "Refund, transfer, or money in?"
            TransactionStatus.CATEGORY_REVIEW -> "${transaction.merchant} · check the category"
            TransactionStatus.UNPARSEABLE -> "Couldn't read the amount — fill it in"
            else -> "${transaction.merchant} · pick a category"
        }
        val openApp = PendingIntent.getActivity(
            context,
            transaction.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
                putExtra(MainActivity.EXTRA_REVIEW_TRANSACTION_ID, transaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, "review")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("A receipt needs you")
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(transaction.id.hashCode(), notification)
    }
}
