/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Icônes du lecteur dessinées en vectoriel : les caractères ⏮ ⏸ 🔊 dépendent des polices
 * emoji de Windows et s'affichaient en petits carrés de couleur
 */
enum class Glyph { PREVIOUS, PLAY, PAUSE, NEXT, VOLUME, MUTED }

@Composable
fun GlyphButton(glyph: Glyph, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            when (glyph) {
                Glyph.PLAY -> drawPath(triangle(w * 0.22f, 0f, w * 0.92f, h), color)
                Glyph.PAUSE -> {
                    drawRect(color, Offset(w * 0.2f, h * 0.1f), Size(w * 0.2f, h * 0.8f))
                    drawRect(color, Offset(w * 0.6f, h * 0.1f), Size(w * 0.2f, h * 0.8f))
                }
                Glyph.PREVIOUS -> {
                    drawRect(color, Offset(w * 0.12f, h * 0.12f), Size(w * 0.12f, h * 0.76f))
                    drawPath(triangle(w * 0.88f, h * 0.12f, w * 0.26f, h * 0.88f), color)
                }
                Glyph.NEXT -> {
                    drawPath(triangle(w * 0.12f, h * 0.12f, w * 0.74f, h * 0.88f), color)
                    drawRect(color, Offset(w * 0.76f, h * 0.12f), Size(w * 0.12f, h * 0.76f))
                }
                Glyph.VOLUME -> {
                    speaker(color)
                    drawArc(
                        color, -45f, 90f, false, Offset(w * 0.3f, h * 0.25f), Size(w * 0.4f, h * 0.5f),
                        style = Stroke(w * 0.08f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color, -45f, 90f, false, Offset(w * 0.2f, h * 0.08f), Size(w * 0.75f, h * 0.84f),
                        style = Stroke(w * 0.08f, cap = StrokeCap.Round)
                    )
                }
                Glyph.MUTED -> {
                    speaker(color)
                    val stroke = w * 0.08f
                    drawLine(color, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.92f, h * 0.64f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(w * 0.92f, h * 0.36f), Offset(w * 0.66f, h * 0.64f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

/** Triangle pointant de (x0) vers (x1), sur la hauteur [top, bottom] */
private fun triangle(x0: Float, top: Float, x1: Float, bottom: Float) = Path().apply {
    moveTo(x0, top)
    lineTo(x1, (top + bottom) / 2)
    lineTo(x0, bottom)
    close()
}

private fun DrawScope.speaker(color: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.08f, h * 0.36f)
            lineTo(w * 0.26f, h * 0.36f)
            lineTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.5f, h * 0.86f)
            lineTo(w * 0.26f, h * 0.64f)
            lineTo(w * 0.08f, h * 0.64f)
            close()
        },
        color
    )
}
