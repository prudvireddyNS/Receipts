package com.prudvi.trackbudget.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.prudvi.trackbudget.TrackBudgetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val preferences = context.getSharedPreferences("track_budget", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("sms_tracking_enabled", false)) return
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis
        val pendingResult = goAsync()
        val app = context.applicationContext as TrackBudgetApplication
        val diagnostics = preferences
        diagnostics.edit().putLong("last_sms_broadcast_at", System.currentTimeMillis()).apply()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val transaction = app.repository.addSms(sender, body, receivedAt)
                diagnostics.edit().putString("last_sms_broadcast_result", if (transaction == null) "ignored_or_duplicate" else "stored").apply()
                if (transaction == null) return@launch
                app.repository.refreshCategoryLimitAlerts()
                ReceiptNotifications.maybeNotifyReview(context, transaction)
            } catch (error: Exception) {
                diagnostics.edit().putString("last_sms_broadcast_result", "error:${error.javaClass.simpleName}").apply()
                Log.e("ReceiptsSms", "Unable to store incoming SMS", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
