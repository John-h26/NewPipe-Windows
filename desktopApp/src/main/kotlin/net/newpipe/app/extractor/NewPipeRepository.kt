/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.Log
import net.newpipe.app.net.DesktopDownloader
import net.newpipe.app.settings.AppSettings
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Accès à NewPipeExtractor. Toutes les requêtes passent la langue et le pays des réglages
 * explicitement à l'extracteur (sinon YouTube renvoie les titres dans sa langue par défaut).
 */
class NewPipeRepository(
    private val settings: AppSettings,
    downloader: DesktopDownloader
) {
    init {
        NewPipe.init(downloader, localization(), contentCountry())
        applyPeertubeInstance()
    }

    val services: List<StreamingService> get() = NewPipe.getServices()

    fun localization() = Localization(settings.language, settings.country)

    fun contentCountry() = ContentCountry(settings.country)

    /** À appeler après un changement de langue ou de pays dans les réglages */
    fun applyLocalization() {
        NewPipe.setupLocalization(localization(), contentCountry())
        Log.i("localisation : ${localization().localizationCode}")
    }

    fun applyPeertubeInstance() {
        ServiceList.PeerTube.setInstance(
            PeertubeInstance(settings.peertubeUrl.trimEnd('/'), settings.peertubeName)
        )
    }

    /** Vérifie qu'une instance PeerTube répond et renvoie son nom */
    suspend fun checkPeertubeInstance(url: String): String = withContext(Dispatchers.IO) {
        val instance = PeertubeInstance(url.trimEnd('/'))
        instance.fetchInstanceMetaData()
        instance.name
    }

    suspend fun search(serviceId: Int, query: String): SearchInfo = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getService(serviceId).getSearchExtractor(query)
        extractor.forceLocalization(localization())
        extractor.forceContentCountry(contentCountry())
        // Contrairement à StreamInfo.getInfo(extractor), SearchInfo.getInfo(extractor)
        // ne charge pas la page lui-même
        extractor.fetchPage()
        SearchInfo.getInfo(extractor)
    }

    suspend fun moreSearchResults(
        serviceId: Int,
        query: String,
        page: Page
    ): ListExtractor.InfoItemsPage<InfoItem> = withContext(Dispatchers.IO) {
        val service = NewPipe.getService(serviceId)
        SearchInfo.getMoreItems(service, service.searchQHFactory.fromQuery(query), page)
    }

    /** Un seul appel à l'extracteur par vidéo : le résultat sert aux détails et à la lecture */
    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getServiceByUrl(url).getStreamExtractor(url)
        extractor.forceLocalization(localization())
        extractor.forceContentCountry(contentCountry())
        StreamInfo.getInfo(extractor)
    }

    /** En-tête d'une chaîne et liste de ses onglets (vidéos, directs, playlists…) */
    suspend fun channel(url: String): ChannelInfo = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getServiceByUrl(url).getChannelExtractor(url)
        extractor.forceLocalization(localization())
        extractor.forceContentCountry(contentCountry())
        extractor.fetchPage()
        ChannelInfo.getInfo(extractor)
    }

    /** Première page d'un onglet de chaîne ([tab] vient de [ChannelInfo.getTabs]) */
    suspend fun channelTab(serviceId: Int, tab: ListLinkHandler): ChannelTabInfo =
        withContext(Dispatchers.IO) {
            val extractor = channelTabExtractor(serviceId, tab)
            extractor.fetchPage()
            ChannelTabInfo.getInfo(extractor)
        }

    suspend fun moreChannelTabItems(
        serviceId: Int,
        tab: ListLinkHandler,
        page: Page
    ): ListExtractor.InfoItemsPage<InfoItem> = withContext(Dispatchers.IO) {
        channelTabExtractor(serviceId, tab).getPage(page)
    }

    private fun channelTabExtractor(serviceId: Int, tab: ListLinkHandler) =
        NewPipe.getService(serviceId).getChannelTabExtractor(tab).apply {
            forceLocalization(localization())
            forceContentCountry(contentCountry())
        }
}
