package com.scoreleaf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scoreleaf.app.data.ScoreRepository
import com.scoreleaf.app.model.MusicSite
import com.scoreleaf.app.model.MusicSitePolicy
import kotlinx.coroutines.launch

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
            MusicSite.entries.forEach { site ->
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
                    Text("Downloading and checking PDF…")
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
                                progress = 0f
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 1f
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
