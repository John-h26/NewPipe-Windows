/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.ui.App
import org.jetbrains.skia.EncodedImageFormat
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Rend l'interface hors écran pendant un scénario réel et enregistre une capture PNG par
 * écran, pour vérifier la mise en page sans session graphique interactive.
 *
 * gradlew :desktopApp:run --args="--snapshot <requête> <dossier>"
 */
object Snapshots {

    fun run(graph: AppGraph, query: String, outDir: File): Int = runBlocking(Dispatchers.Main) {
        outDir.mkdirs()
        val scene = ImageComposeScene(1280, 820, Density(1f)) {
            AppTheme(graph.settings) { App(graph) }
        }
        val start = System.nanoTime()

        suspend fun pump(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                scene.render(System.nanoTime() - start)
                delay(50)
            }
        }

        fun shot(name: String) {
            val image = scene.render(System.nanoTime() - start)
            val png = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return
            File(outDir, "$name.png").writeBytes(png)
            Log.i("capture : $name.png")
        }

        val state = graph.state
        state.query = query
        graph.search()
        pump(300)
        while (state.searching) pump(200)
        pump(3000)
        shot("1-recherche")

        val streams = state.results.filterIsInstance<StreamInfoItem>()
        if (streams.isEmpty()) return@runBlocking 1
        graph.playNow(graph.queue.itemFrom(streams[0]))
        pump(12_000)
        shot("2-lecture")

        // Chaîne de la vidéo en cours, ouverte comme le fait un clic sur son nom
        state.info?.uploaderUrl?.takeIf { it.isNotBlank() }?.let { url ->
            graph.openChannel(url, state.info?.uploaderName)
            pump(300)
            while (state.channelLoading) pump(200)
            pump(3000)
            shot("7-chaine")
            state.channel?.tabs?.getOrNull(1)?.let { tab ->
                graph.selectChannelTab(tab)
                pump(300)
                while (state.channelLoading) pump(200)
                pump(3000)
                shot("8-chaine-onglet")
            }
        }

        streams.drop(1).take(3).forEach { graph.queue.enqueue(graph.queue.itemFrom(it)) }
        state.screen = Screen.QUEUE
        pump(2000)
        shot("3-file")

        val info = state.info
        val option = info?.let { graph.downloads.options(it).lastOrNull() }
        if (info != null && option != null) {
            graph.downloads.start(info, option)
            state.screen = Screen.DOWNLOADS
            pump(4000)
            shot("4-telechargements")
            graph.downloads.jobs.toList().forEach { graph.downloads.remove(it) }
        }

        state.screen = Screen.SETTINGS
        pump(1000)
        shot("5-reglages")

        state.screen = Screen.ABOUT
        pump(1000)
        shot("6-a-propos")

        withContext(Dispatchers.Main) { graph.player?.stop() }
        scene.close()
        0
    }
}
