/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import net.newpipe.app.Log

/**
 * Proxy HTTP local entre VLC et googlevideo.
 *
 * VLC ne sait faire que des GET avec l'en-tête Range, alors que YouTube attend des POST avec
 * le paramètre `range` et le bon User-Agent. Le proxy reçoit les GET de VLC sur 127.0.0.1 et
 * les traduit avec [GoogleVideoFetcher]. Les autres services sont lus directement par VLC.
 */
class StreamProxy(private val fetcher: GoogleVideoFetcher) : AutoCloseable {

    private val targets = ConcurrentHashMap<String, String>()
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    init {
        server.createContext("/s/") { exchange ->
            try {
                handle(exchange)
            } catch (e: IOException) {
                // VLC ferme la connexion à chaque déplacement dans la vidéo : cas normal
                Log.i("proxy : connexion interrompue (${e.message})")
            } catch (e: Exception) {
                Log.e("proxy", e)
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newCachedThreadPool { r ->
            Thread(r, "npw-proxy").apply { isDaemon = true }
        }
        server.start()
        Log.i("proxy démarré sur le port ${server.address.port}")
    }

    /** Renvoie l'URL à donner à VLC pour [url] */
    fun wrap(url: String): String {
        if (!GoogleVideoFetcher.isGoogleVideo(url)) return url
        val id = UUID.randomUUID().toString()
        targets[id] = url
        return "http://127.0.0.1:${server.address.port}/s/$id"
    }

    /** Oublie les URL des lectures précédentes */
    fun reset() = targets.clear()

    private fun handle(exchange: HttpExchange) {
        val id = exchange.requestURI.path.removePrefix("/s/")
        val target = targets[id]
        if (target == null) {
            exchange.sendResponseHeaders(404, -1)
            return
        }

        val total = GoogleVideoFetcher.contentLength(target)
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        val range = rangeHeader?.let { parseRange(it) }
        val headers = exchange.responseHeaders
        headers.add("Content-Type", GoogleVideoFetcher.mimeType(target))
        headers.add("Accept-Ranges", "bytes")

        if (total == null) {
            // Taille inconnue : flux continu sans possibilité de déplacement
            exchange.sendResponseHeaders(200, 0)
            if (exchange.requestMethod != "HEAD") {
                fetcher.copyRange(target, 0, null, exchange.responseBody)
            }
            return
        }

        val start = range?.first ?: 0L
        val end = minOf(range?.second ?: (total - 1), total - 1)
        if (start >= total) {
            headers.add("Content-Range", "bytes */$total")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val length = end - start + 1
        if (range != null) headers.add("Content-Range", "bytes $start-$end/$total")

        val status = if (range != null) 206 else 200
        if (exchange.requestMethod == "HEAD") {
            headers.add("Content-Length", length.toString())
            exchange.sendResponseHeaders(status, -1)
            return
        }
        Log.i("proxy : ${exchange.requestMethod} octets $start-$end/$total")
        exchange.sendResponseHeaders(status, length)
        fetcher.copyRange(target, start, end, exchange.responseBody)
    }

    /** « bytes=a-b » ou « bytes=a- » → (a, b ou null) */
    private fun parseRange(header: String): Pair<Long, Long?>? {
        val match = Regex("bytes=(\\d*)-(\\d*)").find(header) ?: return null
        val (from, to) = match.destructured
        if (from.isEmpty()) return null
        return from.toLong() to to.toLongOrNull()
    }

    override fun close() = server.stop(0)
}
