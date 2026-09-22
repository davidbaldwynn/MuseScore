package com.scoreleaf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.data.MuseScoreSandboxImporter
import com.scoreleaf.app.model.MusicSite
import com.scoreleaf.app.model.MusicSitePolicy
import com.scoreleaf.app.model.MuseScoreSandboxPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSitePicker(onBack: () -> Unit, onOpen: (MusicSite) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Import from a music site") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back to library") }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Sign in to a supported site and download PDFs your account is allowed to access.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            MusicSite.entries.filter { site ->
                site != MusicSite.MUSESCORE_SANDBOX || MuseScoreSandboxPolicy.isVisible(isDebuggable = false)
            }.forEach { site ->
                ListItem(
                    headlineContent = { Text(site.label) },
                    supportingContent = { Text(site.description) },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(site) }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSiteBrowser(repo: ScoreRepository, site: MusicSite, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(site.homeUrl) }
    var sandboxScoreId by remember { mutableStateOf<String?>(null) }
    var sandboxPageCount by remember { mutableIntStateOf(0) }
    var sandboxTitle by remember { mutableStateOf("Sandbox score") }
    var sandboxFirstPageUrl by remember { mutableStateOf<String?>(null) }
    var downloadedPages by remember { mutableIntStateOf(0) }
    val sandboxTokens = remember { mutableStateMapOf<Int, String>() }

    fun refreshSandboxMetadata(browser: WebView, url: String) {
        if (site != MusicSite.MUSESCORE_SANDBOX) return
        sandboxScoreId = MuseScoreSandboxPolicy.scoreId(url)
        browser.evaluateJavascript(
            """(() => {
                const text = document.body?.innerText || '';
                const match = text.match(/\d+\s+of\s+(\d+)\s+pages/i);
                const numbered = document.querySelectorAll('[data-page-number]').length;
                const scroller = document.querySelector('#jmuse-scroller-component');
                const count = match ? Number(match[1]) : Math.max(numbered, scroller?.children?.length || 0);
                const title = document.querySelector('meta[property="og:title"]')?.content || document.title || 'Sandbox score';
                let firstPage = document.querySelector('link[href*="score_0.svg"], link[href*="score_0.png"]')?.href || '';
                if (!firstPage) {
                    for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
                        try {
                            const data = JSON.parse(script.textContent);
                            if (data.thumbnailUrl) { firstPage = data.thumbnailUrl; break; }
                        } catch (_) {}
                    }
                }
                return JSON.stringify({count, title, firstPage});
            })()""".trimIndent()
        ) { raw ->
            runCatching {
                val jsonText = JSONTokener(raw).nextValue() as String
                val metadata = JSONObject(jsonText)
                sandboxPageCount = metadata.optInt("count", 0)
                sandboxTitle = metadata.optString("title", "Sandbox score").trim().ifBlank { "Sandbox score" }
                sandboxFirstPageUrl = metadata.optString("firstPage").takeIf { it.isNotBlank() }
            }
        }
    }

    fun importSandboxPdf() {
        val scoreId = sandboxScoreId
        val pageCount = sandboxPageCount
        if (site != MusicSite.MUSESCORE_SANDBOX || scoreId == null || pageCount <= 0) {
            scope.launch { snackbar.showSnackbar("Open a score in the configured sandbox first") }
            return
        }
        val browser = webView ?: return
        scope.launch {
            downloading = true
            downloadedPages = 0
            browser.evaluateJavascript(
                """(() => {
                    const step = Math.max(window.innerHeight, 800);
                    for (let y = 0; y <= document.body.scrollHeight; y += step) {
                        setTimeout(() => window.scrollTo(0, y), Math.floor(y / step) * 100);
                    }
                    setTimeout(() => window.scrollTo(0, 0), Math.ceil(document.body.scrollHeight / step) * 100 + 100);
                    return true;
                })()""".trimIndent(),
                null
            )
            delay((pageCount * 150L).coerceIn(1_000L, 8_000L))
            val result = runCatching {
                MuseScoreSandboxImporter(browser.context, repo).downloadAndImport(
                    scoreId = scoreId,
                    title = sandboxTitle,
                    pageCount = pageCount,
                    firstPageUrl = sandboxFirstPageUrl,
                    authorizationByPage = sandboxTokens.toMap(),
                    cookies = CookieManager.getInstance().getCookie(currentUrl),
                    userAgent = browser.settings.userAgentString,
                    referrer = currentUrl,
                    onProgress = { completed, _ -> downloadedPages = completed }
                )
            }
            downloading = false
            result.onSuccess { score ->
                snackbar.showSnackbar("Imported ${score.title}")
                onClose()
            }.onFailure { error -> snackbar.showSnackbar(error.message ?: "Sandbox page import failed") }
        }
    }

    BackHandler {
        val browser = webView
        if (browser?.canGoBack() == true) browser.goBack() else onClose()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("${site.label} browser") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close browser") }
                },
                actions = {
                    if (site == MusicSite.MUSESCORE_SANDBOX) {
                        IconButton(
                            onClick = ::importSandboxPdf,
                            enabled = !downloading && sandboxScoreId != null && sandboxPageCount > 0
                        ) { Icon(Icons.Default.Download, "Import sandbox PDF") }
                    }
                    IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                        Icon(Icons.Default.ArrowBack, "Browser back")
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                        Icon(Icons.Default.ArrowForward, "Browser forward")
                    }
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload page") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (progress in 0f..0.99f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (downloading) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(if (sandboxPageCount > 0) "Downloading page ${downloadedPages + 1} of $sandboxPageCount…" else "Preparing sandbox score…")
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val allowed = MusicSitePolicy.canNavigate(request.url.toString())
                                if (!allowed) scope.launch { snackbar.showSnackbar("Blocked an unsafe or non-HTTPS link") }
                                return !allowed
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                url?.let { currentUrl = it }
                                sandboxTokens.clear()
                                sandboxPageCount = 0
                                progress = 0f
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                url?.let {
                                    currentUrl = it
                                    refreshSandboxMetadata(view, it)
                                }
                                progress = 1f
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (site == MusicSite.MUSESCORE_SANDBOX &&
                                    request.url.host.equals("musescore.test", true) &&
                                    request.url.path == "/api/jmuse"
                                ) {
                                    val index = request.url.getQueryParameter("index")?.toIntOrNull()
                                    val authorization = request.requestHeaders.entries
                                        .firstOrNull { it.key.equals("authorization", true) }?.value
                                    if (index != null && !authorization.isNullOrBlank()) {
                                        view.post { sandboxTokens[index] = authorization }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            if (!MusicSitePolicy.isPdfDownload(url, mimeType, contentDisposition)) {
                                scope.launch { snackbar.showSnackbar("Scoreleaf can currently import PDF downloads from this browser") }
                                return@DownloadListener
                            }
                            val cookies = CookieManager.getInstance().getCookie(url)
                            scope.launch {
                                downloading = true
                                val result = runCatching {
                                    repo.importWebPdf(url, cookies, userAgent, contentDisposition)
                                }
                                downloading = false
                                result.onSuccess { score ->
                                    snackbar.showSnackbar("Imported ${score.title}")
                                }.onFailure { error ->
                                    snackbar.showSnackbar(error.message ?: "PDF download failed")
                                }
                            }
                        })
                        loadUrl(site.homeUrl)
                    }
                },
                update = { browser -> webView = browser }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                setDownloadListener(null)
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
        }
    }
}
