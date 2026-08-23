package com.prudvi.trackbudget.sms

import android.Manifest
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.prudvi.trackbudget.TrackBudgetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        job = scope.launch {
            try {
                (application as TrackBudgetApplication).repository.importInbox()
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}
