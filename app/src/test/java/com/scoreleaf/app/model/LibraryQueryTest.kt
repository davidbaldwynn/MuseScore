package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryTest {
    private val bach = Score(id = "1", title = "Cello Suite", fileName = "bach.pdf", composer = "Bach", genre = "Baroque", tags = setOf("cello"), addedAt = 10)
    private val debussy = Score(id = "2", title = "Clair de lune", fileName = "debussy.pdf", composer = "Debussy", genre = "Impressionist", tags = setOf("piano"), addedAt = 30)
    private val mozart = Score(id = "3", title = "Requiem", fileName = "mozart.pdf", composer = "Mozart", genre = "Classical", tags = setOf("choral"), addedAt = 20)
    private val scores = listOf(mozart, bach, debussy)

    @Test fun allScoresAreAlphabetical() {
        assertEquals(listOf("Cello Suite", "Clair de lune", "Requiem"), LibraryQuery.select(scores, LibrarySection.ALL, "").map { it.title })
    }

    @Test fun recentsAreNewestFirst() {
        assertEquals(listOf("Clair de lune", "Requiem", "Cello Suite"), LibraryQuery.select(scores, LibrarySection.RECENTS, "").map { it.title })
    }

    @Test fun searchCoversMetadataAndTagsCaseInsensitively() {
        assertEquals(listOf("Cello Suite"), LibraryQuery.select(scores, LibrarySection.ALL, "BAROQUE").map { it.title })
        assertEquals(listOf("Clair de lune"), LibraryQuery.select(scores, LibrarySection.ALL, "piano").map { it.title })
    }

    @Test fun navigationNeverLeavesDocument() {
        assertEquals(2, PageNavigation.next(2, 3))
        assertEquals(0, PageNavigation.previous(0))
        assertTrue(PageNavigation.isValid(2, 3))
        assertFalse(PageNavigation.isValid(3, 3))
    }

    @Test fun readerPositionIsClampedToTheDocument() {
        assertEquals(2, ReaderState.withPage(bach, 99, 3).lastPage)
        assertEquals(0, ReaderState.withPage(bach, -4, 3).lastPage)
        assertEquals(0, ReaderState.withPage(bach, 4, 0).lastPage)
    }

    @Test fun bookmarkToggleAddsThenRemovesWithoutLosingPosition() {
        val added = ReaderState.toggleBookmark(bach, 3)
        assertEquals(setOf(3), added.bookmarkedPages)
        assertEquals(3, added.lastPage)

        val removed = ReaderState.toggleBookmark(added, 3)
        assertTrue(removed.bookmarkedPages.isEmpty())
        assertEquals(3, removed.lastPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeBookmarkPagesAreRejected() {
        ReaderState.toggleBookmark(bach, -1)
    }

    @Test fun pageLayoutsRespectDocumentBounds() {
        assertEquals(listOf(0), ReaderState.pagesFor(0, 3, PageDisplayMode.SINGLE))
        assertEquals(listOf(0, 1), ReaderState.pagesFor(0, 3, PageDisplayMode.TWO_UP))
        assertEquals(listOf(2), ReaderState.pagesFor(2, 3, PageDisplayMode.TWO_UP))
        assertTrue(ReaderState.pagesFor(0, 0, PageDisplayMode.TWO_UP).isEmpty())
    }

    @Test fun twoUpNavigationMovesBySpreads() {
        assertEquals(2, ReaderState.movePage(0, 1, 6, PageDisplayMode.TWO_UP))
        assertEquals(4, ReaderState.movePage(4, 1, 6, PageDisplayMode.TWO_UP))
        assertEquals(2, ReaderState.movePage(4, -1, 6, PageDisplayMode.TWO_UP))
        assertEquals(2, ReaderState.movePage(1, 1, 6, PageDisplayMode.TWO_UP))
        assertEquals(1, ReaderState.movePage(0, 1, 2, PageDisplayMode.SINGLE))
    }

    @Test fun halfPageNavigationVisitsBothHalvesBeforeChangingPages() {
        val top = ReaderLocation(0, PageHalf.TOP)
        val bottom = ReaderState.move(top, 1, 3, PageDisplayMode.HALF_PAGE)
        val nextTop = ReaderState.move(bottom, 1, 3, PageDisplayMode.HALF_PAGE)

        assertEquals(ReaderLocation(0, PageHalf.BOTTOM), bottom)
        assertEquals(ReaderLocation(1, PageHalf.TOP), nextTop)
        assertEquals(bottom, ReaderState.move(nextTop, -1, 3, PageDisplayMode.HALF_PAGE))
    }

    @Test fun halfPageNavigationClampsAtDocumentEdges() {
        assertEquals(
            ReaderLocation(0, PageHalf.TOP),
            ReaderState.move(ReaderLocation(0, PageHalf.TOP), -1, 2, PageDisplayMode.HALF_PAGE)
        )
        assertEquals(
            ReaderLocation(1, PageHalf.BOTTOM),
            ReaderState.move(ReaderLocation(1, PageHalf.BOTTOM), 1, 2, PageDisplayMode.HALF_PAGE)
        )
    }

    @Test fun nonHalfPageModesNormalizeTheHalfAndUseTheirOwnStep() {
        val bottom = ReaderLocation(1, PageHalf.BOTTOM)
        assertEquals(ReaderLocation(2, PageHalf.TOP), ReaderState.move(bottom, 1, 5, PageDisplayMode.SINGLE))
        assertEquals(ReaderLocation(2, PageHalf.TOP), ReaderState.move(bottom, 1, 5, PageDisplayMode.TWO_UP))
        assertEquals(ReaderLocation(2, PageHalf.TOP), ReaderState.move(bottom, 1, 5, PageDisplayMode.VERTICAL_SCROLL))
    }

    @Test fun readerPreferencesRoundTripThroughJson() {
        val configured = bach.copy(
            displayMode = PageDisplayMode.HALF_PAGE,
            fitMode = PageFitMode.WIDTH,
            lastHalf = PageHalf.BOTTOM
        )

        assertEquals(configured, Score.fromJson(configured.toJson()))
    }

    @Test fun legacyScoresDefaultToSafeReaderPreferences() {
        val json = bach.toJson().apply {
            remove("displayMode")
            remove("fitMode")
            remove("lastHalf")
        }
        val restored = Score.fromJson(json)

        assertEquals(PageDisplayMode.SINGLE, restored.displayMode)
        assertEquals(PageFitMode.PAGE, restored.fitMode)
        assertEquals(PageHalf.TOP, restored.lastHalf)
    }
}
