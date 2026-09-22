package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSiteTest {
    @Test
    fun supportedSitesUseSecureCanonicalHomes() {
        assertEquals(
            listOf("Music-Scores", "MuseScore"),
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
}
