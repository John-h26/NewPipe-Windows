/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import javax.swing.SwingUtilities
import net.newpipe.app.Log
import net.newpipe.app.extractor.PlaybackSource
import net.newpipe.app.net.StreamProxy
import net.newpipe.app.settings.AppSettings
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlavePriority
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

enum class PlaybackState { IDLE, OPENING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

/**
 * Lecteur VLC (vlcj) dont les images sont rendues dans Compose par [VideoFrameBuffer].
 * Tout l'état exposé est de l'état Compose, mis à jour sur le thread de l'interface.
 */
class VlcPlayer private constructor(
    private val settings: AppSettings,
    private val proxy: StreamProxy,
    private val factory: MediaPlayerFactory,
    private val player: EmbeddedMediaPlayer
) : AutoCloseable {

    val frames = VideoFrameBuffer()

    var state by mutableStateOf(PlaybackState.IDLE)
        private set
    var timeMs by mutableLongStateOf(0L)
        private set
    var lengthMs by mutableLongStateOf(0L)
        private set
    var volume by mutableStateOf(settings.volume)
        private set
    var muted by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var fallbackLengthMs = 0L

    /** Appelé en fin de lecture (sur le thread de l'interface) pour passer à la suite */
    var onFinished: () -> Unit = {}

    init {
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(frames, frames, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) = ui {
                state = PlaybackState.PLAYING
                Log.i("lecture en cours")
            }

            override fun paused(mediaPlayer: MediaPlayer) = ui { state = PlaybackState.PAUSED }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui {
                if (newCache < 100f && state != PlaybackState.PAUSED) {
                    state = PlaybackState.BUFFERING
                } else if (newCache >= 100f && state == PlaybackState.BUFFERING) {
                    state = PlaybackState.PLAYING
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) = ui {
                state = PlaybackState.ENDED
                onFinished()
            }

            override fun error(mediaPlayer: MediaPlayer) = ui {
                state = PlaybackState.ERROR
                error = "VLC n'a pas pu lire ce flux (détails dans $vlcLogFile)"
                Log.e("VLC : erreur de lecture")
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = ui {
                timeMs = newTime
                // Interrogé depuis le thread de l'interface, jamais depuis un rappel natif de VLC
                if (frames.displayHeight == 0) {
                    player.video().videoDimension()?.let { frames.setDisplaySize(it.width, it.height) }
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) =
                ui { lengthMs = if (newLength > 0) newLength else fallbackLengthMs }
        })
    }

    fun play(source: PlaybackSource) {
        Log.i("lecture : ${source.label}")
        error = null
        state = PlaybackState.OPENING
        timeMs = 0
        fallbackLengthMs = source.durationMs
        lengthMs = source.durationMs
        frames.clear()
        proxy.reset()

        // L'audio est attaché avant le démarrage : l'ajouter après créait une course
        player.media().prepare(proxy.wrap(source.mainUrl), ":network-caching=$NETWORK_CACHING_MS")
        source.audioUrl?.let {
            player.media().slaves()?.add(MediaSlaveType.AUDIO, MediaSlavePriority.HIGHEST, proxy.wrap(it))
        }
        player.controls().play()
        player.audio().setVolume(volume)
        player.audio().isMute = muted
    }

    fun fail(message: String) {
        stop()
        error = message
        state = PlaybackState.ERROR
    }

    fun togglePause() {
        when (state) {
            PlaybackState.PLAYING, PlaybackState.BUFFERING -> player.controls().setPause(true)
            PlaybackState.PAUSED -> player.controls().setPause(false)
            PlaybackState.ENDED -> {
                player.controls().setTime(0)
                player.controls().play()
            }
            else -> Unit
        }
    }

    fun seekTo(ms: Long) {
        if (lengthMs <= 0) return
        player.controls().setTime(ms.coerceIn(0, lengthMs))
        timeMs = ms.coerceIn(0, lengthMs)
    }

    fun seekBy(deltaMs: Long) = seekTo(timeMs + deltaMs)

    fun updateVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        settings.volume = volume
        player.audio().setVolume(volume)
    }

    fun toggleMute() {
        muted = !muted
        player.audio().isMute = muted
    }

    fun stop() {
        player.controls().stop()
        frames.clear()
        state = PlaybackState.IDLE
        timeMs = 0
        lengthMs = 0
    }

    override fun close() {
        player.controls().stop()
        player.release()
        factory.release()
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)

    companion object {
        private const val NETWORK_CACHING_MS = 3000

        val vlcLogFile = File(System.getProperty("java.io.tmpdir"), "npw-vlc.log")

        /** Renvoie null si VLC est introuvable */
        fun create(settings: AppSettings, proxy: StreamProxy): VlcPlayer? {
            if (!VlcLocator.discover(settings.vlcDir)) return null
            val args = listOf(
                if (settings.hardwareDecoding) "--avcodec-hw=any" else "--avcodec-hw=none",
                "--no-video-title-show",
                // Journal détaillé de VLC, pour diagnostiquer les erreurs HTTP
                "--file-logging",
                "--logfile=${vlcLogFile.absolutePath}",
                "--log-verbose=2"
            )
            val factory = MediaPlayerFactory(args)
            Log.i("libVLC ${factory.application().version()}, journal : $vlcLogFile")
            return VlcPlayer(settings, proxy, factory, factory.mediaPlayers().newEmbeddedMediaPlayer())
        }
    }
}
