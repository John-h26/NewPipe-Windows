/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.AppGraph
import net.newpipe.app.extractor.Formatting
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun SearchScreen(graph: AppGraph) {
    val state = graph.state
    val settings = graph.settings
    // Requête et résultats vivent dans AppState : ils survivent au changement d'onglet
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServicePicker(graph)
            Spacer(Modifier.width(12.dp))
            AppTextField(
                state = state,
                value = state.query,
                onValueChange = { state.query = it },
                placeholder = "Rechercher",
                modifier = Modifier.weight(1f).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                        graph.search()
                        true
                    } else {
                        false
                    }
                }
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { graph.search() }, enabled = !state.searching) { Text("Rechercher") }
        }
        Spacer(Modifier.height(12.dp))
        state.searchError?.let {
            ErrorCard(it)
            Spacer(Modifier.height(8.dp))
        }
        if (state.results.isEmpty() && state.searching) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.results) { _, item ->
                ResultRow(graph, item, settings.language)
            }
            if (state.nextPage != null || (state.searching && state.results.isNotEmpty())) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (state.searching) {
                            CircularProgressIndicator()
                        } else {
                            OutlinedButton(onClick = { graph.loadMoreResults() }) {
                                Text("Plus de résultats")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicePicker(graph: AppGraph) {
    var expanded by remember { mutableStateOf(false) }
    val services = graph.repository.services
    val current = services.firstOrNull { it.serviceId == graph.settings.serviceId }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text((current?.serviceInfo?.name ?: "Service") + "  ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            services.forEach { service ->
                DropdownMenuItem(
                    text = { Text(service.serviceInfo.name) },
                    onClick = {
                        expanded = false
                        graph.settings.serviceId = service.serviceId
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultRow(graph: AppGraph, item: InfoItem, language: String) {
    val uriHandler = LocalUriHandler.current
    when (item) {
        is StreamInfoItem -> Row(
            Modifier.fillMaxWidth()
                .clickable { graph.playNow(graph.queue.itemFrom(item)) }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(Formatting.thumbnail(item.thumbnails), 176.dp, Formatting.duration(item.duration))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        item.uploaderName,
                        Formatting.count(item.viewCount, language)?.let { "$it vues" },
                        Formatting.itemDate(item, language)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { graph.queue.enqueue(graph.queue.itemFrom(item)) }) {
                Text("+ File")
            }
        }

        // Les pages chaîne et playlist arrivent en v0.4 : ouverture dans le navigateur
        is ChannelInfoItem -> OtherRow(
            item,
            "Chaîne · " + listOfNotNull(
                Formatting.count(item.subscriberCount, language)?.let { "$it abonnés" },
                "ouvrir dans le navigateur"
            ).joinToString(" · ")
        ) { uriHandler.openUri(item.url) }

        is PlaylistInfoItem -> OtherRow(
            item,
            "Playlist · ${item.streamCount.takeIf { it >= 0 }?.let { "$it vidéos · " } ?: ""}" +
                "ouvrir dans le navigateur"
        ) { uriHandler.openUri(item.url) }

        else -> OtherRow(item, item.infoType.name) { uriHandler.openUri(item.url) }
    }
}

@Composable
private fun OtherRow(item: InfoItem, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(Formatting.thumbnail(item.thumbnails), 176.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
