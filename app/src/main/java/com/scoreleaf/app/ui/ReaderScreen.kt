package com.scoreleaf.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(repo: ScoreRepository, score: Score, onBack: () -> Unit) {
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
    var strokes by remember(page) { mutableStateOf(repo.strokes(score.id, page)) }
    var activePoints by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
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
            if (inkMode) IconButton(onClick = { strokes = strokes.dropLast(1); repo.saveStrokes(score.id, page, strokes) }, enabled = strokes.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo") }
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
                        InkLayer(strokes, activePoints, inkMode,
                            onPoints = { activePoints = it },
                            onCommit = { points ->
                                if (points.size > 1) strokes = strokes + InkStroke(Color(0xFFD52B1E).value.toLong(), 3f, points)
                                activePoints = emptyList(); repo.saveStrokes(score.id, page, strokes)
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
                onClose = { activePanel = null })
        }
    }
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
                listOf("Crop pages", "Rearrange", "Links & buttons", "Metadata", "Share / Export", "Settings").forEach { item ->
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
private fun InkLayer(strokes: List<InkStroke>, active: List<InkPoint>, enabled: Boolean, onPoints: (List<InkPoint>) -> Unit, onCommit: (List<InkPoint>) -> Unit) {
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(Modifier.fillMaxSize().testTag("ink-layer").onSizeChanged { layerSize = it }.then(if (enabled) Modifier.pointerInput(layerSize) {
        var gesturePoints = emptyList<InkPoint>()
        detectDragGestures(onDragStart = { p ->
            gesturePoints = listOf(InkPoint(p.x / size.width, p.y / size.height)); onPoints(gesturePoints)
        }, onDragEnd = { onCommit(gesturePoints); gesturePoints = emptyList() }) { change, _ ->
            change.consume()
            gesturePoints = gesturePoints + InkPoint(change.position.x / size.width, change.position.y / size.height)
            onPoints(gesturePoints)
        }
    } else Modifier)) {
        fun draw(points: List<InkPoint>, color: Color, width: Float) {
            points.zipWithNext().forEach { (a, b) -> drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), width, cap = StrokeCap.Round) }
        }
        strokes.forEach { draw(it.points, Color(it.color.toULong()), it.width) }
        draw(active, Color(0xFFD52B1E), 3f)
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
