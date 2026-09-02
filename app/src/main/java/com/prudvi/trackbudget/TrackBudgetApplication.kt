package com.prudvi.trackbudget

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.prudvi.trackbudget.data.TrackRepository

class TrackBudgetApplication : Application() {
    val repository by lazy { TrackRepository(this) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    "credits",
                    "Receipt review",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Receipts that need review or resolution"
                },
                NotificationChannel(
                    "budget_alerts",
                    "Budget alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when your active budget reaches 80% and 100%"
                },
            ),
        )
    }

}
