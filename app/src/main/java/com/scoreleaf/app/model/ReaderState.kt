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

    fun pagesFor(startPage: Int, pageCount: Int, mode: PageDisplayMode): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = startPage.coerceIn(0, pageCount - 1)
        val count = if (mode == PageDisplayMode.TWO_UP) 2 else 1
        return (start until (start + count).coerceAtMost(pageCount)).toList()
    }

    fun movePage(
        current: Int,
        direction: Int,
        pageCount: Int,
        mode: PageDisplayMode
    ): Int {
        if (pageCount <= 0 || direction == 0) return current.coerceAtLeast(0)
        val step = if (mode == PageDisplayMode.TWO_UP) 2 else 1
        return (current + direction.coerceIn(-1, 1) * step)
            .coerceIn(0, pageCount - 1)
    }
}
