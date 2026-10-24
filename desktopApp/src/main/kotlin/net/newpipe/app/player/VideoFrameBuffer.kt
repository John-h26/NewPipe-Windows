/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.IntSize
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import net.newpipe.app.Log
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

/**
 * Reçoit les images décodées par VLC (RV32) et les dessine dans Compose.
 *
 * Deux Bitmap Skia sont réutilisés en alternance : aucune allocation sur le tas Java par
 * image. Le canal alpha est ignoré (OPAQUE) car VLC le laisse à 0, ce qui rendait l'image
 * transparente, donc noire. Les mises à jour vers Compose sont limitées à 30 par seconde.
 */
class VideoFrameBuffer : BufferFormatCallback, RenderCallback {

    private val lock = Any()
    private var info = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    private var pixels = ByteArray(0)
    private val bitmaps = arrayOf(Bitmap(), Bitmap())
    private var front = 0
    private var hasFrame = false
    private var lastPublishNanos = 0L
    @Volatile
    var framesRendered = 0L
        private set

    /** Lu par le Canvas de la vue vidéo : chaque incrément provoque un nouveau dessin */
    var version by mutableIntStateOf(0)
        private set

    var videoSize by mutableStateOf(IntSize.Zero)
        private set

    /**
     * Taille affichable, plus petite que le tampon quand le décodeur aligne la hauteur
     * (1920x1088 pour une vidéo 1080p) : les lignes en trop ne sont pas dessinées
     */
    @Volatile
    var displayWidth = 0
        private set

    @Volatile
    var displayHeight = 0
        private set

    fun setDisplaySize(width: Int, height: Int) {
        displayWidth = width
        displayHeight = height
    }

    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        synchronized(lock) {
            info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            pixels = ByteArray(sourceWidth * sourceHeight * 4)
            hasFrame = false
            displayWidth = 0
            displayHeight = 0
        }
        Log.i("format vidéo : ${sourceWidth}x$sourceHeight")
        SwingUtilities.invokeLater { videoSize = IntSize(sourceWidth, sourceHeight) }
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit

    override fun display(
        mediaPlayer: MediaPlayer,
        nativeBuffers: Array<out ByteBuffer>,
        bufferFormat: BufferFormat
    ) {
        try {
            val now = System.nanoTime()
            if (now - lastPublishNanos < FRAME_INTERVAL_NANOS) return
            lastPublishNanos = now

            synchronized(lock) {
                val source = nativeBuffers[0].duplicate()
                source.rewind()
                val length = minOf(pixels.size, source.remaining())
                source.get(pixels, 0, length)
                val back = 1 - front
                bitmaps[back].installPixels(info, pixels, info.width * 4)
                front = back
                hasFrame = true
            }
            framesRendered++
            if (framesRendered % 120 == 0L) Log.i("images affichées : $framesRendered")
            SwingUtilities.invokeLater { version++ }
        } catch (t: Throwable) {
            Log.e("rendu d'une image VLC", t)
        }
    }

    /** Dessine la dernière image en conservant ses proportions (bandes noires sinon) */
    fun draw(scope: DrawScope) {
        synchronized(lock) {
            if (!hasFrame) return
            val bitmap = bitmaps[front]
            val w = (if (displayWidth in 1..info.width) displayWidth else info.width).toFloat()
            val h = (if (displayHeight in 1..info.height) displayHeight else info.height).toFloat()
            val scale = minOf(scope.size.width / w, scope.size.height / h)
            val dw = w * scale
            val dh = h * scale
            val left = (scope.size.width - dw) / 2
            val top = (scope.size.height - dh) / 2
            Image.makeFromBitmap(bitmap).use { image ->
                scope.drawIntoCanvas { canvas ->
                    canvas.skiaCanvas.drawImageRect(
                        image,
                        Rect.makeWH(w, h),
                        Rect.makeXYWH(left, top, dw, dh),
                        SamplingMode.LINEAR,
                        null,
                        true
                    )
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) { hasFrame = false }
        SwingUtilities.invokeLater {
            version++
            videoSize = IntSize.Zero
        }
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 33_000_000L
    }
}
