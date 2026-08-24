package com.prudvi.trackbudget

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TrackBudgetDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ExistingUserRule()).around(compose)

    @Test
    fun primaryNavigationAndSettingsRender() {
        compose.onNodeWithText("RECEIPTS").assertIsDisplayed()

        compose.onNodeWithText("LEDGER").performClick()
        compose.onNodeWithText("Search receipts").assertIsDisplayed()

        compose.onNodeWithText("FEED").performClick()
        compose.onNodeWithText("Feed").assertIsDisplayed()

        compose.onNodeWithText("GOALS").performClick()
        compose.onNodeWithText("STAMPS").assertIsDisplayed()

        compose.onNodeWithText("TODAY").performClick()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Network access").assertIsDisplayed()
    }

    @Test
    fun manualEntryOpensWithoutWritingData() {
        compose.onNodeWithContentDescription("New receipt").performClick()
        compose.onNodeWithText("New receipt").assertIsDisplayed()
        compose.onNodeWithText("Spent").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close new receipt").performClick()
        compose.onNodeWithText("RECEIPTS").assertIsDisplayed()
    }

    private class ExistingUserRule : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getSharedPreferences("track_budget", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarding_complete", true)
                    .putString("mode", "PACE")
                    .putString("theme", "LIGHT")
                    .commit()
                base.evaluate()
            }
        }
    }
}
