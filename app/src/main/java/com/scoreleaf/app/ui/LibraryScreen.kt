package com.scoreleaf.app.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.Score
import com.scoreleaf.app.model.LibraryQuery
import com.scoreleaf.app.model.LibrarySection
import com.scoreleaf.app.model.ScoreMetadata
import com.scoreleaf.app.model.Setlist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: ScoreRepository, onOpen: (Score) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scores by remember { mutableStateOf(repo.scores()) }
    var setlists by remember { mutableStateOf(repo.setlists()) }
    var query by remember { mutableStateOf("") }
    var showSetlists by remember { mutableStateOf(false) }
    var menuScore by remember { mutableStateOf<Score?>(null) }
    var editingScore by remember { mutableStateOf<Score?>(null) }
    var selectedSetlistId by remember { mutableStateOf<String?>(null) }
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
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f).semantics { contentDescription = "Library search" },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, if (grid) "List" else "Grid") }
                IconButton(onClick = { showSetlists = !showSetlists }) { Icon(Icons.Default.Sort, "Sort and filter") }
            }
            Spacer(Modifier.height(12.dp))
            if (showSetlists) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedSetlistId != null) {
                        IconButton(onClick = { selectedSetlistId = null }) { Icon(Icons.Default.ArrowBack, "All setlists") }
                    }
                    Text(
                        setlists.firstOrNull { it.id == selectedSetlistId }?.name ?: "Setlists",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedSetlistId == null) TextButton(onClick = { showNewSetlist = true }) { Text("New") }
                }
                val selectedSetlist = setlists.firstOrNull { it.id == selectedSetlistId }
                if (selectedSetlist == null) {
                    setlists.forEach { setlist ->
                        ListItem(
                            headlineContent = { Text(setlist.name) },
                            supportingContent = { Text("${setlist.scoreIds.size} scores") },
                            leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                            modifier = Modifier.clickable { selectedSetlistId = setlist.id }
                        )
                    }
                } else {
                    val members = selectedSetlist.scoreIds.mapNotNull { id -> scores.firstOrNull { it.id == id } }
                    if (members.isEmpty()) Text("Add scores using their More menu.", style = MaterialTheme.typography.bodyMedium)
                    members.forEachIndexed { index, member ->
                        ListItem(
                            headlineContent = { Text(member.title) },
                            supportingContent = { Text(member.composer.ifBlank { "Unknown composer" }) },
                            leadingContent = { Text("${index + 1}") },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            repo.moveInSetlist(selectedSetlist.id, index, -1)
                                            setlists = repo.setlists()
                                        },
                                        enabled = index > 0
                                    ) { Icon(Icons.Default.ArrowUpward, "Move ${member.title} up") }
                                    IconButton(
                                        onClick = {
                                            repo.moveInSetlist(selectedSetlist.id, index, 1)
                                            setlists = repo.setlists()
                                        },
                                        enabled = index < members.lastIndex
                                    ) { Icon(Icons.Default.ArrowDownward, "Move ${member.title} down") }
                                    IconButton(onClick = { onOpen(member) }) { Icon(Icons.Default.PlayArrow, "Open ${member.title}") }
                                    IconButton(onClick = {
                                        repo.removeFromSetlist(selectedSetlist.id, member.id)
                                        setlists = repo.setlists()
                                    }) { Icon(Icons.Default.RemoveCircleOutline, "Remove ${member.title}") }
                                }
                            },
                            modifier = Modifier.clickable { onOpen(member) }
                        )
                    }
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

    if (showNewSetlist) AlertDialog(onDismissRequest = { showNewSetlist = false }, title = { Text("New setlist") }, text = { OutlinedTextField(newSetlist, { newSetlist = it }, label = { Text("Name") }) }, confirmButton = { TextButton(onClick = { repo.createSetlist(newSetlist); setlists = repo.setlists(); newSetlist = ""; showNewSetlist = false }) { Text("Create") } }, dismissButton = { TextButton(onClick = { showNewSetlist = false }) { Text("Cancel") } })

    menuScore?.let { score ->
        AlertDialog(onDismissRequest = { menuScore = null }, title = { Text(score.title) }, text = {
            Column {
                TextButton(onClick = { editingScore = score; menuScore = null }) { Text("Edit details") }
                if (setlists.isEmpty()) Text("Create a setlist first to organize this score.")
                setlists.forEach { setlist -> TextButton(onClick = {
                    repo.addToSetlist(setlist.id, score.id)
                    setlists = repo.setlists()
                    menuScore = null
                }) { Text("Add to ${setlist.name}") } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { repo.deleteScore(score); scores = repo.scores(); menuScore = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } })
    }

    editingScore?.let { score ->
        MetadataDialog(
            score = score,
            onDismiss = { editingScore = null },
            onSave = { updated ->
                repo.updateScore(updated)
                scores = repo.scores()
                editingScore = null
            }
        )
    }
}

@Composable
private fun MetadataDialog(score: Score, onDismiss: () -> Unit, onSave: (Score) -> Unit) {
    var title by remember(score.id) { mutableStateOf(score.title) }
    var composer by remember(score.id) { mutableStateOf(score.composer) }
    var genre by remember(score.id) { mutableStateOf(score.genre) }
    var tags by remember(score.id) { mutableStateOf(score.tags.joinToString(", ")) }
    var key by remember(score.id) { mutableStateOf(score.key) }
    var tempo by remember(score.id) { mutableStateOf(score.tempo.takeIf { it > 0 }?.toString().orEmpty()) }
    var duration by remember(score.id) { mutableStateOf((score.durationSeconds / 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var rating by remember(score.id) { mutableIntStateOf(score.rating) }
    var difficulty by remember(score.id) { mutableIntStateOf(score.difficulty) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit score details") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(composer, { composer = it }, label = { Text("Composer") }, singleLine = true)
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true)
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text("Key") }, singleLine = true)
                OutlinedTextField(tempo, { tempo = it.filter(Char::isDigit) }, label = { Text("Tempo (BPM)") }, singleLine = true)
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duration (minutes)") }, singleLine = true)
                Text("Rating: $rating")
                Slider(rating.toFloat(), { rating = it.toInt() }, valueRange = 0f..5f, steps = 4)
                Text("Difficulty: $difficulty")
                Slider(difficulty.toFloat(), { difficulty = it.toInt() }, valueRange = 0f..5f, steps = 4)
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave(ScoreMetadata.apply(score, title, composer, genre, tags, key, tempo, duration, rating, difficulty))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp)); Text("Bring your music with you", style = MaterialTheme.typography.titleLarge)
        Text("Import a PDF to start reading and annotating.", style = MaterialTheme.typography.bodyMedium)
    }
}
