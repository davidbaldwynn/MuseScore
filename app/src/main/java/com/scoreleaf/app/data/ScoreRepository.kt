package com.scoreleaf.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.scoreleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class ScoreRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("scoreleaf", Context.MODE_PRIVATE)
    private val scoreDir = File(context.filesDir, "scores").apply { mkdirs() }

    fun scores(): List<Score> = readArray("scores").map(Score::fromJson)
    fun setlists(): List<Setlist> = readArray("setlists").map(Setlist::fromJson)
    fun scoreFile(score: Score) = File(scoreDir, score.fileName)

    suspend fun importPdf(uri: Uri, displayName: String): Score = withContext(Dispatchers.IO) {
        val safeTitle = displayName.removeSuffix(".pdf").ifBlank { "Untitled score" }
        val score = Score(title = safeTitle, fileName = "${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open PDF" }
            scoreFile(score).outputStream().use(input::copyTo)
        }
        saveScores(scores() + score)
        score
    }

    suspend fun importWebPdf(
        url: String,
        cookies: String?,
        userAgent: String?,
        contentDisposition: String?
    ): Score = withContext(Dispatchers.IO) {
        require(MusicSitePolicy.canNavigate(url)) { "Only secure HTTPS downloads are allowed" }
        val temporary = File.createTempFile("score-download-", ".pdf", context.cacheDir)
        try {
            val finalDisposition = downloadPdf(url, cookies, userAgent, temporary) ?: contentDisposition
            val header = ByteArray(1024)
            val count = temporary.inputStream().buffered().use { it.read(header) }
            require(count > 0 && String(header, 0, count, Charsets.ISO_8859_1).contains("%PDF-")) {
                "The downloaded file is not a valid PDF"
            }
            importPdf(Uri.fromFile(temporary), MusicSitePolicy.pdfFileName(finalDisposition))
        } finally {
            temporary.delete()
        }
    }

    private fun downloadPdf(
        startUrl: String,
        cookies: String?,
        userAgent: String?,
        target: File
    ): String? {
        val originHost = URI(startUrl).host
        var current = startUrl
        repeat(6) { redirectCount ->
            require(MusicSitePolicy.canNavigate(current)) { "Download redirected to an unsafe address" }
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("Accept", "application/pdf,application/octet-stream;q=0.9")
            if (!userAgent.isNullOrBlank()) connection.setRequestProperty("User-Agent", userAgent)
            if (!cookies.isNullOrBlank() && URI(current).host.equals(originHost, true)) {
                connection.setRequestProperty("Cookie", cookies)
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectCount < 5) { "Too many download redirects" }
                    val location = connection.getHeaderField("Location")
                    require(!location.isNullOrBlank()) { "Download redirect had no destination" }
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                require(status in 200..299) { "Download failed (HTTP $status)" }
                val declaredSize = connection.contentLengthLong
                require(declaredSize < MAX_WEB_PDF_BYTES || declaredSize == -1L) { "PDF is larger than 250 MB" }
                var total = 0L
                connection.inputStream.buffered().use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_WEB_PDF_BYTES) { "PDF is larger than 250 MB" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                require(total > 0) { "The downloaded PDF was empty" }
                return connection.getHeaderField("Content-Disposition")
            } finally {
                connection.disconnect()
            }
        }
        error("Too many download redirects")
    }

    fun updateScore(updated: Score) = saveScores(scores().map { if (it.id == updated.id) updated else it })

    fun deleteScore(score: Score) {
        scoreFile(score).delete()
        saveScores(scores().filterNot { it.id == score.id })
        saveSetlists(setlists().map { it.copy(scoreIds = it.scoreIds - score.id) })
        File(context.filesDir, "ink_${score.id}.json").delete()
    }

    fun createSetlist(name: String) {
        if (name.isNotBlank()) saveSetlists(setlists() + Setlist(name = name.trim()))
    }

    fun addToSetlist(setlistId: String, scoreId: String) = saveSetlists(setlists().map {
        if (it.id == setlistId) it.copy(scoreIds = SetlistEditor.add(it.scoreIds, scoreId)) else it
    })

    fun removeFromSetlist(setlistId: String, scoreId: String) = saveSetlists(setlists().map {
        if (it.id == setlistId) it.copy(scoreIds = SetlistEditor.remove(it.scoreIds, scoreId)) else it
    })

    fun moveInSetlist(setlistId: String, index: Int, direction: Int) = saveSetlists(setlists().map {
        if (it.id == setlistId) it.copy(scoreIds = SetlistEditor.move(it.scoreIds, index, direction)) else it
    })

    fun renameSetlist(setlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) saveSetlists(setlists().map { if (it.id == setlistId) it.copy(name = clean) else it })
    }

    fun deleteSetlist(setlistId: String) = saveSetlists(setlists().filterNot { it.id == setlistId })

    suspend fun exportAnnotatedPdf(score: Score): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = score.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "Score" }
        val output = File(exportDir, "$safeName-annotated.pdf")
        val descriptor = ParcelFileDescriptor.open(scoreFile(score), ParcelFileDescriptor.MODE_READ_ONLY)
        val document = PdfDocument()
        try {
            PdfRenderer(descriptor).use { renderer ->
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { sourcePage ->
                        val bitmap = Bitmap.createBitmap(sourcePage.width, sourcePage.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val pageInfo = PdfDocument.PageInfo.Builder(sourcePage.width, sourcePage.height, index + 1).create()
                        val outputPage = document.startPage(pageInfo)
                        outputPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        strokes(score.id, index).forEach { stroke ->
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = stroke.color.toInt()
                                strokeWidth = stroke.width
                                strokeCap = Paint.Cap.ROUND
                                style = Paint.Style.STROKE
                            }
                            stroke.points.zipWithNext().forEach { (start, end) ->
                                outputPage.canvas.drawLine(
                                    start.x * sourcePage.width,
                                    start.y * sourcePage.height,
                                    end.x * sourcePage.width,
                                    end.y * sourcePage.height,
                                    paint
                                )
                            }
                        }
                        document.finishPage(outputPage)
                        bitmap.recycle()
                    }
                }
            }
            output.outputStream().use(document::writeTo)
            output
        } finally {
            document.close()
            descriptor.close()
        }
    }

    fun strokes(scoreId: String, page: Int): List<InkStroke> {
        val root = inkRoot(scoreId)
        val array = root.optJSONArray(page.toString()) ?: return emptyList()
        return (0 until array.length()).map { i ->
            val s = array.getJSONObject(i)
            val pts = s.getJSONArray("points")
            InkStroke(s.getLong("color"), s.getDouble("width").toFloat(),
                (0 until pts.length()).map { p ->
                    val pair = pts.getJSONArray(p)
                    InkPoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
                },
                tool = runCatching {
                    AnnotationTool.valueOf(s.optString("tool", AnnotationTool.PEN.name))
                }.getOrDefault(AnnotationTool.PEN),
                layerId = s.optString("layerId", "default")
            )
        }
    }

    fun saveStrokes(scoreId: String, page: Int, strokes: List<InkStroke>) {
        val root = inkRoot(scoreId)
        root.put(page.toString(), JSONArray(strokes.map { stroke ->
            JSONObject().apply {
                put("color", stroke.color); put("width", stroke.width)
                put("points", JSONArray(stroke.points.map { JSONArray(listOf(it.x, it.y)) }))
                put("tool", stroke.tool.name)
                put("layerId", stroke.layerId)
            }
        }))
        File(context.filesDir, "ink_${scoreId}.json").writeText(root.toString())
    }

    private fun inkRoot(id: String): JSONObject = runCatching {
        JSONObject(File(context.filesDir, "ink_${id}.json").readText())
    }.getOrDefault(JSONObject())

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun saveScores(items: List<Score>) = prefs.edit().putString("scores", JSONArray(items.map { it.toJson() }).toString()).apply()
    private fun saveSetlists(items: List<Setlist>) = prefs.edit().putString("setlists", JSONArray(items.map { it.toJson() }).toString()).apply()

    private companion object {
        const val MAX_WEB_PDF_BYTES = 250L * 1024L * 1024L
    }
}
