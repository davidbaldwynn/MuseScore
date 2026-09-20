package com.scoreleaf.app.model

enum class LibrarySection(val label: String) {
    ALL("All Scores"), RECENTS("Recents"), COMPOSERS("Composers"), GENRES("Genres"), TAGS("Tags")
}

object LibraryQuery {
    fun select(scores: List<Score>, section: LibrarySection, query: String): List<Score> {
        val ordered = when (section) {
            LibrarySection.ALL -> scores.sortedBy { it.title.lowercase() }
            LibrarySection.RECENTS -> scores.sortedByDescending { it.addedAt }
            LibrarySection.COMPOSERS -> scores.sortedWith(compareBy<Score> { it.composer.ifBlank { "Unknown composer" }.lowercase() }.thenBy { it.title.lowercase() })
            LibrarySection.GENRES -> scores.sortedWith(compareBy<Score> { it.genre.ifBlank { "Uncategorized" }.lowercase() }.thenBy { it.title.lowercase() })
            LibrarySection.TAGS -> scores.sortedWith(compareBy<Score> { it.tags.minOrNull().orEmpty().lowercase() }.thenBy { it.title.lowercase() })
        }
        val needle = query.trim()
        if (needle.isEmpty()) return ordered
        return ordered.filter { score ->
            sequenceOf(score.title, score.composer, score.genre, score.key, score.tags.joinToString(" "))
                .any { it.contains(needle, ignoreCase = true) }
        }
    }
}

object PageNavigation {
    fun next(current: Int, pageCount: Int) = (current + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
    fun previous(current: Int) = (current - 1).coerceAtLeast(0)
    fun isValid(page: Int, pageCount: Int) = page in 0 until pageCount
}
