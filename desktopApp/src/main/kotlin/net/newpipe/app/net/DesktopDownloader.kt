/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.net

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Implémentation du Downloader de l'extracteur, portée de DownloaderImpl (app Android)
 */
class DesktopDownloader(val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method == "POST") ByteArray(0).toRequestBody() else null

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(request.url())
            .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            // Contrat de l'extracteur : un 429 doit devenir une ReCaptchaException
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString()
            )
        }
    }

    companion object {
        // Même valeur que DownloaderImpl.USER_AGENT de l'app Android
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    }
}
