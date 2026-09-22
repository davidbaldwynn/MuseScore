package com.scoreleaf.app.model

import java.net.URI

enum class MusicSite(
    val label: String,
    val homeUrl: String,
    val description: String,
    val hosts: Set<String>
) {
    MUSIC_SCORES(
        label = "Music-Scores",
        homeUrl = "https://www.music-scores.com/",
        description = "Classical scores, instrumental parts, MP3 and MIDI resources",
        hosts = setOf("music-scores.com", "www.music-scores.com")
    ),
    MUSESCORE(
        label = "MuseScore",
        homeUrl = "https://musescore.com/sheetmusic",
        description = "Community and licensed sheet music available through your account",
        hosts = setOf("musescore.com", "www.musescore.com")
    ),
    IMSLP(
        label = "IMSLP",
        homeUrl = "https://imslp.org/",
        description = "Public-domain scores from the Petrucci Music Library",
        hosts = setOf("imslp.org", "www.imslp.org")
    ),
    MUSICNOTES(
        label = "Musicnotes",
        homeUrl = "https://www.musicnotes.com/",
        description = "Licensed popular, film, stage and classical sheet music",
        hosts = setOf("musicnotes.com", "www.musicnotes.com")
    ),
    VIRTUAL_SHEET_MUSIC(
        label = "Virtual Sheet Music",
        homeUrl = "https://www.virtualsheetmusic.com/",
        description = "PDF scores, parts and accompaniment resources",
        hosts = setOf("virtualsheetmusic.com", "www.virtualsheetmusic.com")
    ),
    EIGHT_NOTES(
        label = "8notes",
        homeUrl = "https://www.8notes.com/",
        description = "Free and premium scores, lessons and play-along tracks",
        hosts = setOf("8notes.com", "www.8notes.com")
    ),
    FREE_SCORES(
        label = "Free-scores",
        homeUrl = "https://www.free-scores.com/",
        description = "Free and licensed PDF, MIDI and audio resources",
        hosts = setOf("free-scores.com", "www.free-scores.com")
    )
}

object MusicSitePolicy {
    fun canNavigate(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null
    }.getOrDefault(false)

    fun isPdfDownload(url: String, mimeType: String?, contentDisposition: String?): Boolean {
        if (!canNavigate(url)) return false
        return mimeType?.substringBefore(';')?.trim()?.equals("application/pdf", true) == true ||
            runCatching { URI(url).path.endsWith(".pdf", true) }.getOrDefault(false) ||
            contentDisposition?.contains(Regex("filename\\*?=.*\\.pdf", RegexOption.IGNORE_CASE)) == true
    }

    fun pdfFileName(suggested: String?): String {
        val dispositionName = suggested.orEmpty()
            .substringAfter("filename*=", missingDelimiterValue = suggested.orEmpty())
            .substringAfter("UTF-8''", missingDelimiterValue = suggested.orEmpty())
            .substringAfter("filename=", missingDelimiterValue = suggested.orEmpty())
        val clean = dispositionName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore(';')
            .substringBefore('?')
            .trim().trim('"', '\'')
            .replace(Regex("[\\x00-\\x1f<>:\"/\\\\|?*]"), "_")
            .trim('.', ' ')
            .take(120)
        if (clean.isBlank()) return "Downloaded score.pdf"
        return if (clean.endsWith(".pdf", true)) clean else "$clean.pdf"
    }
}
