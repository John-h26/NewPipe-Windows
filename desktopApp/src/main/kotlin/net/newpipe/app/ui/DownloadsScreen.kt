/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.io.File
import net.newpipe.app.AppGraph
import net.newpipe.app.download.DownloadJob
import net.newpipe.app.download.DownloadStatus

@Composable
fun DownloadsScreen(graph: AppGraph) {
    val downloads = graph.downloads
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Téléchargements", style = MaterialTheme.typography.titleLarge)
                Text(
                    graph.settings.downloadDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { openFolder(File(graph.settings.downloadDir)) }) {
                Text("Ouvrir le dossier")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (downloads.jobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun téléchargement. Utilisez « Télécharger » depuis la page d'une vidéo.")
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(downloads.jobs, key = { it.id }) { job -> JobCard(graph, job) }
        }
    }
}

@Composable
private fun JobCard(graph: AppGraph, job: DownloadJob) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleSmall)
            Text(
                job.file.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (job.status == DownloadStatus.RUNNING && job.totalBytes == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sizes = formatBytes(job.downloadedBytes) +
                    (job.totalBytes?.let { " / ${formatBytes(it)}" } ?: "")
                val status = when (job.status) {
                    DownloadStatus.QUEUED -> "En attente"
                    DownloadStatus.RUNNING -> "En cours · $sizes"
                    DownloadStatus.DONE -> "Terminé · ${formatBytes(job.downloadedBytes)}"
                    DownloadStatus.FAILED -> "Échec : ${job.error}"
                    DownloadStatus.CANCELLED -> "Annulé"
                }
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                when (job.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                        TextButton(onClick = { graph.downloads.cancel(job) }) { Text("Annuler") }
                    DownloadStatus.DONE -> {
                        TextButton(onClick = { openFile(job.file) }) { Text("Ouvrir") }
                        TextButton(onClick = { graph.downloads.remove(job) }) { Text("Retirer") }
                    }
                    else -> TextButton(onClick = { graph.downloads.remove(job) }) { Text("Retirer") }
                }
            }
        }
    }
}

private fun openFolder(dir: File) {
    dir.mkdirs()
    runCatching { Desktop.getDesktop().open(dir) }
}

private fun openFile(file: File) {
    runCatching { Desktop.getDesktop().open(file) }
}
