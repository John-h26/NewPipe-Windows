/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.newpipe.app.extractor.Formatting
import net.newpipe.app.extractor.StreamSelector
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Vérification de bout en bout sans interface : recherche, détails, choix du flux puis
 * lecture VLC pendant [seconds] secondes. Échoue si la lecture n'avance pas (gel).
 *
 * gradlew :desktopApp:run --args="--selftest <requête> [secondes]"
 */
object SelfTest {

    fun run(graph: AppGraph, query: String, seconds: Int, serviceId: Int = 0): Int = runBlocking {
        val repository = graph.repository
        val language = graph.settings.language
        Log.i("autotest : service $serviceId, recherche « $query » (${repository.localization().localizationCode})")
        val search = repository.search(serviceId, query)
        search.errors.forEach { Log.e("  erreur partielle de l'extracteur", it) }
        val streams = search.relatedItems.filterIsInstance<StreamInfoItem>()
        streams.take(5).forEach { Log.i("  résultat : ${it.name} · ${Formatting.itemDate(it, language)}") }
        if (streams.isEmpty()) return@runBlocking fail("aucun résultat vidéo")

        // Certains contenus n'ont aucun flux (restriction régionale, abonnement) : on essaie
        // les premiers résultats jusqu'à en trouver un lisible
        val info = streams.take(5).firstNotNullOfOrNull { item ->
            runCatching { repository.stream(item.url) }
                .onFailure { Log.i("  ignoré (${it.javaClass.simpleName}) : ${item.name}") }
                .getOrNull()
        } ?: return@runBlocking fail("aucun des 5 premiers résultats n'est lisible")
        Log.i("détails : ${info.name}")
        Log.i("  abonnés (valeur brute de l'extracteur) : ${info.uploaderSubscriberCount}")
        Log.i("  date : ${Formatting.date(info.uploadDate, info.textualUploadDate, language)}")
        Log.i("  description : ${Formatting.description(info.description).text.take(160).replace('\n', ' ')}")
        Log.i("  flux : ${info.videoOnlyStreams.size} vidéo seule, ${info.audioStreams.size} audio, " +
            "${info.videoStreams.size} muxés")

        val source = StreamSelector.select(info, graph.settings.maxResolution)
            ?: return@runBlocking fail("aucun flux lisible")
        Log.i("  choisi : ${source.label}")

        val player = graph.player ?: return@runBlocking fail("VLC introuvable")
        withContext(Dispatchers.Main) { player.play(source) }
        val half = seconds / 2
        var seekTarget = 0L
        repeat(seconds) { second ->
            delay(1000)
            Log.i("  t=${player.timeMs} ms / ${player.lengthMs} ms, état ${player.state}, " +
                "images ${player.frames.framesRendered}, affichage " +
                "${player.frames.displayWidth}x${player.frames.displayHeight}")
            if (second == half && player.lengthMs > 0) {
                // Déplacement : VLC redemande une plage qui ne commence pas à 0
                seekTarget = player.lengthMs / 2
                Log.i("  saut à $seekTarget ms")
                withContext(Dispatchers.Main) { player.seekTo(seekTarget) }
            }
        }
        val expected = seekTarget + (seconds - half - 8) * 1000L
        if (player.timeMs < expected) {
            return@runBlocking fail("la lecture n'avance pas (${player.timeMs} ms < $expected ms)")
        }
        val audioOnly = source.label.startsWith("audio")
        if (!audioOnly && player.frames.framesRendered == 0L) return@runBlocking fail("aucune image reçue")
        Log.i("autotest réussi")
        0
    }

    private fun fail(reason: String): Int {
        Log.e("autotest échoué : $reason")
        return 1
    }
}
