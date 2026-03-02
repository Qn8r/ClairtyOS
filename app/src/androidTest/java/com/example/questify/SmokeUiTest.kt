package com.example.questify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SmokeUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsDailyQuestHeaderOnLaunch() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.nav_menu_desc)).assertIsDisplayed()
    }

    @Test
    fun opensSettingsFromDrawer() {
        openDrawer()
        rule.onNodeWithText(rule.activity.getString(R.string.title_settings)).performClick()
        openDrawer()
    }

    @Test
    fun opensRoutinesFromDrawer() {
        openDrawer()
        rule.onNodeWithText(rule.activity.getString(R.string.title_routines_templates)).performClick()
        openDrawer()
    }

    @Test
    fun opensCatalogAndCalendarFromDrawer() {
        openDrawer()
        rule.onNodeWithText(rule.activity.getString(R.string.title_catalog)).performClick()
        openDrawer()
        rule.onNodeWithText(rule.activity.getString(R.string.title_calendar)).performClick()
        openDrawer()
    }

    private fun openDrawer() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.nav_menu_desc)).assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.nav_menu_desc)).performClick()
    }
}
