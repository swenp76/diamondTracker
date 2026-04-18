package de.baseball.diamond9

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        // Note: We use useUnmergedTree = true because the ExtendedFloatingActionButton
        // might have merged its children (icon + text) into a single semantics node.
        composeTestRule.onNodeWithText("Add Team", useUnmergedTree = true).performClick()

        // 2. Try to type 60 characters into the EditText (uses View-based AlertDialog)
        val longName = "A".repeat(60)
        // Wait for the dialog and find the EditText.
        // We use inRoot(isDialog()) to ensure we are looking at the dialog window.
        onView(isAssignableFrom(android.widget.EditText::class.java))
            .inRoot(isDialog())
            .perform(typeText(longName), closeSoftKeyboard())

        // 3. Verify only 50 characters were accepted
        onView(withText(longName.take(50))).check(matches(isDisplayed()))
    }
}
