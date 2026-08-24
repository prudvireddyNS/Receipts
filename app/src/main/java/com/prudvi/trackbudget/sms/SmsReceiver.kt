package com.prudvi.trackbudget.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.MainActivity
import com.prudvi.trackbudget.R
import com.prudvi.trackbudget.TrackBudgetApplication
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.TransactionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis
        val pendingResult = goAsync()
        val app = context.applicationContext as TrackBudgetApplication
        val diagnostics = context.getSharedPreferences("track_budget", Context.MODE_PRIVATE)
        diagnostics.edit().putLong("last_sms_broadcast_at", System.currentTimeMillis()).apply()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val transaction = app.repository.addSms(sender, body, receivedAt)
                diagnostics.edit().putString("last_sms_broadcast_result", if (transaction == null) "ignored_or_duplicate" else "stored").apply()
                if (transaction == null) return@launch
                if (transaction.direction == Direction.CREDIT &&
                    transaction.status == TransactionStatus.NEEDS_RESOLUTION &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    val openApp = PendingIntent.getActivity(
                        context,
                        transaction.id.hashCode(),
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val notification = Notification.Builder(context, "credits")
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("A receipt needs you")
                        .setContentText("Refund, transfer, or money in?")
                        .setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build()
                    context.getSystemService(NotificationManager::class.java)
                        .notify(transaction.id.hashCode(), notification)
                }
            } catch (error: Exception) {
                diagnostics.edit().putString("last_sms_broadcast_result", "error:${error.javaClass.simpleName}").apply()
                Log.e("ReceiptsSms", "Unable to store incoming SMS", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
