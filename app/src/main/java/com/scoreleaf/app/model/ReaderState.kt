package com.scoreleaf.app.model

object ReaderState {
    fun withPage(score: Score, page: Int, pageCount: Int): Score {
        val last = (pageCount - 1).coerceAtLeast(0)
        return score.copy(lastPage = page.coerceIn(0, last))
    }

    fun toggleBookmark(score: Score, page: Int): Score {
        require(page >= 0) { "Bookmark page must not be negative" }
        val pages = if (page in score.bookmarkedPages) {
            score.bookmarkedPages - page
        } else {
            score.bookmarkedPages + page
        }
        return score.copy(bookmarkedPages = pages, lastPage = page)
    }
}
