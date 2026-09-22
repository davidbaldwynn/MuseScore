package com.scoreleaf.app.model

import com.scoreleaf.app.ScoreleafConstants
import org.json.JSONObject
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
    MUSESCORE_SANDBOX(
        label = "MuseScore sandbox",
        homeUrl = ScoreleafConstants.MUSESCORE_SANDBOX_BASE_URL,
        description = "Authorized test environment configured in ScoreleafConstants",
        hosts = setOfNotNull(runCatching { URI(ScoreleafConstants.MUSESCORE_SANDBOX_BASE_URL).host }.getOrNull())
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

data class MuseScoreSandboxConfig(
    val baseUrl: String = ScoreleafConstants.MUSESCORE_SANDBOX_BASE_URL,
    val pdfExportTemplate: String = ScoreleafConstants.MUSESCORE_SANDBOX_PDF_EXPORT_TEMPLATE
)

object MuseScoreSandboxPolicy {
    private val scorePath = Regex("/(?:score|scores)/([A-Za-z0-9_-]+)(?:/|$)")

    fun isConfigured(config: MuseScoreSandboxConfig = MuseScoreSandboxConfig()): Boolean = runCatching {
        val base = URI(config.baseUrl)
        val export = URI(config.pdfExportTemplate.replace("{scoreId}", "test-score"))
        val host = base.host?.lowercase() ?: return@runCatching false
        base.scheme.equals("https", true) &&
            base.rawUserInfo == null &&
            host != "musescore.com" && !host.endsWith(".musescore.com") &&
            (host == "musescore.test" || host.endsWith(".musescore.test") || !host.endsWith(".test")) &&
            export.scheme.equals("https", true) &&
            export.rawUserInfo == null &&
            export.host.equals(host, true) &&
            config.pdfExportTemplate.contains("{scoreId}")
    }.getOrDefault(false)

    fun exportPdfUrl(
        pageUrl: String,
        config: MuseScoreSandboxConfig = MuseScoreSandboxConfig()
    ): String? = runCatching {
        if (!isConfigured(config)) return@runCatching null
        val base = URI(config.baseUrl)
        val page = URI(pageUrl)
        if (!page.scheme.equals("https", true) || page.rawUserInfo != null || !page.host.equals(base.host, true)) {
            return@runCatching null
        }
        val scoreId = scorePath.findAll(page.path.orEmpty()).lastOrNull()?.groupValues?.get(1)
            ?: return@runCatching null
        val export = URI(config.pdfExportTemplate.replace("{scoreId}", scoreId))
        if (!export.scheme.equals("https", true) || export.rawUserInfo != null || !export.host.equals(base.host, true)) {
            return@runCatching null
        }
        export.toString()
    }.getOrNull()

    fun scoreId(pageUrl: String): String? = runCatching {
        val page = URI(pageUrl)
        val base = URI(ScoreleafConstants.MUSESCORE_SANDBOX_BASE_URL)
        if (!page.scheme.equals("https", true) || !page.host.equals(base.host, true)) return@runCatching null
        scorePath.findAll(page.path.orEmpty()).lastOrNull()?.groupValues?.get(1)
    }.getOrNull()

    fun pageApiUrl(scoreId: String, pageIndex: Int): String? {
        if (!scoreId.matches(Regex("[A-Za-z0-9_-]+")) || pageIndex < 0) return null
        return ScoreleafConstants.MUSESCORE_SANDBOX_PAGE_TEMPLATE
            .replace("{scoreId}", scoreId)
            .replace("{pageIndex}", pageIndex.toString())
    }

    fun mediaUrl(responseJson: String): String? = runCatching {
        val value = JSONObject(responseJson).optJSONObject("info")?.optString("url").orEmpty()
        trustedMediaUrl(value)
    }.getOrNull()

    fun trustedMediaUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase() ?: return@runCatching null
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null) return@runCatching null
        if (host != "musescore.test" && !host.endsWith(".musescore.test")) return@runCatching null
        value
    }.getOrNull()
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
