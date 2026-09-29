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

        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText("Search receipts").assertIsDisplayed()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("BUDGET & PERIOD").assertIsDisplayed()
    }

    @Test
    fun manualEntryOpensWithoutWritingData() {
        compose.onNodeWithContentDescription("Add receipt").performClick()
        compose.onNodeWithText("Add").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close add").performClick()
        compose.onNodeWithText("RECEIPTS").assertIsDisplayed()
    }

    private class ExistingUserRule : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getSharedPreferences("track_budget", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarding_complete", true)
                    .putString("theme", "LIGHT")
                    .commit()
                base.evaluate()
            }
        }
    }
}
