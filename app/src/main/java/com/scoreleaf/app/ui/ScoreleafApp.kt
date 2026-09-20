package com.scoreleaf.app.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.Score

@Composable
fun ScoreleafApp(initialPdf: Uri?) {
    val context = LocalContext.current
    val repo = remember { ScoreRepository(context) }
    var openScore by remember { mutableStateOf<Score?>(null) }
    var importedInitial by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialPdf) {
        if (initialPdf != null && !importedInitial) {
            importedInitial = true
            openScore = repo.importPdf(initialPdf, "Imported score.pdf")
        }
    }

    if (openScore == null) {
        LibraryScreen(repo = repo, onOpen = { openScore = it })
    } else {
        ReaderScreen(repo = repo, score = openScore!!, onBack = { openScore = null })
    }
}
