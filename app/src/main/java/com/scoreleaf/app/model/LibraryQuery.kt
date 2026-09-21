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

object ScoreMetadata {
    fun apply(
        score: Score,
        title: String,
        composer: String,
        genre: String,
        tags: String,
        key: String,
        tempo: String,
        durationMinutes: String,
        rating: Int,
        difficulty: Int
    ): Score = score.copy(
        title = title.trim().ifBlank { score.title },
        composer = composer.trim(),
        genre = genre.trim(),
        tags = tags.split(',').map(String::trim).filter(String::isNotBlank)
            .distinctBy(String::lowercase).toSet(),
        key = key.trim(),
        tempo = tempo.trim().toIntOrNull()?.coerceIn(0, 400) ?: 0,
        durationSeconds = durationMinutes.trim().toIntOrNull()?.takeIf { it >= 0 }
            ?.times(60) ?: 0,
        rating = rating.coerceIn(0, 5),
        difficulty = difficulty.coerceIn(0, 5)
    )
}

object SetlistEditor {
    fun add(scoreIds: List<String>, scoreId: String): List<String> =
        if (scoreId in scoreIds) scoreIds else scoreIds + scoreId

    fun remove(scoreIds: List<String>, scoreId: String): List<String> =
        scoreIds.filterNot { it == scoreId }

    fun move(scoreIds: List<String>, index: Int, direction: Int): List<String> {
        if (index !in scoreIds.indices || direction == 0) return scoreIds
        val target = (index + direction.coerceIn(-1, 1)).coerceIn(scoreIds.indices)
        if (target == index) return scoreIds
        return scoreIds.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
    }
}
