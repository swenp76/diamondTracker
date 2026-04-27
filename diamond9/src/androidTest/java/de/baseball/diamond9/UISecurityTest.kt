package de.baseball.diamond9

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UISecurityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<CoachAct>()

    private fun getString(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    private fun navigateToTeamList() {
<<<<<<< HEAD
        // Wait for the Compose hierarchy to be active and for the splash screen to finish.
        // We use a custom check to avoid IllegalStateException if the hierarchy is temporarily empty.
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            try {
                composeTestRule.onAllNodesWithContentDescription(getString(R.string.nav_home))
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (e: IllegalStateException) {
                // Hierarchy not yet available
                false
            }
=======
        // Wait for splash screen to finish (it has a delay(2500))
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithContentDescription(getString(R.string.nav_home)).fetchSemanticsNodes().isNotEmpty()
>>>>>>> 38b789b (Fix: resolve IllegalStateException in UISecurityTest)
        }

        // Ensure the screen is idle
        composeTestRule.waitForIdle()

        // Open drawer (using the content description of the menu button in CoachAct)
        composeTestRule.onNodeWithContentDescription(getString(R.string.nav_home)).performClick()
        
        // Wait for drawer to open and item to be visible
        composeTestRule.waitForIdle()
        
        // Click Teams - it's in the Navigation Drawer
        composeTestRule.onNodeWithText(getString(R.string.nav_teams)).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun teamNameEntry_enforcesLengthLimit() {
        navigateToTeamList()

        // 1. Click FAB to add team (Compose-based ExtendedFloatingActionButton)
        val addTeamText = getString(R.string.fab_add_team)
        composeTestRule.onNodeWithText(addTeamText, useUnmergedTree = true).performClick()

        // 2. Try to type 60 characters into the EditText (View-based AlertDialog)
        val longName = "A".repeat(60)
        onView(isAssignableFrom(android.widget.EditText::class.java))
            .inRoot(isDialog())
            .perform(typeText(longName), closeSoftKeyboard())

        // 3. Verify only 50 characters were accepted (InputFilter.LengthFilter(50))
        onView(withText(longName.take(50))).check(matches(isDisplayed()))
        
        // Cleanup: Click create to close dialog
        onView(withText(getString(R.string.btn_create))).perform(click())
    }

    @Test
    fun playerNameEntry_enforcesLengthLimit() {
        navigateToTeamList()
        val teamName = "Security Test Team"
        
        // 1. Create a team first to ensure we have something to click
        composeTestRule.onNodeWithText(getString(R.string.fab_add_team), useUnmergedTree = true).performClick()
        onView(isAssignableFrom(android.widget.EditText::class.java))
            .inRoot(isDialog())
            .perform(typeText(teamName), closeSoftKeyboard())
        onView(withText(getString(R.string.btn_create))).perform(click())

        // 2. Click the team to go to TeamHubActivity
        composeTestRule.onNodeWithText(teamName).performClick()

        // 3. Click "Kader" (Roster) in TeamHubActivity to go to TeamDetailActivity
        composeTestRule.onNodeWithText(getString(R.string.teamhub_edit_roster)).performClick()

        // 4. Click FAB to add player
        composeTestRule.onNodeWithText(getString(R.string.fab_add_player), useUnmergedTree = true).performClick()

        // 5. Test Name field limit (maxLength = 50)
        val longName = "B".repeat(60)
        val expectedName = "B".repeat(50)
        
        // Find by hint text and filter for nodes that have the SetText action
        composeTestRule.onAllNodesWithText(getString(R.string.hint_full_name))
            .filterToOne(hasSetTextAction())
            .performTextReplacement(longName)
        
        // Verify the text was truncated
        composeTestRule.onNodeWithText(expectedName).assertExists()
        
        // 6. Test Jersey Number limit (maxLength = 3)
        val longNumber = "12345"
        val expectedNumber = "123"
        composeTestRule.onAllNodesWithText(getString(R.string.hint_jersey_number))
            .filterToOne(hasSetTextAction())
            .performTextReplacement(longNumber)
            
        composeTestRule.onNodeWithText(expectedNumber).assertExists()

        // Cleanup: Click Add to close dialog
        composeTestRule.onNodeWithText(getString(R.string.btn_add)).performClick()
    }
}
