/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import java.io.File
import net.newpipe.app.Log
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Trouve libvlc.dll, dans l'ordre :
 * 1. le dossier choisi dans les réglages ;
 * 2. le VLC embarqué dans le MSI (ressources de l'application) ;
 * 3. le VLC portable de développement (_tools/vlc-*) ;
 * 4. un VLC installé (détection standard de vlcj : registre, Program Files).
 */
object VlcLocator {

    fun discover(configuredDir: String): Boolean {
        val dir = candidates(configuredDir).firstOrNull { File(it, "libvlc.dll").isFile }
        if (dir != null) {
            // Lu par JnaLibraryPathDirectoryProvider, qui règle aussi VLC_PLUGIN_PATH
            System.setProperty("jna.library.path", dir.absolutePath)
            Log.i("VLC : ${dir.absolutePath}")
        }
        val discovery = NativeDiscovery()
        val found = discovery.discover()
        Log.i("VLC trouvé : $found (${discovery.discoveredPath()})")
        return found
    }

    private fun candidates(configuredDir: String): List<File> = buildList {
        if (configuredDir.isNotBlank()) add(File(configuredDir))
        System.getProperty("compose.application.resources.dir")?.let { add(File(it, "vlc")) }
        File(System.getProperty("user.dir")).let { cwd ->
            listOf(cwd, cwd.parentFile).filterNotNull().forEach { base ->
                File(base, "_tools").listFiles { f -> f.isDirectory && f.name.startsWith("vlc-") }
                    ?.sortedDescending()
                    ?.forEach { add(it) }
            }
        }
    }
}
