/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import net.newpipe.app.AppGraph
import net.newpipe.app.Screen

private val Screen.symbol: String
    get() = when (this) {
        Screen.SEARCH -> "⌕"
        Screen.CHANNEL -> "@"
        Screen.PLAYER -> "▶"
        Screen.QUEUE -> "≡"
        Screen.DOWNLOADS -> "↓"
        Screen.SETTINGS -> "⚙"
        Screen.ABOUT -> "i"
    }

@Composable
fun App(graph: AppGraph) {
    val state = graph.state

    // La lecture suit l'identifiant de l'élément courant, pas sa position dans la file
    LaunchedEffect(graph.queue.currentId) {
        graph.load(graph.queue.current)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.fullscreen) {
            PlayerScreen(graph, fullscreen = true)
            return@Surface
        }
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight()) {
                Screen.entries.filter {
                    it != Screen.CHANNEL || state.screen == Screen.CHANNEL || state.channel != null
                }.forEach { screen ->
                    NavigationRailItem(
                        selected = state.screen == screen,
                        onClick = { state.screen = screen },
                        icon = { Text(screen.symbol, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(screen.label) }
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (state.screen) {
                    Screen.SEARCH -> SearchScreen(graph)
                    Screen.CHANNEL -> ChannelScreen(graph)
                    Screen.PLAYER -> PlayerScreen(graph, fullscreen = false)
                    Screen.QUEUE -> QueueScreen(graph)
                    Screen.DOWNLOADS -> DownloadsScreen(graph)
                    Screen.SETTINGS -> SettingsScreen(graph)
                    Screen.ABOUT -> AboutScreen(graph)
                }
            }
        }
    }
}
