package com.example.blueheartv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainNavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchApp_showsAuthScreen() {
        composeRule.onNodeWithText("欢迎使用超级蓝心小V")
            .assertIsDisplayed()
    }
}
