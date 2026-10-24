/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.newpipe.app.download.DownloadManager
import net.newpipe.app.extractor.NewPipeRepository
import net.newpipe.app.extractor.StreamSelector
import net.newpipe.app.net.DesktopDownloader
import net.newpipe.app.net.GoogleVideoFetcher
import net.newpipe.app.net.StreamProxy
import net.newpipe.app.player.VlcPlayer
import net.newpipe.app.queue.PlayQueue
import net.newpipe.app.queue.QueueItem
import net.newpipe.app.settings.AppSettings
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.StreamInfo

enum class Screen(val label: String) {
    SEARCH("Recherche"),
    PLAYER("Lecture"),
    QUEUE("File"),
    DOWNLOADS("Téléchargements"),
    SETTINGS("Réglages"),
    ABOUT("À propos")
}

/** État de l'interface qui doit survivre aux changements d'écran */
class AppState {
    var screen by mutableStateOf(Screen.SEARCH)

    /** Vrai quand un champ texte a le focus : les raccourcis clavier sont alors ignorés */
    var typing by mutableStateOf(false)

    var query by mutableStateOf("")
    var searchedQuery by mutableStateOf("")
    var searchedServiceId by mutableStateOf(0)
    val results = mutableStateListOf<InfoItem>()
    var nextPage by mutableStateOf<Page?>(null)
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var info by mutableStateOf<StreamInfo?>(null)
    var loadingStream by mutableStateOf(false)
    var streamLabel by mutableStateOf<String?>(null)
    var streamError by mutableStateOf<String?>(null)

    var fullscreen by mutableStateOf(false)
}

/**
 * Assemble les composants de l'application (injection manuelle des dépendances)
 * et porte les actions déclenchées par l'interface
 */
class AppGraph private constructor() : AutoCloseable {
    val settings = AppSettings()
    val state = AppState()
    val queue = PlayQueue()

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val fetcher = GoogleVideoFetcher(client)
    private val proxy = StreamProxy(fetcher)

    val repository = NewPipeRepository(settings, DesktopDownloader(client))
    val downloads = DownloadManager(settings, client, fetcher)

    /** null si VLC est introuvable : l'application reste utilisable sans lecture */
    val player: VlcPlayer? = runCatching { VlcPlayer.create(settings, proxy) }
        .onFailure { Log.e("initialisation de VLC", it) }
        .getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        state.searchedServiceId = settings.serviceId
        player?.onFinished = { if (queue.next().not()) Log.i("fin de la file") }
    }

    fun search() {
        val query = state.query.trim()
        if (query.isEmpty()) return
        val serviceId = settings.serviceId
        searchJob?.cancel()
        searchJob = scope.launch {
            state.searching = true
            state.searchError = null
            state.results.clear()
            state.nextPage = null
            try {
                val info = repository.search(serviceId, query)
                state.searchedQuery = query
                state.searchedServiceId = serviceId
                state.results.addAll(info.relatedItems)
                state.nextPage = info.nextPage.takeIf { Page.isValid(it) }
                if (info.errors.isNotEmpty()) Log.i("recherche : ${info.errors.size} erreur(s) partielle(s)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("recherche « $query »", e)
                state.searchError = describe(e)
            } finally {
                state.searching = false
            }
        }
    }

    fun loadMoreResults() {
        val page = state.nextPage ?: return
        if (state.searching) return
        searchJob = scope.launch {
            state.searching = true
            try {
                val more = repository.moreSearchResults(state.searchedServiceId, state.searchedQuery, page)
                state.results.addAll(more.items)
                state.nextPage = more.nextPage.takeIf { Page.isValid(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("page suivante", e)
                state.searchError = describe(e)
            } finally {
                state.searching = false
            }
        }
    }

    /** Appelé quand l'élément courant de la file change (identifiant, pas position) */
    fun load(item: QueueItem?) {
        loadJob?.cancel()
        if (item == null) {
            player?.stop()
            state.info = null
            state.streamLabel = null
            state.streamError = null
            return
        }
        loadJob = scope.launch {
            state.loadingStream = true
            state.streamError = null
            state.streamLabel = null
            state.info = null
            player?.stop()
            try {
                val info = repository.stream(item.url)
                state.info = info
                val p = player
                if (p == null) {
                    state.streamError = "VLC est introuvable : installez VLC 3 (64 bits) " +
                        "ou indiquez son dossier dans les réglages."
                    return@launch
                }
                val source = StreamSelector.select(info, settings.maxResolution)
                if (source == null) {
                    state.streamError = "Aucun flux lisible pour cette vidéo."
                    return@launch
                }
                state.streamLabel = source.label
                p.play(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("chargement de ${item.url}", e)
                state.streamError = describe(e)
            } finally {
                state.loadingStream = false
            }
        }
    }

    fun playNow(item: QueueItem) {
        queue.playNow(item)
        state.screen = Screen.PLAYER
    }

    private fun describe(e: Exception): String = when (e) {
        is ReCaptchaException ->
            "Le service demande un captcha (trop de requêtes). Réessayez plus tard."
        is SignInConfirmNotBotException ->
            "YouTube demande de confirmer que vous n'êtes pas un robot (adresse IP signalée)."
        is AgeRestrictedContentException -> "Contenu soumis à une limite d'âge."
        is GeographicRestrictionException -> "Contenu indisponible dans votre pays."
        is ContentNotAvailableException -> "Contenu indisponible : ${e.message}"
        is java.io.IOException -> "Erreur réseau : ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun close() {
        scope.cancel()
        runCatching { player?.close() }
        proxy.close()
    }

    companion object {
        fun create() = AppGraph()
    }
}
