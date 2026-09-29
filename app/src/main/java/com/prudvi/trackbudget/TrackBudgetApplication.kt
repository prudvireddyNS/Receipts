package com.prudvi.trackbudget

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.prudvi.trackbudget.data.TrackRepository

class TrackBudgetApplication : Application() {
    val repository by lazy { TrackRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Opening the database (and, once, migrating the old one) is the slow part of startup; do it
        // off the main thread so the first frame never waits on it.
        Thread { repository }.apply { name = "receipts-warmup"; start() }
        val manager = getSystemService(NotificationManager::class.java)
        // The first release used a heads-up "credits" channel that buzzed for every uncertain receipt.
        manager.deleteNotificationChannel("credits")
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    "review",
                    "Receipt review",
                    NotificationManager.IMPORTANCE_DEFAULT,
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
