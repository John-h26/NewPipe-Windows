/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser
import kotlinx.coroutines.launch
import net.newpipe.app.AppGraph
import net.newpipe.app.settings.AppSettings

private val languages = listOf(
    "fr" to "Français", "en" to "English", "de" to "Deutsch", "es" to "Español",
    "it" to "Italiano", "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski",
    "ru" to "Русский", "tr" to "Türkçe", "ar" to "العربية", "ja" to "日本語"
)

private val countries = listOf(
    "FR" to "France", "BE" to "Belgique", "CH" to "Suisse", "CA" to "Canada",
    "LU" to "Luxembourg", "MA" to "Maroc", "US" to "États-Unis", "GB" to "Royaume-Uni",
    "DE" to "Allemagne", "ES" to "Espagne", "IT" to "Italie", "PT" to "Portugal"
)

private val resolutions = listOf(
    360 to "360p", 480 to "480p", 720 to "720p", 1080 to "1080p",
    1440 to "1440p", 2160 to "2160p (4K)", 0 to "Meilleure disponible"
)

@Composable
fun SettingsScreen(graph: AppGraph) {
    val settings = graph.settings
    val state = graph.state
    val scope = rememberCoroutineScope()
    var peertubeUrl by remember { mutableStateOf(settings.peertubeUrl) }
    var peertubeStatus by remember { mutableStateOf<String?>(null) }
    var vlcDir by remember { mutableStateOf(settings.vlcDir) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 900.dp)
    ) {
        Text("Réglages", style = MaterialTheme.typography.titleLarge)

        SectionTitle("Contenu")
        Dropdown("Langue du contenu", languages, settings.language, {
            settings.language = it
            graph.repository.applyLocalization()
        })
        Dropdown("Pays du contenu", countries, settings.country, {
            settings.country = it
            graph.repository.applyLocalization()
        })
        Hint("Envoyés aux services : langue des titres et des dates. Nouvelle recherche nécessaire.")

        SectionTitle("Lecture")
        Dropdown("Résolution maximale", resolutions, settings.maxResolution, { settings.maxResolution = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Décodage matériel", Modifier.width(220.dp))
            Switch(checked = settings.hardwareDecoding, onCheckedChange = { settings.hardwareDecoding = it })
        }
        Hint("Pris en compte au prochain démarrage de l'application.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                state, vlcDir, { vlcDir = it },
                label = "Dossier de VLC (vide = détection automatique)",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { chooseFolder(vlcDir)?.let { vlcDir = it } }) { Text("Parcourir") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { settings.vlcDir = vlcDir.trim() }) { Text("Enregistrer") }
        }
        Hint(
            if (graph.player != null) "VLC est chargé. Un nouveau dossier s'applique au prochain démarrage."
            else "VLC est introuvable : installez VLC 3 64 bits ou indiquez son dossier, puis redémarrez."
        )

        SectionTitle("PeerTube")
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(state, peertubeUrl, { peertubeUrl = it }, label = "Instance", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                peertubeStatus = "Vérification…"
                scope.launch {
                    peertubeStatus = try {
                        val name = graph.repository.checkPeertubeInstance(peertubeUrl)
                        settings.peertubeUrl = peertubeUrl.trim().trimEnd('/')
                        settings.peertubeName = name
                        graph.repository.applyPeertubeInstance()
                        "Instance active : $name"
                    } catch (e: Exception) {
                        "Instance injoignable : ${e.message}"
                    }
                }
            }) { Text("Appliquer") }
        }
        Hint(peertubeStatus ?: "Instance active : ${settings.peertubeName} (${settings.peertubeUrl})")

        SectionTitle("Téléchargements")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(settings.downloadDir, Modifier.weight(1f))
            OutlinedButton(onClick = { chooseFolder(settings.downloadDir)?.let { settings.downloadDir = it } }) {
                Text("Changer")
            }
        }

        SectionTitle("Apparence")
        Dropdown(
            "Thème",
            listOf(
                AppSettings.THEME_SYSTEM to "Système",
                AppSettings.THEME_LIGHT to "Clair",
                AppSettings.THEME_DARK to "Sombre",
                AppSettings.THEME_BLACK to "Noir"
            ),
            settings.theme,
            { settings.theme = it }
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun chooseFolder(start: String): String? {
    val chooser = JFileChooser(start.ifBlank { null }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choisir un dossier"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
