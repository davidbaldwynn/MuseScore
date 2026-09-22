package com.scoreleaf.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.scoreleaf.app.model.MusicSite
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MuseScoreSandboxUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun configuredSandboxAppearsAndCanBeSelected() {
        var selected: MusicSite? = null
        compose.setContent {
            ScoreleafTheme {
                MusicSitePicker(onBack = {}, onOpen = { selected = it })
            }
        }

        compose.onNodeWithText("MuseScore sandbox").assertIsDisplayed().performClick()
        assertEquals(MusicSite.MUSESCORE_SANDBOX, selected)
    }
}
