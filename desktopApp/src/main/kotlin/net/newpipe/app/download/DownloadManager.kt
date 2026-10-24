/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.newpipe.app.Log
import net.newpipe.app.net.GoogleVideoFetcher
import net.newpipe.app.settings.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** Choix proposé à l'utilisateur pour une vidéo */
data class DownloadOption(val label: String, val url: String, val extension: String, val size: Long?)

/** Les champs observés par l'écran sont de l'état Compose : la progression s'affiche en direct */
class DownloadJob(val id: Long, val title: String, val file: File) {
    var progress by mutableFloatStateOf(0f)
    var downloadedBytes by mutableStateOf(0L)
    var totalBytes by mutableStateOf<Long?>(null)
    var status by mutableStateOf(DownloadStatus.QUEUED)
    var error by mutableStateOf<String?>(null)
    internal var job: Job? = null
    internal var lastPublish = 0L
}

class DownloadManager(
    private val settings: AppSettings,
    private val client: OkHttpClient,
    private val fetcher: GoogleVideoFetcher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ids = AtomicLong(1)

    val jobs = mutableStateListOf<DownloadJob>()

    /**
     * Vidéos avec son (flux muxés) et pistes audio. Les vidéos sans son (DASH) ne sont pas
     * proposées : les assembler avec l'audio demande un muxeur (prévu en v0.4)
     */
    fun options(info: StreamInfo): List<DownloadOption> {
        val videos = info.videoStreams.filter { it.isDownloadable() }
            .sortedByDescending { it.height }
            .map { it.toOption() }
        val audios = info.audioStreams.filter { it.isDownloadable() }
            .sortedByDescending { it.averageBitrate }
            .map { it.toOption() }
        return videos + audios
    }

    fun start(info: StreamInfo, option: DownloadOption) {
        val dir = File(settings.downloadDir).apply { mkdirs() }
        val file = uniqueFile(dir, sanitize(info.name), option.extension)
        val job = DownloadJob(ids.getAndIncrement(), info.name, file)
        job.totalBytes = option.size
        jobs.add(0, job)
        job.job = scope.launch {
            job.status = DownloadStatus.RUNNING
            try {
                withContext(Dispatchers.IO) { download(option.url, job) }
                job.downloadedBytes = file.length()
                job.progress = 1f
                job.status = DownloadStatus.DONE
                Log.i("téléchargé : ${file.absolutePath}")
            } catch (e: CancellationException) {
                job.status = DownloadStatus.CANCELLED
                file.delete()
            } catch (e: Exception) {
                Log.e("téléchargement de ${info.url}", e)
                job.error = e.message ?: e.javaClass.simpleName
                job.status = DownloadStatus.FAILED
                file.delete()
            }
        }
    }

    fun cancel(job: DownloadJob) {
        job.job?.cancel()
    }

    fun remove(job: DownloadJob) {
        if (job.status == DownloadStatus.RUNNING) cancel(job)
        jobs.remove(job)
    }

    private suspend fun download(url: String, job: DownloadJob) {
        val context = kotlin.coroutines.coroutineContext
        FileOutputStream(job.file).buffered().use { out ->
            val counting = ProgressStream(out) { bytes ->
                context.ensureActive()
                publish(job, bytes)
            }
            if (GoogleVideoFetcher.isGoogleVideo(url)) {
                job.totalBytes = job.totalBytes ?: GoogleVideoFetcher.contentLength(url)
                val end = job.totalBytes?.minus(1)
                fetcher.copyRange(url, 0, end, counting)
            } else {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    if (job.totalBytes == null) {
                        job.totalBytes = response.body.contentLength().takeIf { it > 0 }
                    }
                    response.body.byteStream().copyTo(counting)
                }
            }
        }
    }

    private fun publish(job: DownloadJob, bytes: Long) {
        val now = System.currentTimeMillis()
        if (now - job.lastPublish < 200) return
        job.lastPublish = now
        javax.swing.SwingUtilities.invokeLater {
            job.downloadedBytes = bytes
            job.totalBytes?.let { if (it > 0) job.progress = (bytes.toFloat() / it).coerceIn(0f, 1f) }
        }
    }

    /** N'écrase jamais un fichier existant : « titre (2).mp4 », « titre (3).mp4 »… */
    private fun uniqueFile(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).$extension")
            n++
        }
        return candidate
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(150)
            .ifEmpty { "video" }

    private fun Stream.isDownloadable() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && content.isNotEmpty()

    private fun VideoStream.toOption() = DownloadOption(
        label = "Vidéo ${getResolution()} ${format?.name ?: ""}".trim(),
        url = content,
        extension = format?.suffix ?: "mp4",
        size = GoogleVideoFetcher.contentLength(content)
    )

    private fun AudioStream.toOption() = DownloadOption(
        label = "Audio ${averageBitrate.takeIf { it > 0 }?.let { "$it kb/s " } ?: ""}${format?.name ?: ""}".trim(),
        url = content,
        extension = format?.suffix ?: "m4a",
        size = GoogleVideoFetcher.contentLength(content)
    )

    private class ProgressStream(
        private val target: OutputStream,
        private val onProgress: (Long) -> Unit
    ) : OutputStream() {
        private var written = 0L
        override fun write(b: Int) {
            target.write(b)
            written++
            onProgress(written)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            written += len
            onProgress(written)
        }

        override fun flush() = target.flush()
    }
}
