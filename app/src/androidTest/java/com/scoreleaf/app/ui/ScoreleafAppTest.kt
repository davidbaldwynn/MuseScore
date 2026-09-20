package com.scoreleaf.app.ui

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.scoreleaf.app.data.ScoreRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ScoreleafAppTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun clearAppState() {
        context.getSharedPreferences("scoreleaf", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("scores").deleteRecursively()
    }

    @Test
    fun emptyLibraryShowsPrimaryNavigationAndImportActions() {
        launchApp()

        compose.onNodeWithText("Scoreleaf").assertIsDisplayed()
        compose.onNodeWithText("All Scores").assertExists()
        compose.onNodeWithText("Bring your music with you").assertIsDisplayed()
        compose.onNodeWithContentDescription("Import PDF").assertIsEnabled()
        compose.onNodeWithText("Import PDF").assertExists()
    }

    @Test
    fun importedPdfOpensTurnsBookmarksAnnotatesAndCreatesSetlist() {
        runBlocking {
            ScoreRepository(context).importPdf(
                Uri.fromFile(createPdf("flow.pdf", 2)),
                "Flow score.pdf"
            )
        }
        launchApp()

        compose.onNodeWithText("Flow score").performClick()
        compose.waitUntilAtLeastOneExists(hasText("1 / 2"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNodeWithText("Bookmark page 2").performClick()
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNodeWithText("Page 2").assertIsDisplayed()
        compose.onNodeWithText("Remove page 2").performClick()

        compose.onNodeWithContentDescription("Annotate").performClick()
        compose.onNodeWithTag("ink-layer").performTouchInput {
            swipe(start = centerLeft, end = centerRight, durationMillis = 500)
        }
        compose.onNodeWithContentDescription("Undo").assertIsEnabled()

        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithContentDescription("Setlists").performClick()
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Rehearsal")
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Rehearsal").assertIsDisplayed()
    }

    @Test
    fun corruptPdfShowsRecoverableReaderError() {
        val invalid = context.cacheDir.resolve("invalid.pdf").apply {
            writeText("This is not a PDF")
        }
        runBlocking {
            ScoreRepository(context).importPdf(Uri.fromFile(invalid), "Broken score.pdf")
        }
        launchApp()

        compose.onNodeWithText("Broken score").performClick()
        compose.waitUntilAtLeastOneExists(
            hasText("Unable to display this score"),
            timeoutMillis = 15_000
        )
    }

    private fun launchApp() {
        compose.setContent {
            ScoreleafTheme {
                ScoreleafApp(initialPdf = null)
            }
        }
    }

    private fun createPdf(name: String, pages: Int) = context.cacheDir.resolve(name).also { file ->
        val document = PdfDocument()
        try {
            repeat(pages) { index ->
                val info = PdfDocument.PageInfo.Builder(600, 800, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawText("Test page ${index + 1}", 80f, 120f, Paint().apply {
                    textSize = 32f
                })
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
