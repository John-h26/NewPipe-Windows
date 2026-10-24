/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.net

import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isVisionOsStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl

/**
 * Lit les flux googlevideo comme le fait YoutubeHttpDataSource dans l'app Android :
 * requêtes POST, plage demandée par le paramètre `range` (et non l'en-tête Range),
 * compteur `rn`, User-Agent du client YouTube qui a fourni l'URL.
 *
 * Les flux sont lus par blocs de [CHUNK_SIZE] octets : YouTube bride ou coupe les
 * téléchargements d'un seul bloc.
 */
class GoogleVideoFetcher(private val client: OkHttpClient) {

    private val requestNumber = AtomicLong(0)

    /**
     * Copie les octets [from, to] (inclus) de [url] dans [out]. Si [to] est null, lit jusqu'à
     * la fin du flux. Renvoie le nombre d'octets copiés.
     */
    fun copyRange(
        url: String,
        from: Long,
        to: Long?,
        out: OutputStream,
        onBytes: (Long) -> Unit = {}
    ): Long {
        var position = from
        var copied = 0L
        val buffer = ByteArray(64 * 1024)
        while (to == null || position <= to) {
            val chunkEnd = (position + CHUNK_SIZE - 1).let { if (to != null) minOf(it, to) else it }
            val received = fetchChunk(url, position, chunkEnd) { input ->
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                    onBytes(n.toLong())
                }
                total
            }
            val expected = chunkEnd - position + 1
            copied += received
            position += received
            // Bloc incomplet : fin du flux atteinte
            if (received < expected) break
        }
        out.flush()
        return copied
    }

    private fun <T> fetchChunk(
        url: String,
        from: Long,
        to: Long,
        read: (java.io.InputStream) -> T
    ): T {
        val requestUrl = "$url&range=$from-$to&rn=${requestNumber.getAndIncrement()}"
        val builder = Request.Builder()
            .url(requestUrl)
            .post(POST_BODY.toRequestBody())
            .header("Accept-Encoding", "identity")
            .header(
                "User-Agent",
                if (isVisionOsStreamingUrl(url)) getVisionOsUserAgent(null)
                else DesktopDownloader.USER_AGENT
            )
        if (isWebStreamingUrl(url)) {
            builder.header("Origin", YOUTUBE_BASE_URL)
                .header("Referer", YOUTUBE_BASE_URL)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("googlevideo HTTP ${response.code} (plage $from-$to)")
            }
            return read(response.body.byteStream())
        }
    }

    companion object {
        const val CHUNK_SIZE = 2L * 1024 * 1024
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com"

        // Même corps que YoutubeHttpDataSource.POST_BODY
        private val POST_BODY = byteArrayOf(0x78, 0)

        fun isGoogleVideo(url: String): Boolean =
            runCatching { URI(url).host?.endsWith(".googlevideo.com") == true }.getOrDefault(false)

        /** Taille totale annoncée par le paramètre `clen` des URL googlevideo */
        fun contentLength(url: String): Long? =
            Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()

        fun mimeType(url: String): String =
            Regex("[?&]mime=([^&]+)").find(url)?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
                ?: "application/octet-stream"
    }
}
