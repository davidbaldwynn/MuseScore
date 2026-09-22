package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSiteTest {
    @Test
    fun supportedSitesUseSecureCanonicalHomes() {
        assertEquals(
            listOf("Music-Scores", "MuseScore", "MuseScore sandbox", "IMSLP", "Musicnotes", "Virtual Sheet Music", "8notes", "Free-scores"),
            MusicSite.entries.map { it.label }
        )
        MusicSite.entries.forEach { site ->
            assertTrue(site.homeUrl.startsWith("https://"))
            assertTrue(site.hosts.isNotEmpty())
        }
    }

    @Test
    fun browserOnlyAllowsWebSchemesAndRejectsCredentialLeakingUrls() {
        assertTrue(MusicSitePolicy.canNavigate("https://musescore.com/sheetmusic"))
        assertTrue(MusicSitePolicy.canNavigate("https://accounts.google.com/login"))
        assertFalse(MusicSitePolicy.canNavigate("http://musescore.com/sheetmusic"))
        assertFalse(MusicSitePolicy.canNavigate("javascript:alert(1)"))
        assertFalse(MusicSitePolicy.canNavigate("file:///sdcard/secret.pdf"))
        assertFalse(MusicSitePolicy.canNavigate("content://com.example/private"))
        assertFalse(MusicSitePolicy.canNavigate("https://user:password@example.com/file.pdf"))
    }

    @Test
    fun detectsPdfDownloadsFromMimeUrlOrDisposition() {
        assertTrue(MusicSitePolicy.isPdfDownload("https://cdn.example/score", "application/pdf", null))
        assertTrue(MusicSitePolicy.isPdfDownload("https://cdn.example/score.PDF?token=abc", null, null))
        assertTrue(MusicSitePolicy.isPdfDownload("https://cdn.example/download", "application/octet-stream", "attachment; filename=Etude.pdf"))
        assertFalse(MusicSitePolicy.isPdfDownload("https://cdn.example/song.mp3", "audio/mpeg", null))
        assertFalse(MusicSitePolicy.isPdfDownload("blob:https://musescore.com/id", "application/pdf", "score.pdf"))
    }

    @Test
    fun filenameSanitizationCannotEscapeStorageAndKeepsPdfExtension() {
        assertEquals("Moonlight Sonata.pdf", MusicSitePolicy.pdfFileName("../../Moonlight Sonata.pdf"))
        assertEquals("Downloaded score.pdf", MusicSitePolicy.pdfFileName(".."))
        assertEquals("Etude.pdf", MusicSitePolicy.pdfFileName("Etude"))
        assertEquals("score.pdf", MusicSitePolicy.pdfFileName("attachment; filename=score.pdf"))
    }

    @Test
    fun sandboxAdapterBuildsOnlySameHostAuthorizedExportUrls() {
        val config = MuseScoreSandboxConfig(
            baseUrl = "https://sandbox.sheetmusic.example/",
            pdfExportTemplate = "https://sandbox.sheetmusic.example/api/test/scores/{scoreId}/export.pdf"
        )

        assertEquals(
            "https://sandbox.sheetmusic.example/api/test/scores/abc-123/export.pdf",
            MuseScoreSandboxPolicy.exportPdfUrl(
                "https://sandbox.sheetmusic.example/scores/abc-123",
                config
            )
        )
        assertEquals(null, MuseScoreSandboxPolicy.exportPdfUrl("https://attacker.example/scores/abc-123", config))
        assertEquals(null, MuseScoreSandboxPolicy.exportPdfUrl("http://sandbox.sheetmusic.example/scores/abc-123", config))
        assertEquals(null, MuseScoreSandboxPolicy.exportPdfUrl("https://sandbox.sheetmusic.example/scores/../../secret", config))
    }

    @Test
    fun sandboxAdapterCannotBeConfiguredForProductionMuseScore() {
        listOf("musescore.com", "www.musescore.com", "api.musescore.com").forEach { host ->
            val config = MuseScoreSandboxConfig(
                baseUrl = "https://$host/",
                pdfExportTemplate = "https://$host/api/scores/{scoreId}/export.pdf"
            )
            assertFalse(MuseScoreSandboxPolicy.isConfigured(config))
            assertEquals(null, MuseScoreSandboxPolicy.exportPdfUrl("https://$host/scores/123", config))
        }
    }

    @Test
    fun placeholderSandboxConstantsStayDisabledUntilExplicitlyConfigured() {
        assertTrue(MuseScoreSandboxPolicy.isConfigured())
    }

    @Test
    fun sandboxPageRequestsIncludeScoreIdIndexAndImageType() {
        assertEquals(
            "https://musescore.test/api/jmuse?id=12345&index=7&type=img",
            MuseScoreSandboxPolicy.pageApiUrl("12345", 7)
        )
        assertEquals(null, MuseScoreSandboxPolicy.pageApiUrl("../../secret", 0))
        assertEquals(null, MuseScoreSandboxPolicy.pageApiUrl("12345", -1))
    }

    @Test
    fun sandboxMediaResponsesCannotEscapeTheTestDomain() {
        assertEquals(
            "https://cdn.musescore.test/scores/12345/page-2.svg",
            MuseScoreSandboxPolicy.mediaUrl(
                """{"info":{"url":"https://cdn.musescore.test/scores/12345/page-2.svg"}}"""
            )
        )
        assertEquals(null, MuseScoreSandboxPolicy.mediaUrl("""{"info":{"url":"https://musescore.com/secret.svg"}}"""))
        assertEquals(null, MuseScoreSandboxPolicy.mediaUrl("""{"info":{"url":"http://cdn.musescore.test/page.png"}}"""))
        assertEquals(null, MuseScoreSandboxPolicy.mediaUrl("not json"))
    }
}
