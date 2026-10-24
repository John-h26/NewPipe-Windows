/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extractor

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Ce que le lecteur doit ouvrir : un flux principal et, pour les vidéos sans son
 * (YouTube au-delà de 360p), un flux audio séparé
 */
data class PlaybackSource(
    val mainUrl: String,
    val audioUrl: String?,
    val label: String,
    // Durée annoncée par le service, utilisée quand VLC ne la trouve pas (PeerTube)
    val durationMs: Long = 0
)

object StreamSelector {

    /**
     * Choisit la meilleure vidéo de hauteur ≤ [maxHeight] (0 = sans limite), en préférant
     * l'audio d'origine, puis se replie sur un flux muxé, l'audio seul ou le HLS
     */
    fun select(info: StreamInfo, maxHeight: Int): PlaybackSource? =
        selectStreams(info, maxHeight)?.copy(durationMs = info.duration.coerceAtLeast(0) * 1000)

    private fun selectStreams(info: StreamInfo, maxHeight: Int): PlaybackSource? {
        val limit = if (maxHeight <= 0) Int.MAX_VALUE else maxHeight
        val audio = bestAudio(info.audioStreams)

        val videoOnly = bestVideo(info.videoOnlyStreams, limit)
        if (videoOnly != null && audio != null) {
            return PlaybackSource(
                videoOnly.content, audio.content,
                "${videoOnly.label()} + audio ${audio.averageBitrate} kb/s"
            )
        }

        val muxed = bestVideo(info.videoStreams, limit, allowHls = true)
        if (muxed != null) {
            val delivery = if (muxed.deliveryMethod == DeliveryMethod.HLS) " HLS" else ""
            return PlaybackSource(muxed.content, null, muxed.label() + delivery)
        }

        // Audio seul (SoundCloud, Bandcamp) : SoundCloud ne fournit plus que du HLS
        val audioOnly = audio ?: bestAudio(info.audioStreams, allowHls = true)
        if (audioOnly != null) {
            val delivery = if (audioOnly.deliveryMethod == DeliveryMethod.HLS) " HLS" else ""
            val bitrate = audioOnly.averageBitrate.takeIf { it > 0 }?.let { " $it kb/s" } ?: ""
            return PlaybackSource(audioOnly.content, null, "audio$bitrate ${audioOnly.format?.name ?: ""}$delivery".trim())
        }

        info.hlsUrl?.takeIf { it.isNotEmpty() }?.let { return PlaybackSource(it, null, "HLS") }
        return null
    }

    private fun bestVideo(streams: List<VideoStream>, limit: Int, allowHls: Boolean = false): VideoStream? {
        val playable = streams.filter {
            it.isPlayable() || (allowHls && it.isUrl && it.deliveryMethod == DeliveryMethod.HLS)
        }
        val withinLimit = playable.filter { it.heightOrParsed() <= limit }
        val candidates = withinLimit.ifEmpty { playable }
        return candidates.maxWithOrNull(
            compareBy<VideoStream> { it.heightOrParsed().coerceAtMost(limit) }
                .thenBy { it.seekability() }
                // À hauteur égale, le H.264 (MP4) est le plus léger à décoder
                .thenBy { if (it.format == MediaFormat.MPEG_4) 1 else 0 }
                .thenBy { it.fps }
        )
    }

    /**
     * Les MP4 « fragmentés » de PeerTube (segments HLS servis en progressif) n'ont pas d'index :
     * VLC ne peut pas s'y déplacer. La playlist HLS équivalente, elle, le permet.
     */
    private fun VideoStream.seekability(): Int = when {
        deliveryMethod == DeliveryMethod.HLS -> 1
        content.contains("-fragmented.") -> 0
        else -> 2
    }

    private fun bestAudio(streams: List<AudioStream>, allowHls: Boolean = false): AudioStream? {
        val playable = streams.filter {
            it.isPlayable() || (allowHls && it.isUrl && it.deliveryMethod == DeliveryMethod.HLS)
        }
        return playable.maxWithOrNull(
            compareBy<AudioStream> {
                when (it.audioTrackType) {
                    null, AudioTrackType.ORIGINAL -> 1
                    else -> 0
                }
            }
                .thenBy { if (it.format == MediaFormat.M4A) 1 else 0 }
                .thenBy { it.averageBitrate }
        )
    }

    private fun Stream.isPlayable() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && content.isNotEmpty()

    private fun VideoStream.heightOrParsed(): Int =
        if (height > 0) height
        else Regex("(\\d+)p").find(getResolution())?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun VideoStream.label(): String =
        "${getResolution().ifEmpty { "?" }} ${format?.name ?: ""}".trim()
}
