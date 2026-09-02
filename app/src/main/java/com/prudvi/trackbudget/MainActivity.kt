package com.prudvi.trackbudget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.prudvi.trackbudget.ui.ReceiptsRoot
import com.prudvi.trackbudget.widget.BudgetWidgetProvider

class MainActivity : FragmentActivity() {
    private val openReview = mutableStateOf(false)
    private var reviewTransactionId by mutableStateOf<String?>(null)
    // Compose state, not a plain flag: the shell is only composed once this flips, so a locked app
    // never draws its contents behind the prompt or into the recents thumbnail.
    private var unlockedForDeviceSession by mutableStateOf(false)
    private var promptShowing = false
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
        val repository = (application as TrackBudgetApplication).repository
        // Cheap, and the one moment we know the user is looking at fresh figures in the app while
        // the home-screen card may still be showing yesterday's.
        BudgetWidgetProvider.updateAll(this)
        // Keeps the locked app out of the recents preview too, not just off the screen.
        if (repository.biometricLockEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val shouldOpenReview by openReview
            if (repository.biometricLockEnabled && !unlockedForDeviceSession) {
                LockVeil()
            } else {
                ReceiptsRoot(
                    repository,
                    openReview = shouldOpenReview,
                    initialReviewTransactionId = reviewTransactionId,
                    onReviewConsumed = {
                        openReview.value = false
                        reviewTransactionId = null
                    },
                )
            }
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
        if (!repository.biometricLockEnabled || unlockedForDeviceSession || promptShowing) return
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
                    promptShowing = false
                    unlockedForDeviceSession = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    if (!isFinishing) finish()
                }
            },
        ).also { promptShowing = true }.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Receipts")
                .setSubtitle("Confirm it’s you to view your receipts")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    @Composable
    private fun LockVeil() {
        Box(Modifier.fillMaxSize().background(Color(0xFF121212)))
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
        const val EXTRA_REVIEW_TRANSACTION_ID = "review_transaction_id"
    }
}
