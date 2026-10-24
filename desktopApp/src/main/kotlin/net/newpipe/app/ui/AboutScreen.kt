/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.newpipe.app.AppGraph
import net.newpipe.app.player.VlcPlayer

@Composable
fun AboutScreen(graph: AppGraph) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("NewPipe Windows", style = MaterialTheme.typography.headlineSmall)
        Text("Version ${System.getProperty("npw.version") ?: "dev"}")
        Spacer(Modifier.height(16.dp))
        SelectionContainer {
            Text(
                """
                Client libre pour YouTube, PeerTube, SoundCloud, media.ccc.de et Bandcamp,
                basé sur NewPipeExtractor et sur le module :desktopApp de NewPipe.

                Licence : GNU GPL v3 ou ultérieure. Le code source de cette version doit
                être publié sous la même licence.

                NewPipeExtractor : commit e8f89145a830b4d6cae32412007f24ea8b36c73a
                Base upstream NewPipe (dev) : commit acb7dfd658a8478686d0f8a0c389269d0ca8df95
                Lecteur : VLC via vlcj 4.9.0 (GPL v3), ${if (graph.player != null) "chargé" else "introuvable"}
                Journal VLC : ${VlcPlayer.vlcLogFile.absolutePath}

                NewPipe Windows n'est ni affilié à YouTube ni approuvé par Google.
                """.trimIndent()
            )
        }
    }
}
