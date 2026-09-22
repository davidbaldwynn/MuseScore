package com.scoreleaf.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(repo: ScoreRepository, score: Score, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentScore by remember(score.id) { mutableStateOf(score) }
    var page by remember { mutableIntStateOf(score.lastPage) }
    var pageCount by remember { mutableIntStateOf(1) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secondBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayMode by remember(score.id) { mutableStateOf(score.displayMode) }
    var fitMode by remember(score.id) { mutableStateOf(score.fitMode) }
    var half by remember(score.id) { mutableStateOf(score.lastHalf) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var inkMode by remember { mutableStateOf(false) }
    var annotationHistory by remember(page) {
        mutableStateOf(AnnotationHistory(repo.strokes(score.id, page)))
    }
    var annotationTool by remember { mutableStateOf(AnnotationTool.PEN) }
    var activePoints by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var activePressures by remember { mutableStateOf<List<Float>>(emptyList()) }
    var annotationLayers by remember(score.id) { mutableStateOf(repo.layers(score.id)) }
    var activeLayerId by remember(score.id) { mutableStateOf(AnnotationLayer.DEFAULT.id) }
    var selectedAnnotationIds by remember(page) { mutableStateOf<Set<String>>(emptySet()) }
    var penColor by remember { mutableLongStateOf(android.graphics.Color.RED.toLong()) }
    var penWidth by remember { mutableFloatStateOf(3f) }
    var showAnnotationTools by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showStampDialog by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<ReaderPanel?>(null) }
    val file = remember(score.id) { repo.scoreFile(score) }
    val pageCache = remember(score.id) { PageRenderCache<Bitmap>(6) }

    fun persistPosition() {
        currentScore = ReaderState.withPage(currentScore, page, pageCount).copy(lastHalf = half)
        repo.updateScore(currentScore)
    }
    fun movePage(direction: Int) {
        val next = ReaderState.move(ReaderLocation(page, half), direction, pageCount, displayMode)
        if (next != ReaderLocation(page, half)) {
            persistPosition()
            page = next.page
            half = next.half
        }
    }
    fun selectDisplayMode(mode: PageDisplayMode) {
        displayMode = mode
        if (mode != PageDisplayMode.HALF_PAGE) half = PageHalf.TOP
        inkMode = false
        currentScore = currentScore.copy(displayMode = mode, lastPage = page, lastHalf = half)
        repo.updateScore(currentScore)
    }
    fun selectFitMode(mode: PageFitMode) {
        fitMode = mode
        currentScore = currentScore.copy(fitMode = mode, lastPage = page, lastHalf = half)
        repo.updateScore(currentScore)
    }
    BackHandler { persistPosition(); onBack() }

    LaunchedEffect(page, file, displayMode) {
        renderError = null
        secondBitmap = null
        try {
            val rendered = renderPage(file, page, pageCache)
            pageCount = rendered.second
            bitmap = rendered.first
            if (displayMode == PageDisplayMode.TWO_UP && page + 1 < rendered.second) {
                secondBitmap = renderPage(file, page + 1, pageCache).first
            }
            val prefetch = listOf(page - 1, page + 1, page + 2)
                .filter { it in 0 until rendered.second }
            prefetch.forEach { renderPage(file, it, pageCache) }
        } catch (error: Exception) {
            bitmap = null
            renderError = error.message ?: "This PDF page could not be rendered."
        }
    }

    Scaffold(
        topBar = { if (controls) ScoreControlBar(score.title,
            onLibrary = { persistPosition(); onBack() },
            onPanel = { activePanel = it },
            onAnnotate = {
                if (displayMode != PageDisplayMode.SINGLE) {
                    selectDisplayMode(PageDisplayMode.SINGLE)
                    inkMode = true
                } else {
                    inkMode = !inkMode
                }
            }, inkMode = inkMode
        ) },
        bottomBar = { if (controls) BottomAppBar {
            IconButton(
                onClick = { movePage(-1) },
                enabled = ReaderState.move(ReaderLocation(page, half), -1, pageCount, displayMode) != ReaderLocation(page, half)
            ) { Icon(Icons.Default.ChevronLeft, "Previous") }
            val visiblePages = ReaderState.pagesFor(page, pageCount, displayMode)
            val pageLabel = when {
                displayMode == PageDisplayMode.HALF_PAGE -> "${page + 1} ${half.name.lowercase()} / $pageCount"
                visiblePages.size == 2 -> "${visiblePages.first() + 1}–${visiblePages.last() + 1} / $pageCount"
                else -> "${page + 1} / $pageCount"
            }
            Text(pageLabel, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (inkMode) {
                IconButton(onClick = { annotationTool = AnnotationTool.PEN }) {
                    Icon(Icons.Default.Draw, "Pen", tint = if (annotationTool == AnnotationTool.PEN) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                IconButton(onClick = { annotationTool = AnnotationTool.HIGHLIGHTER }) {
                    Icon(Icons.Default.BorderColor, "Highlighter", tint = if (annotationTool == AnnotationTool.HIGHLIGHTER) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                IconButton(onClick = { annotationTool = AnnotationTool.ERASER }) {
                    Icon(Icons.Default.AutoFixOff, "Eraser", tint = if (annotationTool == AnnotationTool.ERASER) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                IconButton(onClick = { showAnnotationTools = true }) {
                    Icon(Icons.Default.Construction, "Annotation tools")
                }
                IconButton(onClick = { showLayersDialog = true }) {
                    Icon(Icons.Default.Layers, "Annotation layers")
                }
                IconButton(onClick = {
                    annotationHistory = AnnotationEditor.undo(annotationHistory)
                    repo.saveStrokes(score.id, page, annotationHistory.strokes)
                }, enabled = annotationHistory.strokes.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo") }
                IconButton(onClick = {
                    annotationHistory = AnnotationEditor.redo(annotationHistory)
                    repo.saveStrokes(score.id, page, annotationHistory.strokes)
                }, enabled = annotationHistory.redo.isNotEmpty() || annotationHistory.redoSnapshots.isNotEmpty()) { Icon(Icons.Default.Redo, "Redo") }
            }
            IconButton(
                onClick = { movePage(1) },
                enabled = ReaderState.move(ReaderLocation(page, half), 1, pageCount, displayMode) != ReaderLocation(page, half)
            ) { Icon(Icons.Default.ChevronRight, "Next") }
        } }
    ) { padding ->
        BoxWithConstraints(
            Modifier.padding(padding).fillMaxSize().testTag("score-reader").background(Color(0xFF24211E))
                .pointerInput(page, inkMode) { if (!inkMode) detectTapGestures { tap ->
                    when { tap.x < size.width * .25f -> movePage(-1); tap.x > size.width * .75f -> movePage(1); else -> controls = !controls }
                } },
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let { bmp ->
                if (displayMode == PageDisplayMode.VERTICAL_SCROLL) {
                    VerticalScrollReader(file, pageCount, page, fitMode, pageCache) { visiblePage ->
                        if (visiblePage != page) page = visiblePage
                    }
                } else if (displayMode == PageDisplayMode.TWO_UP) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(bmp.asImageBitmap(), "Page ${page + 1}", Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                        secondBitmap?.let { second ->
                            Image(second.asImageBitmap(), "Page ${page + 2}", Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                        } ?: Spacer(Modifier.weight(1f))
                    }
                } else if (displayMode == PageDisplayMode.HALF_PAGE) {
                    HalfPageImage(bmp, page, half, fitMode)
                } else {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
                    val displayModifier = if (ratio > boxRatio) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxHeight().aspectRatio(ratio)
                    Box(displayModifier) {
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = fitMode.contentScale())
                        val visibleLayerIds = annotationLayers.filter(AnnotationLayer::visible).mapTo(hashSetOf(), AnnotationLayer::id)
                        InkLayer(annotationHistory.strokes.filter { it.layerId in visibleLayerIds }, activePoints, activePressures, selectedAnnotationIds, inkMode, annotationTool, penColor, penWidth,
                            onPoints = { points, pressures -> activePoints = points; activePressures = pressures },
                            onCommit = { points, pressures ->
                                val activeLayer = annotationLayers.firstOrNull { it.id == activeLayerId } ?: AnnotationLayer.DEFAULT
                                annotationHistory = when {
                                    activeLayer.locked -> annotationHistory
                                    annotationTool == AnnotationTool.ERASER && points.isNotEmpty() ->
                                        AnnotationEditor.eraseNearest(annotationHistory, points.last(), .04f)
                                    annotationTool == AnnotationTool.LASSO && points.size > 1 -> {
                                        selectedAnnotationIds = AnnotationEditor.selectInRect(annotationHistory, points.first(), points.last())
                                        annotationHistory
                                    }
                                    points.size > 1 -> AnnotationEditor.add(
                                        annotationHistory,
                                        InkStroke(
                                            color = if (annotationTool == AnnotationTool.HIGHLIGHTER) 0x66FFD54FL else penColor,
                                            width = if (annotationTool == AnnotationTool.HIGHLIGHTER) 18f else penWidth,
                                            points = if (annotationTool in setOf(AnnotationTool.LINE, AnnotationTool.RECTANGLE, AnnotationTool.ELLIPSE)) listOf(points.first(), points.last()) else points,
                                            tool = annotationTool,
                                            layerId = activeLayerId,
                                            pressures = pressures
                                        )
                                    )
                                    else -> annotationHistory
                                }
                                activePoints = emptyList()
                                activePressures = emptyList()
                                repo.saveStrokes(score.id, page, annotationHistory.strokes)
                            })
                    }
                }
            } ?: renderError?.let { message ->
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text("Unable to display this score", color = Color.White)
                    Text(message, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            } ?: CircularProgressIndicator()
        }
    }

    activePanel?.let { panel ->
        ModalBottomSheet(onDismissRequest = { activePanel = null }) {
            ReaderPanelContent(panel, currentScore, page, pageCount, displayMode, fitMode,
                onBookmark = {
                    currentScore = ReaderState.toggleBookmark(currentScore, page)
                    repo.updateScore(currentScore)
                    activePanel = null
                },
                onDisplayMode = {
                    selectDisplayMode(it)
                    activePanel = null
                },
                onFitMode = {
                    selectFitMode(it)
                    activePanel = null
                },
                onExport = {
                    activePanel = null
                    scope.launch {
                        val exported = repo.exportAnnotatedPdf(currentScore)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", exported)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Export annotated PDF"))
                    }
                },
                onClose = { activePanel = null })
        }
    }

    if (showAnnotationTools) AnnotationToolsDialog(
        selectedTool = annotationTool,
        selectedCount = selectedAnnotationIds.size,
        onTool = { tool ->
            annotationTool = tool
            showAnnotationTools = false
            if (tool == AnnotationTool.TEXT) showTextDialog = true
            if (tool == AnnotationTool.STAMP) showStampDialog = true
        },
        onPreset = { color, width -> penColor = color; penWidth = width; annotationTool = AnnotationTool.PEN },
        onMoveSelection = { dx, dy ->
            annotationHistory = AnnotationEditor.move(annotationHistory, selectedAnnotationIds, dx, dy)
            repo.saveStrokes(score.id, page, annotationHistory.strokes)
        },
        onDeleteSelection = {
            annotationHistory = AnnotationEditor.delete(annotationHistory, selectedAnnotationIds)
            selectedAnnotationIds = emptySet()
            repo.saveStrokes(score.id, page, annotationHistory.strokes)
        },
        onDismiss = { showAnnotationTools = false }
    )

    if (showTextDialog) AnnotationTextDialog(
        title = "Add text",
        placeholder = "Rehearsal note",
        onDismiss = { showTextDialog = false; annotationTool = AnnotationTool.PEN },
        onAdd = { value ->
            val layer = annotationLayers.firstOrNull { it.id == activeLayerId }
            if (layer?.locked != true && value.isNotBlank()) {
                annotationHistory = AnnotationEditor.add(annotationHistory, InkStroke(
                    color = penColor, width = 5f, points = listOf(InkPoint(.5f, .5f)),
                    tool = AnnotationTool.TEXT, layerId = activeLayerId, text = value.trim()
                ))
                repo.saveStrokes(score.id, page, annotationHistory.strokes)
            }
            showTextDialog = false
            annotationTool = AnnotationTool.PEN
        }
    )

    if (showStampDialog) StampDialog(
        onDismiss = { showStampDialog = false; annotationTool = AnnotationTool.PEN },
        onStamp = { symbol ->
            val layer = annotationLayers.firstOrNull { it.id == activeLayerId }
            if (layer?.locked != true) {
                annotationHistory = AnnotationEditor.add(annotationHistory, InkStroke(
                    color = penColor, width = 8f, points = listOf(InkPoint(.5f, .5f)),
                    tool = AnnotationTool.STAMP, layerId = activeLayerId, text = symbol
                ))
                repo.saveStrokes(score.id, page, annotationHistory.strokes)
            }
            showStampDialog = false
            annotationTool = AnnotationTool.PEN
        }
    )

    if (showLayersDialog) AnnotationLayersDialog(
        layers = annotationLayers,
        activeLayerId = activeLayerId,
        onSelect = { activeLayerId = it },
        onChange = { updated -> annotationLayers = updated; repo.saveLayers(score.id, updated) },
        onDismiss = { showLayersDialog = false }
    )
}

private enum class ReaderPanel { BOOKMARKS, SEARCH, METRONOME, PITCH, TOOLS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreControlBar(title: String, onLibrary: () -> Unit, onPanel: (ReaderPanel) -> Unit, onAnnotate: () -> Unit, inkMode: Boolean) {
    TopAppBar(
        title = { Text(title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onClick = onLibrary) { Icon(Icons.Default.LibraryMusic, "Library") } },
        actions = {
            IconButton(onClick = { onPanel(ReaderPanel.BOOKMARKS) }) { Icon(Icons.Default.Bookmarks, "Bookmarks") }
            IconButton(onClick = { onPanel(ReaderPanel.SEARCH) }) { Icon(Icons.Default.Search, "Search") }
            IconButton(onClick = { onPanel(ReaderPanel.METRONOME) }) { Icon(Icons.Default.Timer, "Metronome") }
            IconButton(onClick = { onPanel(ReaderPanel.PITCH) }) { Icon(Icons.Default.GraphicEq, "Pitch") }
            IconButton(onClick = onAnnotate) { Icon(if (inkMode) Icons.Default.EditOff else Icons.Default.Draw, "Annotate", tint = if (inkMode) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
            IconButton(onClick = { onPanel(ReaderPanel.TOOLS) }) { Icon(Icons.Default.MoreHoriz, "Tools") }
        }
    )
}

@Composable
private fun ReaderPanelContent(
    panel: ReaderPanel,
    score: Score,
    page: Int,
    pageCount: Int,
    displayMode: PageDisplayMode,
    fitMode: PageFitMode,
    onBookmark: () -> Unit,
    onDisplayMode: (PageDisplayMode) -> Unit,
    onFitMode: (PageFitMode) -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(panel.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        when (panel) {
            ReaderPanel.BOOKMARKS -> {
                Button(onClick = onBookmark) { Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(if (page in score.bookmarkedPages) "Remove page ${page + 1}" else "Bookmark page ${page + 1}") }
                score.bookmarkedPages.sorted().forEach { Text("Page ${it + 1}", Modifier.padding(vertical = 10.dp)) }
            }
            ReaderPanel.SEARCH -> { OutlinedTextField("", {}, Modifier.fillMaxWidth(), placeholder = { Text("Search title, composer, tags, or page text") }, leadingIcon = { Icon(Icons.Default.Search, null) }); Text("PDF text indexing is scheduled for the parity build.", style = MaterialTheme.typography.bodySmall) }
            ReaderPanel.METRONOME -> MetronomePanel(score.tempo.takeIf { it > 0 } ?: 120)
            ReaderPanel.PITCH -> PitchPanel()
            ReaderPanel.TOOLS -> {
                Text("Page layout", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { onDisplayMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Page fit", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageFitMode.entries.forEach { mode ->
                        FilterChip(
                            selected = fitMode == mode,
                            onClick = { onFitMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                ListItem(
                    headlineContent = { Text("Export annotated PDF") },
                    leadingContent = { Icon(Icons.Default.Share, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable(onClick = onExport)
                )
                listOf("Crop pages", "Rearrange", "Links & buttons", "Metadata", "Settings").forEach { item ->
                    ListItem(headlineContent = { Text(item) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
                }
            }
        }
        if (panel != ReaderPanel.BOOKMARKS) TextButton(onClick = onClose, Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable private fun MetronomePanel(initialTempo: Int) {
    var tempo by remember { mutableFloatStateOf(initialTempo.toFloat()) }
    var playing by remember { mutableStateOf(false) }
    Text("${tempo.toInt()} BPM", style = MaterialTheme.typography.displaySmall)
    Slider(tempo, { tempo = it }, valueRange = 30f..240f, steps = 209)
    FilledTonalButton(onClick = { playing = !playing }) { Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, null); Text(if (playing) " Stop" else " Start") }
}

@Composable private fun PitchPanel() {
    var note by remember { mutableIntStateOf(9) }
    val notes = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    Text("${notes[note]}4", style = MaterialTheme.typography.displaySmall)
    androidx.compose.foundation.lazy.LazyRow { items(notes.size) { index -> AssistChip(onClick = { note = index }, label = { Text(notes[index]) }, modifier = Modifier.padding(end = 4.dp)) } }
}

@Composable
private fun AnnotationToolsDialog(
    selectedTool: AnnotationTool,
    selectedCount: Int,
    onTool: (AnnotationTool) -> Unit,
    onPreset: (Long, Float) -> Unit,
    onMoveSelection: (Float, Float) -> Unit,
    onDeleteSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Annotation tools") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Draw and place", style = MaterialTheme.typography.titleSmall)
                listOf(
                    AnnotationTool.LINE to "Line",
                    AnnotationTool.RECTANGLE to "Rectangle",
                    AnnotationTool.ELLIPSE to "Ellipse",
                    AnnotationTool.TEXT to "Text",
                    AnnotationTool.STAMP to "Music stamp",
                    AnnotationTool.LASSO to "Lasso selection"
                ).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (tool, label) ->
                            FilterChip(selected = selectedTool == tool, onClick = { onTool(tool) }, label = { Text(label) })
                        }
                    }
                }
                Text("Pen presets", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { onPreset(android.graphics.Color.RED.toLong(), 3f) }, label = { Text("Red fine") })
                    AssistChip(onClick = { onPreset(android.graphics.Color.BLUE.toLong(), 6f) }, label = { Text("Blue medium") })
                    AssistChip(onClick = { onPreset(android.graphics.Color.BLACK.toLong(), 10f) }, label = { Text("Black bold") })
                }
                if (selectedCount > 0) {
                    Text("$selectedCount selected", style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = { onMoveSelection(-.02f, 0f) }) { Icon(Icons.Default.ArrowBack, "Move selection left") }
                        IconButton(onClick = { onMoveSelection(.02f, 0f) }) { Icon(Icons.Default.ArrowForward, "Move selection right") }
                        IconButton(onClick = { onMoveSelection(0f, -.02f) }) { Icon(Icons.Default.ArrowUpward, "Move selection up") }
                        IconButton(onClick = { onMoveSelection(0f, .02f) }) { Icon(Icons.Default.ArrowDownward, "Move selection down") }
                        IconButton(onClick = onDeleteSelection) { Icon(Icons.Default.Delete, "Delete selection") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun AnnotationTextDialog(title: String, placeholder: String, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, placeholder = { Text(placeholder) }, singleLine = false) },
        confirmButton = { TextButton(onClick = { onAdd(value) }, enabled = value.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StampDialog(onDismiss: () -> Unit, onStamp: (String) -> Unit) {
    val stamps = listOf("♩", "♪", "♫", "♭", "♯", "𝄐")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music stamp") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stamps.forEach { stamp -> FilledTonalIconButton(onClick = { onStamp(stamp) }) { Text(stamp) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AnnotationLayersDialog(
    layers: List<AnnotationLayer>,
    activeLayerId: String,
    onSelect: (String) -> Unit,
    onChange: (List<AnnotationLayer>) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renameLayerId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Annotation layers") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                layers.forEach { layer ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(layer.id) }.semantics {
                            contentDescription = "Select ${layer.name} layer"
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = activeLayerId == layer.id, onClick = { onSelect(layer.id) })
                        Text(layer.name, Modifier.weight(1f))
                        IconButton(onClick = {
                            renameLayerId = layer.id
                            renameValue = layer.name
                        }) { Icon(Icons.Default.Edit, "Rename ${layer.name}") }
                        IconToggleButton(checked = layer.visible, onCheckedChange = {
                            onChange(AnnotationLayers.setVisible(layers, layer.id, it))
                        }) { Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "${if (layer.visible) "Hide" else "Show"} ${layer.name}") }
                        IconToggleButton(checked = layer.locked, onCheckedChange = {
                            onChange(AnnotationLayers.setLocked(layers, layer.id, it))
                        }) { Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, "${if (layer.locked) "Unlock" else "Lock"} ${layer.name}") }
                    }
                }
                if (renameLayerId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(renameValue, { renameValue = it }, Modifier.weight(1f), label = { Text("Rename layer") }, singleLine = true)
                        IconButton(onClick = {
                            onChange(AnnotationLayers.rename(layers, renameLayerId!!, renameValue))
                            renameLayerId = null
                            renameValue = ""
                        }, enabled = renameValue.isNotBlank()) { Icon(Icons.Default.Check, "Save layer name") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), label = { Text("New layer name") }, singleLine = true)
                    IconButton(onClick = {
                        val updated = AnnotationLayers.add(layers, newName)
                        onChange(updated)
                        updated.lastOrNull()?.let { onSelect(it.id) }
                        newName = ""
                    }, enabled = newName.isNotBlank()) { Icon(Icons.Default.Add, "Add layer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InkLayer(
    strokes: List<InkStroke>,
    active: List<InkPoint>,
    activePressures: List<Float>,
    selectedIds: Set<String>,
    enabled: Boolean,
    tool: AnnotationTool,
    penColor: Long,
    penWidth: Float,
    onPoints: (List<InkPoint>, List<Float>) -> Unit,
    onCommit: (List<InkPoint>, List<Float>) -> Unit
) {
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val latestPressure = remember { floatArrayOf(1f) }
    Canvas(Modifier.fillMaxSize().testTag("ink-layer").onSizeChanged { layerSize = it }
        .then(if (enabled) Modifier.pointerInteropFilter { event ->
            latestPressure[0] = event.getPressure(event.actionIndex).coerceIn(.2f, 1.5f)
            false
        } else Modifier)
        .then(if (enabled) Modifier.pointerInput(layerSize, tool) {
        var gesturePoints = emptyList<InkPoint>()
        var gesturePressures = emptyList<Float>()
        detectDragGestures(onDragStart = { p ->
            gesturePoints = listOf(InkPoint(p.x / size.width, p.y / size.height))
            gesturePressures = listOf(latestPressure[0])
            onPoints(gesturePoints, gesturePressures)
        }, onDragEnd = { onCommit(gesturePoints, gesturePressures); gesturePoints = emptyList(); gesturePressures = emptyList() }) { change, _ ->
            change.consume()
            gesturePoints = gesturePoints + InkPoint(change.position.x / size.width, change.position.y / size.height)
            gesturePressures = gesturePressures + latestPressure[0]
            onPoints(gesturePoints, gesturePressures)
        }
    } else Modifier)) {
        fun drawInk(points: List<InkPoint>, pressures: List<Float>, color: Color, width: Float) {
            points.zipWithNext().forEachIndexed { index, (a, b) ->
                val pressure = pressures.getOrNull(index)?.coerceIn(.2f, 1.5f) ?: 1f
                drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), width * pressure, cap = StrokeCap.Round)
            }
        }
        fun drawElement(element: InkStroke) {
            val color = Color(annotationArgb(element.color))
            val first = element.points.firstOrNull() ?: return
            val last = element.points.lastOrNull() ?: first
            val start = Offset(first.x * size.width, first.y * size.height)
            val end = Offset(last.x * size.width, last.y * size.height)
            val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
            val bounds = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
            when (element.tool) {
                AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> drawInk(element.points, element.pressures, color, element.width)
                AnnotationTool.LINE -> drawLine(color, start, end, element.width, cap = StrokeCap.Round)
                AnnotationTool.RECTANGLE -> drawRect(color, topLeft, bounds, style = Stroke(element.width))
                AnnotationTool.ELLIPSE -> drawOval(color, topLeft, bounds, style = Stroke(element.width))
                AnnotationTool.TEXT, AnnotationTool.STAMP -> drawContext.canvas.nativeCanvas.drawText(
                    element.text,
                    start.x,
                    start.y,
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = annotationArgb(element.color)
                        textSize = element.width * 5f
                    }
                )
                AnnotationTool.ERASER, AnnotationTool.LASSO -> Unit
            }
            if (element.id in selectedIds) {
                val padding = 10f
                drawRect(
                    Color(0xFF2F80ED),
                    Offset(topLeft.x - padding, topLeft.y - padding),
                    Size(bounds.width.coerceAtLeast(20f) + padding * 2, bounds.height.coerceAtLeast(20f) + padding * 2),
                    style = Stroke(2f)
                )
            }
        }
        strokes.forEach(::drawElement)
        if (tool !in setOf(AnnotationTool.ERASER, AnnotationTool.TEXT, AnnotationTool.STAMP)) {
            val preview = InkStroke(
                color = if (tool == AnnotationTool.HIGHLIGHTER) 0x66FFD54FL else if (tool == AnnotationTool.LASSO) android.graphics.Color.BLUE.toLong() else penColor,
                width = if (tool == AnnotationTool.HIGHLIGHTER) 18f else penWidth,
                points = if (tool in setOf(AnnotationTool.LINE, AnnotationTool.RECTANGLE, AnnotationTool.ELLIPSE, AnnotationTool.LASSO) && active.size > 1) listOf(active.first(), active.last()) else active,
                tool = if (tool == AnnotationTool.LASSO) AnnotationTool.RECTANGLE else tool,
                pressures = activePressures
            )
            drawElement(preview)
        }
    }
}

private fun PageFitMode.contentScale() = when (this) {
    PageFitMode.PAGE -> ContentScale.Fit
    PageFitMode.WIDTH -> ContentScale.FillWidth
    PageFitMode.HEIGHT -> ContentScale.FillHeight
}

@Composable
private fun HalfPageImage(bitmap: Bitmap, page: Int, half: PageHalf, fitMode: PageFitMode) {
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().semantics {
            contentDescription = "Page ${page + 1} ${half.name.lowercase()} half"
        }
    ) {
        Image(
            bitmap.asImageBitmap(),
            null,
            Modifier.fillMaxWidth().height(maxHeight * 2)
                .offset(y = if (half == PageHalf.BOTTOM) -maxHeight else 0.dp),
            contentScale = if (fitMode == PageFitMode.HEIGHT) ContentScale.FillHeight else ContentScale.FillWidth
        )
    }
}

@Composable
private fun VerticalScrollReader(
    file: File,
    pageCount: Int,
    initialPage: Int,
    fitMode: PageFitMode,
    cache: PageRenderCache<Bitmap>,
    onVisiblePage: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceAtLeast(0))
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onVisiblePage(listState.firstVisibleItemIndex)
    }
    LazyColumn(
        Modifier.fillMaxSize().semantics { contentDescription = "Scrollable score" },
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pageCount) { index ->
            var rendered by remember(file, index) { mutableStateOf(cache[index]) }
            LaunchedEffect(file, index) {
                if (rendered == null) {
                    rendered = runCatching { renderPage(file, index, cache).first }.getOrNull()
                }
            }
            Box(Modifier.fillParentMaxWidth().aspectRatio(0.75f), contentAlignment = Alignment.Center) {
                rendered?.let {
                    Image(
                        it.asImageBitmap(),
                        "Page ${index + 1}",
                        Modifier.fillMaxSize(),
                        contentScale = fitMode.contentScale()
                    )
                } ?: CircularProgressIndicator()
            }
        }
    }
}

private suspend fun renderPage(file: File, index: Int, cache: PageRenderCache<Bitmap>): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
        cache.getOrPut(safeIndex) {
            renderer.openPage(safeIndex).use { page ->
                val scale = 2f
                Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } to renderer.pageCount
    }.also { descriptor.close() }
}
