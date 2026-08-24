package com.prudvi.trackbudget

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.data.TrackRepository
import com.prudvi.trackbudget.sms.SmsSyncJobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackBudgetApplication : Application() {
    val repository by lazy { TrackRepository(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var smsObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    "credits",
                    "Money received",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Credits that need to be identified"
                },
                NotificationChannel(
                    "category_limits",
                    "Pace and limits",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Pace and category-limit updates you choose to receive"
                },
            ),
        )

        val scheduler = getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(SMS_SYNC_JOB_ID) == null) {
            scheduler.schedule(
                JobInfo.Builder(SMS_SYNC_JOB_ID, ComponentName(this, SmsSyncJobService::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }
        startSmsObserver()
    }

    fun startSmsObserver() {
        if (smsObserver != null || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                applicationScope.launch { repository.importInbox() }
            }
        }.also {
            contentResolver.registerContentObserver(Telephony.Sms.Inbox.CONTENT_URI, true, it)
        }
    }

    private companion object {
        const val SMS_SYNC_JOB_ID = 42017
    }
}
