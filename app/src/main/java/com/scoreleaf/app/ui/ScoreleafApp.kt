package com.scoreleaf.app.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.Score
import com.scoreleaf.app.model.MusicSite

@Composable
fun ScoreleafApp(initialPdf: Uri?) {
    val context = LocalContext.current
    val repo = remember { ScoreRepository(context) }
    var openScore by remember { mutableStateOf<Score?>(null) }
    var importedInitial by rememberSaveable { mutableStateOf(false) }
    var choosingMusicSite by rememberSaveable { mutableStateOf(false) }
    var musicSite by remember { mutableStateOf<MusicSite?>(null) }

    LaunchedEffect(initialPdf) {
        if (initialPdf != null && !importedInitial) {
            importedInitial = true
            openScore = repo.importPdf(initialPdf, "Imported score.pdf")
        }
    }

    if (openScore != null) {
        ReaderScreen(repo = repo, score = openScore!!, onBack = { openScore = null })
    } else if (musicSite != null) {
        MusicSiteBrowser(repo = repo, site = musicSite!!, onClose = { musicSite = null })
    } else if (choosingMusicSite) {
        MusicSitePicker(
            onBack = { choosingMusicSite = false },
            onOpen = { musicSite = it }
        )
    } else {
        LibraryScreen(
            repo = repo,
            onOpen = { openScore = it },
            onBrowseSites = { choosingMusicSite = true }
        )
    }
}
