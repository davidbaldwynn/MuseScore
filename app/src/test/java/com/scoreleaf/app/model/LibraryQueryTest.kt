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
}
