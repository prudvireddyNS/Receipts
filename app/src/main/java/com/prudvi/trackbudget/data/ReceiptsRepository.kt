package com.prudvi.trackbudget.data

import android.content.Context
import android.content.SharedPreferences
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.AppMode
import com.prudvi.trackbudget.model.AppThemePreference
import com.prudvi.trackbudget.model.DefaultCommittedCategoryIds
import com.prudvi.trackbudget.model.MoneyRhythm
import com.prudvi.trackbudget.model.ReceiptsPreferences
import com.prudvi.trackbudget.widget.BudgetWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ReceiptsRepository(
    context: Context,
    private val preferences: SharedPreferences,
) {
    private val appContext = context.applicationContext
    private val _preferences = MutableStateFlow(loadPreferences())

    val preferencesFlow: StateFlow<ReceiptsPreferences> = _preferences

    @Synchronized
    fun updatePreferences(value: ReceiptsPreferences) {
        val rhythm = if (value.rhythm == MoneyRhythm.WEEKLY) MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
        val clean = value.copy(
            rhythm = rhythm,
            resetDay = value.resetDay.coerceIn(if (rhythm == MoneyRhythm.WEEKLY) 1..7 else 1..28),
        )
        val rhythmChanged = clean.rhythm != _preferences.value.rhythm || clean.resetDay != _preferences.value.resetDay
        preferences.edit()
            .putString(RHYTHM, clean.rhythm.name)
            .putInt(RESET_DAY, clean.resetDay)
            .putString(THEME, clean.theme.name)
            .putBoolean(THEME_V2, true)
            .putBoolean(SMS_TRACKING_ENABLED, clean.smsTrackingEnabled)
            .putBoolean(COUNT_INVESTMENTS_AS_SPENDING, clean.countInvestmentsAsSpending)
            .putBoolean(BIOMETRIC_LOCK_ENABLED, clean.biometricLockEnabled)
            .putStringSet(COMMITTED_CATEGORY_IDS, clean.committedCategoryIds)
            .apply {
                if (rhythmChanged) {
                    putString("budget_period", if (clean.rhythm == MoneyRhythm.WEEKLY) "Week" else "Month")
                    remove("budget_confirmed_key")
                }
            }
            .apply()
        _preferences.value = clean
        BudgetWidgetProvider.updateAll(appContext)
    }

    private fun loadPreferences(): ReceiptsPreferences {
        val rhythm = when (preferences.getString(RHYTHM, null)) {
            MoneyRhythm.WEEKLY.name -> MoneyRhythm.WEEKLY
            else -> if (preferences.getString("budget_period", "Month") == "Week") MoneyRhythm.WEEKLY else MoneyRhythm.MONTHLY
        }
        return ReceiptsPreferences(
            mode = AppMode.CHILL,
            rhythm = rhythm,
            amplitude = AppAmplitude.QUIET,
            resetDay = preferences.getInt(RESET_DAY, 1).coerceIn(if (rhythm == MoneyRhythm.WEEKLY) 1..7 else 1..28),
            theme = loadTheme(),
            smsTrackingEnabled = preferences.getBoolean(SMS_TRACKING_ENABLED, false),
            countInvestmentsAsSpending = preferences.getBoolean(COUNT_INVESTMENTS_AS_SPENDING, false),
            biometricLockEnabled = preferences.getBoolean(BIOMETRIC_LOCK_ENABLED, false),
            committedCategoryIds = preferences.getStringSet(COMMITTED_CATEGORY_IDS, null) ?: DefaultCommittedCategoryIds,
        )
    }

    private fun loadTheme(): AppThemePreference {
        if (!preferences.getBoolean(THEME_V2, false)) {
            preferences.edit().putString(THEME, AppThemePreference.COLORFUL.name).putBoolean(THEME_V2, true).apply()
            return AppThemePreference.COLORFUL
        }
        return when (preferences.getString(THEME, AppThemePreference.COLORFUL.name)) {
            AppThemePreference.LIGHT.name -> AppThemePreference.LIGHT
            AppThemePreference.DARK.name -> AppThemePreference.DARK
            AppThemePreference.SUBTLE.name -> AppThemePreference.SUBTLE
            else -> AppThemePreference.COLORFUL
        }
    }

    private companion object {
        const val RHYTHM = "rhythm"
        const val RESET_DAY = "reset_day"
        const val THEME = "theme"
        const val THEME_V2 = "theme_palette_v2"
        const val SMS_TRACKING_ENABLED = "sms_tracking_enabled"
        const val COUNT_INVESTMENTS_AS_SPENDING = "count_investments_as_spending"
        const val BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
        const val COMMITTED_CATEGORY_IDS = "committed_category_ids"
    }
}
