package com.scoreleaf.app.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.Score
import com.scoreleaf.app.model.LibraryQuery
import com.scoreleaf.app.model.LibrarySection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: ScoreRepository, onOpen: (Score) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scores by remember { mutableStateOf(repo.scores()) }
    var query by remember { mutableStateOf("") }
    var showSetlists by remember { mutableStateOf(false) }
    var menuScore by remember { mutableStateOf<Score?>(null) }
    var newSetlist by remember { mutableStateOf("") }
    var showNewSetlist by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf(LibrarySection.ALL) }
    var grid by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "Imported score.pdf"
        scope.launch { onOpen(repo.importPdf(uri, name).also { scores = repo.scores() }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Scoreleaf", fontWeight = FontWeight.SemiBold); Text("Your music, ready to play", style = MaterialTheme.typography.labelSmall) } },
                actions = {
                    IconButton(onClick = { showSetlists = !showSetlists }) { Icon(Icons.Default.QueueMusic, "Setlists") }
                    IconButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.Add, "Import PDF") }
                }
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }, icon = { Icon(Icons.Default.PictureAsPdf, null) }, text = { Text("Import PDF") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            ScrollableTabRow(selectedTabIndex = content.ordinal, edgePadding = 0.dp, divider = {}) {
                LibrarySection.entries.forEach { item -> Tab(selected = content == item, onClick = { content = item }, text = { Text(item.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, if (grid) "List" else "Grid") }
                IconButton(onClick = { showSetlists = !showSetlists }) { Icon(Icons.Default.Sort, "Sort and filter") }
            }
            Spacer(Modifier.height(12.dp))
            if (showSetlists) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Setlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showNewSetlist = true }) { Text("New") }
                }
                repo.setlists().forEach { setlist ->
                    ListItem(headlineContent = { Text(setlist.name) }, supportingContent = { Text("${setlist.scoreIds.size} scores") }, leadingContent = { Icon(Icons.Default.LibraryMusic, null) })
                }
                HorizontalDivider(); Spacer(Modifier.height(8.dp))
            }
            val filtered = LibraryQuery.select(scores, content, query)
            if (filtered.isEmpty()) EmptyLibrary(Modifier.weight(1f)) else if (!grid) LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { score ->
                    Card(onClick = { onOpen(score) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(score.title, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(if (score.lastPage > 0) "Continue on page ${score.lastPage + 1}" else "PDF score") },
                            leadingContent = { Icon(Icons.Default.MusicNote, null) },
                            trailingContent = { IconButton(onClick = { menuScore = score }) { Icon(Icons.Default.MoreVert, "More") } }
                        )
                    }
                }
            } else LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(filtered, key = { it.id }) { score ->
                    Card(onClick = { onOpen(score) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(.78f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.secondary)
                            IconButton(onClick = { menuScore = score }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.MoreVert, "More") }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(score.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(score.composer.ifBlank { "Unknown composer" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            val details = listOf(score.key, score.tempo.takeIf { it > 0 }?.let { "$it BPM" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showNewSetlist) AlertDialog(onDismissRequest = { showNewSetlist = false }, title = { Text("New setlist") }, text = { OutlinedTextField(newSetlist, { newSetlist = it }, label = { Text("Name") }) }, confirmButton = { TextButton(onClick = { repo.createSetlist(newSetlist); newSetlist = ""; showNewSetlist = false }) { Text("Create") } }, dismissButton = { TextButton(onClick = { showNewSetlist = false }) { Text("Cancel") } })

    menuScore?.let { score ->
        AlertDialog(onDismissRequest = { menuScore = null }, title = { Text(score.title) }, text = {
            Column {
                if (repo.setlists().isEmpty()) Text("Create a setlist first to organize this score.")
                repo.setlists().forEach { setlist -> TextButton(onClick = { repo.addToSetlist(setlist.id, score.id); menuScore = null }) { Text("Add to ${setlist.name}") } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { repo.deleteScore(score); scores = repo.scores(); menuScore = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } })
    }
}

@Composable private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp)); Text("Bring your music with you", style = MaterialTheme.typography.titleLarge)
        Text("Import a PDF to start reading and annotating.", style = MaterialTheme.typography.bodyMedium)
    }
}
