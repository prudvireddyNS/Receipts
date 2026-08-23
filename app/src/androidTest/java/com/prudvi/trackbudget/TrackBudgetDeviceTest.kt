package com.prudvi.trackbudget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TrackBudgetDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAndPrimaryNavigationRender() {
        compose.onNodeWithText("Home").assertIsDisplayed()

        compose.onNodeWithText("Timeline").performClick()
        compose.onNodeWithText("Search merchant, amount, note…").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("DAILY SPEND").assertIsDisplayed()

        compose.onNodeWithText("Insights").performClick()
        compose.onNodeWithText("ON THIS PACE", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Privacy & data").assertIsDisplayed().performClick()
        compose.onNodeWithText("SMS permission").assertIsDisplayed()
        compose.onNodeWithText("Past inbox import").assertIsDisplayed()
    }

    @Test
    fun reviewCreditAndBudgetSheetsOpenWithoutMutation() {
        compose.onNode(
            hasText("from", substring = true) and hasText("₹", substring = true) and hasClickAction(),
        ).performClick()
        compose.onNodeWithText("Refund").assertIsDisplayed()
        compose.onNodeWithText("×").performClick()

        compose.onNodeWithText("Review →").performClick()
        compose.onNodeWithText("CATEGORY").assertIsDisplayed()
        compose.onNodeWithText("×").performClick()

        compose.onNodeWithText("☷").performClick()
        compose.onNodeWithText("Budget setup").assertIsDisplayed()
        compose.onNodeWithText("BUDGET AMOUNT").assertIsDisplayed()
        compose.onNodeWithText("×").performClick()
    }
}
