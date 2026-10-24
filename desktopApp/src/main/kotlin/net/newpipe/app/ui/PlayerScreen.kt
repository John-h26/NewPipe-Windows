/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.AppGraph
import net.newpipe.app.Screen
import net.newpipe.app.extractor.Formatting
import net.newpipe.app.player.PlaybackState
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun PlayerScreen(graph: AppGraph, fullscreen: Boolean) {
    val state = graph.state
    if (fullscreen) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            VideoView(graph, Modifier.weight(1f).fillMaxWidth())
            Controls(graph, Modifier.background(MaterialTheme.colorScheme.surface))
        }
        return
    }

    if (graph.queue.current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rien en lecture", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { state.screen = Screen.SEARCH }) { Text("Rechercher une vidéo") }
            }
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
            VideoView(graph, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Controls(graph)
            Status(graph)
            state.info?.let { Details(graph, it) }
        }
        Column(Modifier.width(360.dp).fillMaxHeight().padding(vertical = 16.dp)) {
            Related(graph)
        }
    }
}

@Composable
private fun VideoView(graph: AppGraph, modifier: Modifier) {
    val player = graph.player
    val state = graph.state
    Box(
        modifier.background(Color.Black).pointerInput(player) {
            detectTapGestures(
                onTap = { player?.togglePause() },
                onDoubleTap = { state.fullscreen = !state.fullscreen }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val thumbnail = state.info?.thumbnails?.let { Formatting.thumbnail(it) }
        if (player != null && player.frames.videoSize != IntSize.Zero) {
            val frames = player.frames
            Canvas(Modifier.fillMaxSize()) {
                // Lire version ici abonne le dessin à chaque nouvelle image
                if (frames.version >= 0) frames.draw(this)
            }
        } else if (thumbnail != null) {
            // Audio seul (SoundCloud…) ou vidéo pas encore démarrée : vignette
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        val busy = state.loadingStream ||
            player?.state == PlaybackState.OPENING || player?.state == PlaybackState.BUFFERING
        if (busy) CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun Controls(graph: AppGraph, modifier: Modifier = Modifier) {
    val player = graph.player ?: return
    val state = graph.state
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val length = player.lengthMs
    val position = if (length > 0) player.timeMs.toFloat() / length else 0f

    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(Glyph.PREVIOUS, { graph.queue.previous() })
        GlyphButton(
            when (player.state) {
                PlaybackState.PLAYING, PlaybackState.BUFFERING -> Glyph.PAUSE
                else -> Glyph.PLAY
            },
            { player.togglePause() }
        )
        GlyphButton(Glyph.NEXT, { graph.queue.next() })
        Text(Formatting.millis(if (dragging) (dragValue * length).toLong() else player.timeMs))
        Slider(
            value = if (dragging) dragValue else position,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                player.seekTo((dragValue * length).toLong())
                dragging = false
            },
            enabled = length > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(Formatting.millis(length))
        GlyphButton(if (player.muted) Glyph.MUTED else Glyph.VOLUME, { player.toggleMute() })
        Slider(
            value = player.volume / 100f,
            onValueChange = { player.updateVolume((it * 100).toInt()) },
            modifier = Modifier.width(110.dp)
        )
        TextButton(onClick = { state.fullscreen = !state.fullscreen }) {
            Text(if (state.fullscreen) "Quitter le plein écran" else "Plein écran")
        }
    }
}

/** Un seul endroit pour l'état de lecture et les erreurs */
@Composable
private fun Status(graph: AppGraph) {
    val state = graph.state
    val error = state.streamError ?: graph.player?.error
    when {
        error != null -> ErrorCard(error, Modifier.padding(vertical = 8.dp))
        state.streamLabel != null -> Text(
            "Flux : ${state.streamLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Details(graph: AppGraph, info: StreamInfo) {
    val language = graph.settings.language
    var expanded by remember(info.url) { mutableStateOf(false) }
    val description = remember(info.url) { Formatting.description(info.description) }

    Spacer(Modifier.height(12.dp))
    SelectionContainer {
        Text(info.name, style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        listOfNotNull(
            info.uploaderName,
            Formatting.count(info.uploaderSubscriberCount, language)?.let { "$it abonnés" }
        ).joinToString(" · "),
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        listOfNotNull(
            Formatting.count(info.viewCount, language)?.let { "$it vues" },
            Formatting.count(info.likeCount, language)?.let { "$it j'aime" },
            Formatting.date(info.uploadDate, info.textualUploadDate, language)
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    DownloadButton(graph, info)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (description.length > 300 || description.lines().size > 6) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Afficher moins" else "Afficher plus")
        }
    }
}

@Composable
private fun DownloadButton(graph: AppGraph, info: StreamInfo) {
    var expanded by remember { mutableStateOf(false) }
    var added by remember(info.url) { mutableStateOf<String?>(null) }
    val options = remember(info.url) { graph.downloads.options(info) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty()) {
                Text("Télécharger  ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option.label + (option.size?.let { " · ${formatBytes(it)}" } ?: ""))
                        },
                        onClick = {
                            expanded = false
                            graph.downloads.start(info, option)
                            added = option.label
                        }
                    )
                }
            }
        }
        added?.let {
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { graph.state.screen = Screen.DOWNLOADS }) {
                Text("« $it » ajouté aux téléchargements")
            }
        }
    }
}

@Composable
private fun Related(graph: AppGraph) {
    val info = graph.state.info
    val related = info?.relatedItems?.filterIsInstance<StreamInfoItem>().orEmpty()
    Text(
        "À suivre",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(related) { item ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { graph.playNow(graph.queue.itemFrom(item)) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Thumbnail(Formatting.thumbnail(item.thumbnails), 128.dp, Formatting.duration(item.duration))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.uploaderName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { graph.queue.enqueue(graph.queue.itemFrom(item)) }) { Text("+") }
            }
        }
    }
}
