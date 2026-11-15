package com.naekimyeon.pleos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class Glyph { MARK, BACK, ARROW, BREEZE, TALK, RAIN, CLOCK, PARKING, PIN, CHECK, RESET, HEART, INFO, CLOSE }

@Composable
internal fun Symbol(glyph: Glyph, color: Color = Ink.Muted, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(vararg pts: Float) = Path().apply {
            moveTo(pts[0], pts[1]); var i = 2
            while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
        }
        withTransform({ scale(this@Canvas.size.width / 24f, this@Canvas.size.height / 24f, Offset.Zero) }) {
            when (glyph) {
                Glyph.MARK -> {
                    drawPath(Path().apply { moveTo(12f, 1f); cubicTo(12f, 8f, 17f, 12f, 23f, 12f); cubicTo(16f, 12f, 12f, 17f, 12f, 23f); cubicTo(12f, 16f, 7f, 12f, 1f, 12f); cubicTo(8f, 12f, 12f, 7f, 12f, 1f) }, color)
                    drawCircle(Ink.Background, 2f, Offset(12f, 12f))
                }
                Glyph.BACK -> { drawPath(path(14f, 5f, 7f, 12f, 14f, 19f), color, style = stroke); drawLine(color, Offset(7f, 12f), Offset(21f, 12f), 1.8f, StrokeCap.Round) }
                Glyph.ARROW -> { drawPath(path(14f, 5f, 21f, 12f, 14f, 19f), color, style = stroke); drawLine(color, Offset(3f, 12f), Offset(21f, 12f), 1.8f, StrokeCap.Round) }
                Glyph.BREEZE -> {
                    drawPath(Path().apply { moveTo(3f, 8f); lineTo(15f, 8f); cubicTo(21f, 8f, 20f, 2f, 16f, 4f) }, color, style = stroke)
                    drawPath(Path().apply { moveTo(3f, 12f); lineTo(19f, 12f); cubicTo(24f, 12f, 22f, 19f, 18f, 17f) }, color, style = stroke)
                    drawPath(Path().apply { moveTo(3f, 16f); lineTo(10f, 16f); cubicTo(14f, 16f, 13f, 22f, 9f, 20f) }, color, style = stroke)
                }
                Glyph.TALK -> {
                    drawPath(Path().apply { moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 16f); lineTo(10f, 16f); lineTo(5f, 20f); lineTo(5f, 16f); lineTo(4f, 16f); close() }, color, style = stroke)
                    drawLine(color, Offset(8f, 9f), Offset(16f, 9f), 1.8f, StrokeCap.Round)
                }
                Glyph.RAIN -> {
                    drawPath(Path().apply { moveTo(6f, 14f); cubicTo(0f, 14f, 1f, 7f, 7f, 7f); cubicTo(8f, 1f, 18f, 2f, 18f, 8f); cubicTo(24f, 8f, 24f, 14f, 19f, 14f); close() }, color, style = stroke)
                    for (x in listOf(7f, 12f, 17f)) drawLine(color, Offset(x, 17f), Offset(x - 1f, 21f), 1.8f, StrokeCap.Round)
                }
                Glyph.CLOCK -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); drawPath(path(12f, 6f, 12f, 12f, 16f, 14f), color, style = stroke) }
                Glyph.PARKING -> { drawRoundRect(color, Offset(3f, 3f), Size(18f, 18f), CornerRadius(4f), style = stroke); drawPath(Path().apply { moveTo(9f, 17f); lineTo(9f, 7f); lineTo(13f, 7f); cubicTo(18f, 7f, 18f, 13f, 13f, 13f); lineTo(9f, 13f) }, color, style = stroke) }
                Glyph.PIN -> { drawPath(Path().apply { moveTo(12f, 22f); cubicTo(-6f, 5f, 8f, -3f, 12f, 3f); cubicTo(16f, -3f, 30f, 5f, 12f, 22f); close() }, color, style = stroke); drawCircle(color, 2.4f, Offset(12f, 9f), style = stroke) }
                Glyph.CHECK -> drawPath(path(5f, 12f, 10f, 17f, 20f, 6f), color, style = stroke)
                Glyph.RESET -> { drawArc(color, 205f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke); drawPath(path(3f, 3f, 3f, 9f, 9f, 9f), color, style = stroke) }
                Glyph.HEART -> drawPath(Path().apply { moveTo(12f, 21f); cubicTo(-10f, 8f, 6f, -4f, 12f, 6f); cubicTo(18f, -4f, 34f, 8f, 12f, 21f); close() }, color, style = stroke)
                Glyph.INFO -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); drawCircle(color, 1f, Offset(12f, 7f)); drawLine(color, Offset(12f, 11f), Offset(12f, 17f), 1.8f, StrokeCap.Round) }
                Glyph.CLOSE -> { drawLine(color, Offset(6f, 6f), Offset(18f, 18f), 1.8f, StrokeCap.Round); drawLine(color, Offset(18f, 6f), Offset(6f, 18f), 1.8f, StrokeCap.Round) }
            }
        }
    }
}
