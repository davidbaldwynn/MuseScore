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
        return move(ReaderLocation(current), direction, pageCount, mode).page
    }

    fun move(
        current: ReaderLocation,
        direction: Int,
        pageCount: Int,
        mode: PageDisplayMode
    ): ReaderLocation {
        if (pageCount <= 0) return ReaderLocation(0)
        val safePage = current.page.coerceIn(0, pageCount - 1)
        if (direction == 0) return ReaderLocation(safePage, if (mode == PageDisplayMode.HALF_PAGE) current.half else PageHalf.TOP)
        if (mode == PageDisplayMode.HALF_PAGE) {
            val ordinal = safePage * 2 + if (current.half == PageHalf.BOTTOM) 1 else 0
            val lastOrdinal = pageCount * 2 - 1
            val moved = (ordinal + direction.coerceIn(-1, 1)).coerceIn(0, lastOrdinal)
            return ReaderLocation(moved / 2, if (moved % 2 == 0) PageHalf.TOP else PageHalf.BOTTOM)
        }
        val step = if (mode == PageDisplayMode.TWO_UP) 2 else 1
        val normalized = if (mode == PageDisplayMode.TWO_UP) safePage - safePage.mod(2) else safePage
        val maximumStart = if (mode == PageDisplayMode.TWO_UP) {
            ((pageCount - 1) / 2) * 2
        } else {
            pageCount - 1
        }
        return ReaderLocation(
            (normalized + direction.coerceIn(-1, 1) * step).coerceIn(0, maximumStart),
            PageHalf.TOP
        )
    }
}
