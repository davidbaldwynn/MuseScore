package com.scoreleaf.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScoreleafAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun clearAppState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("scoreleaf", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("scores").deleteRecursively()
    }

    @Test
    fun emptyLibraryShowsPrimaryNavigationAndImportActions() {
        compose.setContent {
            ScoreleafTheme {
                ScoreleafApp(initialPdf = null)
            }
        }

        compose.onNodeWithText("Scoreleaf").assertIsDisplayed()
        compose.onNodeWithText("All Scores").assertIsDisplayed()
        compose.onNodeWithText("Bring your music with you").assertIsDisplayed()
        compose.onNodeWithContentDescription("Import PDF").assertIsEnabled()
        compose.onNodeWithText("Import PDF").assertIsDisplayed()
    }
}
