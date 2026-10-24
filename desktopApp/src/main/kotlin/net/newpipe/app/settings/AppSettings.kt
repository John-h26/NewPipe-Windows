/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.settings

import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.prefs.Preferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Réglages persistants (registre Windows, via java.util.prefs) exposés comme état Compose :
 * un écran qui lit un réglage se recompose quand il change.
 */
class AppSettings(
    private val prefs: Preferences = Preferences.userRoot().node("net/newpipe/windows")
) {
    // Langue et pays envoyés aux services (titres, dates relatives, tendances)
    var language by string("language", "fr")
    var country by string("country", "FR")

    var theme by string("theme", THEME_SYSTEM)

    // Hauteur maximale de la vidéo lue (en pixels), 0 = meilleure disponible
    var maxResolution by int("max_resolution", 1080)

    // Lu au démarrage du lecteur : un changement s'applique au prochain lancement
    var hardwareDecoding by boolean("hardware_decoding", false)

    var volume by int("volume", 80)

    var peertubeUrl by string("peertube_url", "https://framatube.org")
    var peertubeName by string("peertube_name", "Framatube")

    var serviceId by int("service_id", 0)

    var downloadDir by string(
        "download_dir",
        File(System.getProperty("user.home"), "Videos${File.separator}NewPipe").path
    )

    // Dossier de VLC (contenant libvlc.dll) ; vide = détection automatique
    var vlcDir by string("vlc_dir", "")

    private fun string(key: String, default: String) = Pref(
        key, prefs.get(key, default)
    ) { prefs.put(key, it) }

    private fun int(key: String, default: Int) = Pref(
        key, prefs.getInt(key, default)
    ) { prefs.putInt(key, it) }

    private fun boolean(key: String, default: Boolean) = Pref(
        key, prefs.getBoolean(key, default)
    ) { prefs.putBoolean(key, it) }

    private class Pref<T>(
        @Suppress("unused") val key: String,
        initial: T,
        private val write: (T) -> Unit
    ) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            write(value)
        }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_BLACK = "black"
    }
}
