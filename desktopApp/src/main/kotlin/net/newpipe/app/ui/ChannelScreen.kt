/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.AppGraph
import net.newpipe.app.extractor.Formatting
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

/** Page d'une chaîne : en-tête, onglets fournis par le service et contenu de l'onglet choisi */
@Composable
fun ChannelScreen(graph: AppGraph) {
    val state = graph.state
    val language = graph.settings.language
    val info = state.channel
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.screen = state.channelOrigin }) { Text("← Retour") }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                info?.avatars?.let { Formatting.thumbnail(it) }?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info?.name ?: state.channelName ?: "Chaîne",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                info?.let { Formatting.abbreviatedCount(it.subscriberCount, language, it.serviceId) }?.let {
                    Text(
                        "$it abonnés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (info != null) {
                TextButton(onClick = { uriHandler.openUri(info.url) }) { Text("Ouvrir dans le navigateur") }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (info != null && info.tabs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                info.tabs.forEach { tab ->
                    if (tab == state.channelTab) {
                        Button(onClick = { graph.selectChannelTab(tab) }) { Text(tab.label()) }
                    } else {
                        OutlinedButton(onClick = { graph.selectChannelTab(tab) }) { Text(tab.label()) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        state.channelError?.let {
            ErrorCard(it)
            Spacer(Modifier.height(8.dp))
        }
        if (state.channelItems.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                when {
                    state.channelLoading -> CircularProgressIndicator()
                    state.channelError != null -> Unit
                    info != null && info.tabs.isEmpty() -> Text("Cette chaîne n'a aucun contenu à afficher.")
                    info != null -> Text("Aucun contenu dans cet onglet.")
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.channelItems) { _, item ->
                ResultRow(graph, item, language, channelName = info?.name)
            }
            if (state.channelItems.isNotEmpty() && (state.channelNextPage != null || state.channelLoading)) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (state.channelLoading) {
                            CircularProgressIndicator()
                        } else {
                            OutlinedButton(onClick = { graph.loadMoreChannelItems() }) {
                                Text("Plus de contenus")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ListLinkHandler.label(): String = when (val tab = contentFilters.firstOrNull()) {
    ChannelTabs.VIDEOS -> "Vidéos"
    ChannelTabs.TRACKS -> "Titres"
    ChannelTabs.SHORTS -> "Shorts"
    ChannelTabs.LIVESTREAMS -> "Directs"
    ChannelTabs.CHANNELS -> "Chaînes"
    ChannelTabs.PLAYLISTS -> "Playlists"
    ChannelTabs.ALBUMS -> "Albums"
    ChannelTabs.LIKES -> "J'aime"
    ChannelTabs.PODCASTS -> "Podcasts"
    ChannelTabs.COURSES -> "Cours"
    else -> tab?.replaceFirstChar { it.uppercase() } ?: "Contenu"
}
