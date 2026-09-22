package com.scoreleaf.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.caverock.androidsvg.SVG
import com.scoreleaf.app.model.MuseScoreSandboxPolicy
import com.scoreleaf.app.model.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.min

class MuseScoreSandboxImporter(
    private val context: Context,
    private val repository: ScoreRepository
) {
    suspend fun downloadAndImport(
        scoreId: String,
        title: String,
        pageCount: Int,
        firstPageUrl: String?,
        authorizationByPage: Map<Int, String>,
        cookies: String?,
        userAgent: String?,
        referrer: String,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Score = withContext(Dispatchers.IO) {
        require(pageCount in 1..MAX_PAGES) { "Invalid sandbox page count" }
        require(MuseScoreSandboxPolicy.scoreId(referrer) == scoreId) { "Score does not match the sandbox page" }
        val temporaryPdf = File.createTempFile("sandbox-score-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            repeat(pageCount) { index ->
                val mediaUrl = if (index == 0 && firstPageUrl != null) {
                    requireNotNull(MuseScoreSandboxPolicy.trustedMediaUrl(firstPageUrl)) {
                        "Sandbox returned an invalid first page"
                    }
                } else {
                    val token = authorizationByPage[index] ?: authorizationByPage.values.firstOrNull()
                        ?: error("The sandbox did not authorize page ${index + 1}")
                    val apiUrl = requireNotNull(MuseScoreSandboxPolicy.pageApiUrl(scoreId, index))
                    val response = request(apiUrl, token, cookies, userAgent, referrer, MAX_JSON_BYTES)
                    requireNotNull(MuseScoreSandboxPolicy.mediaUrl(response.bytes.toString(Charsets.UTF_8))) {
                        "Sandbox returned an invalid page ${index + 1} response"
                    }
                }
                val media = request(mediaUrl, null, null, userAgent, referrer, MAX_IMAGE_BYTES)
                val bitmap = decodePage(media.bytes, media.contentType)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
                withContext(Dispatchers.Main) { onProgress(index + 1, pageCount) }
            }
            temporaryPdf.outputStream().use(document::writeTo)
            repository.importPdf(
                Uri.fromFile(temporaryPdf),
                "${title.trim().ifBlank { "Sandbox score" }}.pdf"
            )
        } finally {
            document.close()
            temporaryPdf.delete()
        }
    }

    private fun request(
        url: String,
        authorization: String?,
        cookies: String?,
        userAgent: String?,
        referrer: String,
        maximumBytes: Int
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json,image/svg+xml,image/png,image/jpeg")
        connection.setRequestProperty("Referer", referrer)
        if (!authorization.isNullOrBlank()) connection.setRequestProperty("Authorization", authorization)
        if (!userAgent.isNullOrBlank()) connection.setRequestProperty("User-Agent", userAgent)
        if (!cookies.isNullOrBlank() && URI(url).host.equals(URI(referrer).host, true)) {
            connection.setRequestProperty("Cookie", cookies)
        }
        try {
            require(connection.responseCode in 200..299) { "Sandbox request failed (HTTP ${connection.responseCode})" }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= maximumBytes) { "Sandbox response was too large" }
            val output = ByteArrayOutputStream()
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maximumBytes) { "Sandbox response was too large" }
                    output.write(buffer, 0, read)
                }
            }
            require(output.size() > 0) { "Sandbox returned an empty response" }
            return Response(output.toByteArray(), connection.contentType.orEmpty().substringBefore(';'))
        } finally {
            connection.disconnect()
        }
    }

    private fun decodePage(bytes: ByteArray, contentType: String): Bitmap {
        val beginsWithSvg = bytes.toString(Charsets.UTF_8).trimStart().let {
            it.startsWith("<svg") || it.startsWith("<?xml")
        }
        if (contentType.equals("image/svg+xml", true) || beginsWithSvg) {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
            val sourceWidth = svg.documentWidth.takeIf { it.isFinite() && it > 0 } ?: 1200f
            val sourceHeight = svg.documentHeight.takeIf { it.isFinite() && it > 0 } ?: 1700f
            val scale = min(1f, MAX_BITMAP_EDGE / maxOf(sourceWidth, sourceHeight))
            val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
            val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
            svg.setDocumentWidth(width.toFloat())
            svg.setDocumentHeight(height.toFloat())
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.WHITE)
                svg.renderToCanvas(android.graphics.Canvas(bitmap))
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Sandbox page was not a supported image" }
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > MAX_BITMAP_EDGE) sample *= 2
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })) { "Sandbox page could not be decoded" }
    }

    private data class Response(val bytes: ByteArray, val contentType: String)

    private companion object {
        const val MAX_PAGES = 500
        const val MAX_JSON_BYTES = 256 * 1024
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        const val MAX_BITMAP_EDGE = 2400f
    }
}
