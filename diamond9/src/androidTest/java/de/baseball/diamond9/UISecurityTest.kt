package de.baseball.diamond9

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UISecurityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TeamListActivity>()

    @Test
    fun teamNameEntry_enforcesLengthLimit() {
        // 1. Click FAB to add team (uses Compose)
        composeTestRule.onNodeWithText("Add Team", useUnmergedTree = true).performClick()

        // 2. Try to type 60 characters into the EditText (uses View-based AlertDialog)
        val longName = "A".repeat(60)
        onView(isAssignableFrom(android.widget.EditText::class.java))
            .inRoot(isDialog())
            .perform(typeText(longName), closeSoftKeyboard())

        // 3. Verify only 50 characters were accepted
        onView(withText(longName.take(50))).check(matches(isDisplayed()))
    }

    @Test
    fun playerNameEntry_enforcesLengthLimit() {
        // 1. Click on the first team in the list to go to TeamDetailActivity
        // We assume there's at least one team if we are testing this, 
        // or we rely on the rule starting TeamListActivity and we can click a team.
        // For a robust test, we might want to ensure a team exists.
        // But let's assume the UI state allows navigation.
        
        // Let's try to find a team item and click it. 
        // If the list is empty, we might need to create one first.
        
        // For now, let's assume we are in TeamDetailActivity or can get there.
        // Actually, the rule starts TeamListActivity.
        
        // 1. Create a team first to ensure we have something to click
        composeTestRule.onNodeWithText("Add Team", useUnmergedTree = true).performClick()
        onView(isAssignableFrom(android.widget.EditText::class.java))
            .inRoot(isDialog())
            .perform(typeText("Test Team"), closeSoftKeyboard())
        onView(withText("Create")).perform(click())

        // 2. Click the team to go to details
        composeTestRule.onNodeWithText("Test Team").performClick()

        // 3. Click FAB to add player
        composeTestRule.onNodeWithText("Add Player", useUnmergedTree = true).performClick()

        // 4. Try to type 60 characters into the Name field (Compose OutlinedTextField)
        val longName = "P".repeat(60)
        composeTestRule.onNodeWithText("Full Name").performTextReplacement(longName)

        // 5. Verify only 50 characters were accepted in the TextField
        composeTestRule.onNodeWithText(longName.take(50)).assertExists()
    }
}
