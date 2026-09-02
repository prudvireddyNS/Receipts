package com.prudvi.trackbudget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.prudvi.trackbudget.ui.ReceiptsRoot

class MainActivity : FragmentActivity() {
    private val openReview = mutableStateOf(false)
    private var reviewTransactionId by mutableStateOf<String?>(null)
    private var unlockedForDeviceSession = false
    private var screenReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) unlockedForDeviceSession = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOffReceiver, filter)
        screenReceiverRegistered = true
        consumeReviewIntent(intent)
        setContent {
            val shouldOpenReview by openReview
            ReceiptsRoot(
                (application as TrackBudgetApplication).repository,
                openReview = shouldOpenReview,
                initialReviewTransactionId = reviewTransactionId,
                onReviewConsumed = {
                    openReview.value = false
                    reviewTransactionId = null
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAuthenticate()
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReviewIntent(intent)
    }

    private fun consumeReviewIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REVIEW, false) != true) return
        openReview.value = true
        reviewTransactionId = intent.getStringExtra(EXTRA_REVIEW_TRANSACTION_ID)
    }

    private fun maybeAuthenticate() {
        val repository = (application as TrackBudgetApplication).repository
        if (!repository.biometricLockEnabled || unlockedForDeviceSession) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlockedForDeviceSession = true
            return
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockedForDeviceSession = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!isFinishing) finish()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Receipts")
                .setSubtitle("Confirm it’s you to view your receipts")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
        const val EXTRA_REVIEW_TRANSACTION_ID = "review_transaction_id"
    }
}
