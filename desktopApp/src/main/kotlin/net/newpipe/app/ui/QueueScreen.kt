/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.AppGraph
import net.newpipe.app.Screen
import net.newpipe.app.extractor.Formatting

@Composable
fun QueueScreen(graph: AppGraph) {
    val queue = graph.queue
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "File de lecture (${queue.items.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { queue.clear() }, enabled = queue.items.isNotEmpty()) {
                Text("Vider")
            }
        }
        if (queue.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("La file est vide. Ajoutez des vidéos avec « + File » dans la recherche.")
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue.items, key = { _, item -> item.id }) { index, item ->
                val isCurrent = item.id == queue.currentId
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        )
                        .clickable {
                            queue.select(item.id)
                            graph.state.screen = Screen.PLAYER
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", Modifier.width(32.dp))
                    Thumbnail(item.thumbnailUrl, 128.dp, Formatting.duration(item.durationSeconds))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (isCurrent) "En lecture · " else "") + item.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { queue.move(item.id, -1) }, enabled = index > 0) { Text("▲") }
                    TextButton(
                        onClick = { queue.move(item.id, 1) },
                        enabled = index < queue.items.lastIndex
                    ) { Text("▼") }
                    TextButton(onClick = { queue.remove(item.id) }) { Text("✕") }
                }
            }
        }
    }
}
