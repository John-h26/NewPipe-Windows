/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.ui.App

fun main(args: Array<String>) {
    Log.i(
        "NewPipe Windows ${System.getProperty("npw.version") ?: "dev"}, " +
            "Java ${System.getProperty("java.version")}"
    )
    val graph = AppGraph.create()
    val mode = args.firstOrNull()
    if (mode == "--selftest" || mode == "--snapshot") {
        // Toujours fermer le proxy et VLC, même sur exception, sinon la JVM reste ouverte
        val code = try {
            if (mode == "--selftest") {
                SelfTest.run(
                    graph,
                    args.getOrElse(1) { "it-connect" },
                    args.getOrNull(2)?.toIntOrNull() ?: 25,
                    args.getOrNull(3)?.toIntOrNull() ?: 0
                )
            } else {
                Snapshots.run(graph, args.getOrElse(1) { "it-connect" }, java.io.File(args.getOrElse(2) { "snapshots" }))
            }
        } catch (e: Throwable) {
            Log.e("mode $mode", e)
            1
        } finally {
            graph.close()
        }
        exitProcess(code)
    }
    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
        LaunchedEffect(graph.state.fullscreen) {
            windowState.placement =
                if (graph.state.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        Window(
            onCloseRequest = {
                graph.close()
                exitApplication()
            },
            state = windowState,
            title = "NewPipe Windows",
            onPreviewKeyEvent = { handleShortcut(graph, it) }
        ) {
            AppTheme(graph.settings) { App(graph) }
        }
    }
}

/**
 * Raccourcis du lecteur. Ignorés pendant une saisie : taper « it-connect » dans la recherche
 * ne doit pas sauter deux fois à la vidéo suivante.
 */
private fun handleShortcut(graph: AppGraph, event: KeyEvent): Boolean {
    val state = graph.state
    if (event.type != KeyEventType.KeyDown || state.typing) return false
    if (event.isCtrlPressed || event.isAltPressed) return false
    if (state.screen != Screen.PLAYER && !state.fullscreen) return false
    val player = graph.player
    when (event.key) {
        Key.Spacebar, Key.K -> player?.togglePause()
        Key.DirectionLeft, Key.J -> player?.seekBy(-10_000)
        Key.DirectionRight, Key.L -> player?.seekBy(10_000)
        Key.M -> player?.toggleMute()
        Key.F -> state.fullscreen = !state.fullscreen
        Key.Escape -> if (state.fullscreen) state.fullscreen = false else return false
        Key.N -> graph.queue.next()
        Key.P -> graph.queue.previous()
        else -> return false
    }
    return true
}
