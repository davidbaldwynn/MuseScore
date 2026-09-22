package com.scoreleaf.app.ui

import android.graphics.Paint
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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
        compose.onNodeWithText("All Scores").fetchSemanticsNode()
        compose.onNodeWithText("Bring your music with you").assertIsDisplayed()
        compose.onNodeWithContentDescription("Import PDF").assertIsEnabled()
    }

    @Test
    fun musicSiteBrowserFlowListsProvidersAndOpensDedicatedBrowser() {
        launchApp()

        compose.onNodeWithContentDescription("Import PDF").performClick()
        compose.onNodeWithText("Add sheet music").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Browse music sites").performClick()
        compose.onNodeWithText("Import from a music site").assertIsDisplayed()
        compose.onNodeWithText("Music-Scores").assertIsDisplayed()
        compose.onNodeWithText("MuseScore").assertIsDisplayed()
        compose.onNodeWithText("IMSLP").assertIsDisplayed()
        compose.onNodeWithText("Musicnotes").assertIsDisplayed()

        compose.onNodeWithText("Music-Scores").performClick()
        compose.onNodeWithText("Music-Scores browser").assertIsDisplayed()
        compose.onNodeWithContentDescription("Browser back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Browser forward").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reload page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close browser").performClick()
        compose.onNodeWithText("Import from a music site").assertIsDisplayed()
    }

    @Test
    fun importSourceDialogCanBeCancelledWithoutOpeningSystemPicker() {
        launchApp()

        compose.onNodeWithContentDescription("Import PDF").performClick()
        compose.onNodeWithText("Add sheet music").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Bring your music with you").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Two pages").performClick()
        compose.waitUntilAtLeastOneExists(hasText("1–2 / 2"), timeoutMillis = 15_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Page 1"), timeoutMillis = 15_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Page 2"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Page 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Half page").performClick()
        compose.waitUntilAtLeastOneExists(hasText("1 top / 2"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithText("1 bottom / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithText("2 top / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Fit width").performClick()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Vertical scroll").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Scrollable score"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Single page").performClick()
        compose.waitUntilAtLeastOneExists(hasText("2 / 2"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled()
        compose.onNodeWithContentDescription("Redo").performClick()
        compose.onNodeWithContentDescription("Highlighter").performClick()
        compose.onNodeWithTag("ink-layer").performTouchInput {
            swipe(start = centerLeft, end = centerRight, durationMillis = 500)
        }
        compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()

        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit details").performClick()
        compose.onNodeWithText("Composer").performTextReplacement("Ada Composer")
        compose.onNodeWithText("Tags (comma separated)").performTextReplacement("solo, recital")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Library search").performTextReplacement("Ada Composer")
        compose.onNodeWithText("Flow score").assertIsDisplayed()
        compose.onNodeWithContentDescription("Library search").performTextReplacement("")
        compose.onNodeWithContentDescription("Setlists").performClick()
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Rehearsal")
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Rehearsal").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Add to Rehearsal").performClick()
        compose.onNodeWithText("Rehearsal").performClick()
        compose.onNodeWithContentDescription("Open Flow score").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Flow score").performClick()
        compose.waitUntilAtLeastOneExists(hasText("2 / 2"), timeoutMillis = 15_000)
        compose.onNodeWithContentDescription("Annotate").performClick()
        compose.onNodeWithContentDescription("Undo").assertIsEnabled()
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

    @Test
    fun annotatedExportIsAValidPdfWithFlattenedInk() = runBlocking {
        val repo = ScoreRepository(context)
        val score = repo.importPdf(Uri.fromFile(createPdf("export.pdf", 2)), "Export score.pdf")
        repo.saveStrokes(
            score.id,
            0,
            listOf(
                com.scoreleaf.app.model.InkStroke(
                    color = Color.RED.toLong(),
                    width = 12f,
                    points = listOf(
                        com.scoreleaf.app.model.InkPoint(.1f, .5f),
                        com.scoreleaf.app.model.InkPoint(.9f, .5f)
                    )
                )
            )
        )

        val exported = repo.exportAnnotatedPdf(score)
        assertTrue(exported.exists() && exported.length() > 0)
        val descriptor = ParcelFileDescriptor.open(exported, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(descriptor).use { renderer ->
            assertEquals(2, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val coloredPixels = (260..540 step 20).count { y ->
                    (60..540 step 20).any { x -> Color.red(bitmap.getPixel(x, y)) > 180 && Color.green(bitmap.getPixel(x, y)) < 100 }
                }
                assertTrue("Expected flattened red annotation pixels", coloredPixels > 0)
            }
        }
        descriptor.close()
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
