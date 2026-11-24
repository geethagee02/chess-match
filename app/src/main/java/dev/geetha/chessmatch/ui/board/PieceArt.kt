package dev.geetha.chessmatch.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.geetha.chessmatch.rules.PieceType
import dev.geetha.chessmatch.rules.Side

@Composable
fun PieceArt(side: Side, type: PieceType, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val light = side == Side.WHITE
        val fill = if (light) Color(0xFFFFF8EE) else Color(0xFF241C16)
        val edge = if (light) Color(0xFF3A2E22) else Color(0xFFE4C58A)
        val ink = if (light) Color(0xFF3A2E22) else Color(0xFFF4EDE2)
        val span = size.minDimension
        val origin = Offset((size.width - span) / 2f, (size.height - span) / 2f)
        fun at(x: Float, y: Float) = origin + Offset(span * x, span * y)
        drawOval(
            color = Color(0x55000000),
            topLeft = at(0.20f, 0.80f),
            size = Size(span * 0.60f, span * 0.10f),
        )
        val stroke = Stroke(width = span * 0.035f, cap = StrokeCap.Round)
        when (type) {
            PieceType.PAWN -> drawPawn(::at, span, fill, edge, stroke)
            PieceType.ROOK -> drawRook(::at, span, fill, edge, stroke)
            PieceType.KNIGHT -> drawKnight(::at, span, fill, edge, ink, stroke)
            PieceType.BISHOP -> drawBishop(::at, span, fill, edge, ink, stroke)
            PieceType.QUEEN -> drawQueen(::at, span, fill, edge, stroke)
            PieceType.KING -> drawKing(::at, span, fill, edge, stroke)
        }
    }
}

private fun DrawScope.drawPawn(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    stroke: Stroke,
) {
    val base = Path().apply {
        moveTo(at(0.28f, 0.78f).x, at(0.28f, 0.78f).y)
        lineTo(at(0.72f, 0.78f).x, at(0.72f, 0.78f).y)
        lineTo(at(0.64f, 0.68f).x, at(0.64f, 0.68f).y)
        lineTo(at(0.36f, 0.68f).x, at(0.36f, 0.68f).y)
        close()
    }
    drawPath(base, fill)
    drawPath(base, edge, style = stroke)
    val stem = Path().apply {
        moveTo(at(0.42f, 0.68f).x, at(0.42f, 0.68f).y)
        lineTo(at(0.38f, 0.52f).x, at(0.38f, 0.52f).y)
        lineTo(at(0.62f, 0.52f).x, at(0.62f, 0.52f).y)
        lineTo(at(0.58f, 0.68f).x, at(0.58f, 0.68f).y)
        close()
    }
    drawPath(stem, fill)
    drawPath(stem, edge, style = stroke)
    drawCircle(fill, radius = span * 0.15f, center = at(0.50f, 0.40f))
    drawCircle(edge, radius = span * 0.15f, center = at(0.50f, 0.40f), style = stroke)
}

private fun DrawScope.drawRook(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    stroke: Stroke,
) {
    fun block(left: Float, top: Float, right: Float, bottom: Float, color: Color, outlined: Boolean) {
        val rect = Size(span * (right - left), span * (bottom - top))
        drawRect(color, topLeft = at(left, top), size = rect)
        if (outlined) drawRect(edge, topLeft = at(left, top), size = rect, style = stroke)
    }
    block(0.26f, 0.70f, 0.74f, 0.82f, fill, true)
    block(0.32f, 0.38f, 0.68f, 0.72f, fill, true)
    block(0.28f, 0.22f, 0.40f, 0.40f, fill, true)
    block(0.44f, 0.22f, 0.56f, 0.40f, fill, true)
    block(0.60f, 0.22f, 0.72f, 0.40f, fill, true)
}

private fun DrawScope.drawBishop(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    ink: Color,
    stroke: Stroke,
) {
    val base = Path().apply {
        moveTo(at(0.28f, 0.80f).x, at(0.28f, 0.80f).y)
        lineTo(at(0.72f, 0.80f).x, at(0.72f, 0.80f).y)
        lineTo(at(0.64f, 0.70f).x, at(0.64f, 0.70f).y)
        lineTo(at(0.36f, 0.70f).x, at(0.36f, 0.70f).y)
        close()
    }
    drawPath(base, fill)
    drawPath(base, edge, style = stroke)
    drawOval(fill, topLeft = at(0.34f, 0.36f), size = Size(span * 0.32f, span * 0.38f))
    drawOval(edge, topLeft = at(0.34f, 0.36f), size = Size(span * 0.32f, span * 0.38f), style = stroke)
    val mitre = Path().apply {
        moveTo(at(0.50f, 0.16f).x, at(0.50f, 0.16f).y)
        lineTo(at(0.34f, 0.42f).x, at(0.34f, 0.42f).y)
        lineTo(at(0.66f, 0.42f).x, at(0.66f, 0.42f).y)
        close()
    }
    drawPath(mitre, fill)
    drawPath(mitre, edge, style = stroke)
    drawLine(ink, at(0.50f, 0.24f), at(0.50f, 0.36f), strokeWidth = span * 0.03f, cap = StrokeCap.Round)
}

private fun DrawScope.drawQueen(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    stroke: Stroke,
) {
    val body = Path().apply {
        moveTo(at(0.30f, 0.78f).x, at(0.30f, 0.78f).y)
        lineTo(at(0.70f, 0.78f).x, at(0.70f, 0.78f).y)
        lineTo(at(0.62f, 0.48f).x, at(0.62f, 0.48f).y)
        lineTo(at(0.38f, 0.48f).x, at(0.38f, 0.48f).y)
        close()
    }
    drawPath(body, fill)
    drawPath(body, edge, style = stroke)
    val jewels = listOf(0.30f to 0.40f, 0.40f to 0.30f, 0.50f to 0.22f, 0.60f to 0.30f, 0.70f to 0.40f)
    jewels.forEach { (x, y) ->
        drawCircle(fill, radius = span * 0.07f, center = at(x, y))
        drawCircle(edge, radius = span * 0.07f, center = at(x, y), style = stroke)
    }
}

private fun DrawScope.drawKing(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    stroke: Stroke,
) {
    val body = Path().apply {
        moveTo(at(0.32f, 0.78f).x, at(0.32f, 0.78f).y)
        lineTo(at(0.68f, 0.78f).x, at(0.68f, 0.78f).y)
        lineTo(at(0.60f, 0.46f).x, at(0.60f, 0.46f).y)
        lineTo(at(0.40f, 0.46f).x, at(0.40f, 0.46f).y)
        close()
    }
    drawPath(body, fill)
    drawPath(body, edge, style = stroke)
    drawRect(fill, topLeft = at(0.46f, 0.16f), size = Size(span * 0.08f, span * 0.28f))
    drawRect(edge, topLeft = at(0.46f, 0.16f), size = Size(span * 0.08f, span * 0.28f), style = stroke)
    drawRect(fill, topLeft = at(0.38f, 0.22f), size = Size(span * 0.24f, span * 0.08f))
    drawRect(edge, topLeft = at(0.38f, 0.22f), size = Size(span * 0.24f, span * 0.08f), style = stroke)
}

private fun DrawScope.drawKnight(
    at: (Float, Float) -> Offset,
    span: Float,
    fill: Color,
    edge: Color,
    ink: Color,
    stroke: Stroke,
) {
    val base = Path().apply {
        moveTo(at(0.26f, 0.80f).x, at(0.26f, 0.80f).y)
        lineTo(at(0.74f, 0.80f).x, at(0.74f, 0.80f).y)
        lineTo(at(0.66f, 0.70f).x, at(0.66f, 0.70f).y)
        lineTo(at(0.34f, 0.70f).x, at(0.34f, 0.70f).y)
        close()
    }
    val head = Path().apply {
        moveTo(at(0.66f, 0.72f).x, at(0.66f, 0.72f).y)
        lineTo(at(0.34f, 0.72f).x, at(0.34f, 0.72f).y)
        cubicTo(
            at(0.22f, 0.70f).x, at(0.22f, 0.70f).y,
            at(0.16f, 0.56f).x, at(0.16f, 0.56f).y,
            at(0.28f, 0.48f).x, at(0.28f, 0.48f).y,
        )
        lineTo(at(0.16f, 0.50f).x, at(0.16f, 0.50f).y)
        lineTo(at(0.24f, 0.38f).x, at(0.24f, 0.38f).y)
        cubicTo(
            at(0.18f, 0.28f).x, at(0.18f, 0.28f).y,
            at(0.28f, 0.16f).x, at(0.28f, 0.16f).y,
            at(0.40f, 0.20f).x, at(0.40f, 0.20f).y,
        )
        lineTo(at(0.34f, 0.10f).x, at(0.34f, 0.10f).y)
        lineTo(at(0.48f, 0.22f).x, at(0.48f, 0.22f).y)
        cubicTo(
            at(0.64f, 0.14f).x, at(0.64f, 0.14f).y,
            at(0.80f, 0.28f).x, at(0.80f, 0.28f).y,
            at(0.74f, 0.46f).x, at(0.74f, 0.46f).y,
        )
        cubicTo(
            at(0.84f, 0.54f).x, at(0.84f, 0.54f).y,
            at(0.78f, 0.68f).x, at(0.78f, 0.68f).y,
            at(0.66f, 0.72f).x, at(0.66f, 0.72f).y,
        )
        close()
    }
    drawPath(base, fill)
    drawPath(base, edge, style = stroke)
    drawPath(head, fill)
    drawPath(head, edge, style = stroke)
    drawCircle(ink, radius = span * 0.035f, center = at(0.42f, 0.34f))
}
