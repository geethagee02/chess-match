package dev.geetha.chessmatch.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.geetha.chessmatch.rules.Move
import dev.geetha.chessmatch.rules.Position
import dev.geetha.chessmatch.rules.Square
import dev.geetha.chessmatch.rules.TrackedPiece
import dev.geetha.chessmatch.rules.victimSquare
import dev.geetha.chessmatch.ui.theme.CheckRed
import dev.geetha.chessmatch.ui.theme.DarkSquare
import dev.geetha.chessmatch.ui.theme.Gold
import dev.geetha.chessmatch.ui.theme.LightSquare
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ChessBoard(
    pieces: List<TrackedPiece>,
    selected: Square?,
    targets: List<Move>,
    position: Position,
    lastMove: Move?,
    checkSquare: Square?,
    enabled: Boolean,
    boardEpoch: Int,
    onSquare: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureSquares = targets.filter { victimSquare(position, it) != null }.map { it.to }.toSet()
    val quietSquares = targets.map { it.to }.filter { it !in captureSquares }.toSet()
    BoxWithConstraints(
        modifier.pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val cell = size.width / 8f
                    if (cell <= 0f) return@detectTapGestures
                    val file = (offset.x / cell).toInt().coerceIn(0, 7)
                    val rankFromTop = (offset.y / cell).toInt().coerceIn(0, 7)
                    onSquare(Square(file, 7 - rankFromTop))
                }
            },
    ) {
        val cell = maxWidth / 8
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val square = Square(file, rank)
                SquareFace(
                    square = square,
                    cell = cell,
                    dark = (file + rank) % 2 == 0,
                    selected = square == selected,
                    last = lastMove != null && (square == lastMove.from || square == lastMove.to),
                    checked = square == checkSquare,
                    occupant = pieces.find { it.square == square },
                    legal = square in quietSquares || square in captureSquares,
                    capture = square in captureSquares,
                    enabled = enabled,
                    onSquare = onSquare,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val span = size.width / 8f
            fun center(square: Square) = Offset(
                (square.file + 0.5f) * span,
                (7 - square.rank + 0.5f) * span,
            )
            quietSquares.forEach { square ->
                val onDark = (square.file + square.rank) % 2 == 0
                val color = if (onDark) Color(0xFFF4EDE2).copy(alpha = 0.88f) else Color(0xFF3B2A1E).copy(alpha = 0.55f)
                drawCircle(color, radius = span * 0.14f, center = center(square))
            }
            captureSquares.forEach { square ->
                drawCircle(
                    color = Color(0xFFF4EDE2),
                    radius = span * 0.40f,
                    center = center(square),
                    style = Stroke(width = span * 0.07f, cap = StrokeCap.Round),
                )
            }
        }
        val ghosts = rememberGhosts(pieces, boardEpoch)
        ghosts.forEach { ghost ->
            key("ghost", boardEpoch, ghost.id) {
                val alpha = remember(ghost.id) { Animatable(1f) }
                LaunchedEffect(ghost.id) {
                    alpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                }
                PlacedPiece(ghost, cell, cellPx, boardEpoch, alpha.value, lift = false)
            }
        }
        pieces.forEach { piece ->
            key(boardEpoch, piece.id) {
                PlacedPiece(piece, cell, cellPx, boardEpoch, alpha = 1f, lift = true)
            }
        }
    }
}

@Composable
private fun SquareFace(
    square: Square,
    cell: Dp,
    dark: Boolean,
    selected: Boolean,
    last: Boolean,
    checked: Boolean,
    occupant: TrackedPiece?,
    legal: Boolean,
    capture: Boolean,
    enabled: Boolean,
    onSquare: (Square) -> Unit,
) {
    var color = if (dark) DarkSquare else LightSquare
    if (last) color = lerp(color, Gold, 0.42f)
    if (selected) color = lerp(color, Color(0xFFF6E7A2), 0.62f)
    val label = buildString {
        append(square.algebraic())
        if (occupant == null) append(", empty") else append(", ${occupant.side.name.lowercase()} ${occupant.type.name.lowercase()}")
        if (capture) append(", capture") else if (legal) append(", legal move")
        if (checked) append(", check")
    }
    val labelColor = if (dark) LightSquare.copy(alpha = 0.78f) else Color(0xFF6B4A2B).copy(alpha = 0.8f)
    Box(
        Modifier
            .offset {
                IntOffset(
                    (square.file * cell.toPx()).roundToInt(),
                    ((7 - square.rank) * cell.toPx()).roundToInt(),
                )
            }
            .size(cell)
            .background(color)
            .then(if (checked) Modifier.border(3.dp, CheckRed) else Modifier)
            .semantics {
                contentDescription = label
                if (enabled) {
                    onClick {
                        onSquare(square)
                        true
                    }
                }
            },
    ) {
        if (square.file == 0) {
            Text(
                text = "${square.rank + 1}",
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
            )
        }
        if (square.rank == 0) {
            Text(
                text = "${'a' + square.file}",
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            )
        }
    }
}

@Composable
private fun PlacedPiece(
    piece: TrackedPiece,
    cell: Dp,
    cellPx: Float,
    boardEpoch: Int,
    alpha: Float,
    lift: Boolean,
) {
    val target = Offset(piece.square.file * cellPx, (7 - piece.square.rank) * cellPx)
    val animated by animateOffsetAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "$boardEpoch-${piece.id}",
    )
    val moving = lift && (animated - target).getDistance() > 0.8f
    Box(
        Modifier
            .offset { IntOffset(animated.x.roundToInt(), animated.y.roundToInt()) }
            .size(cell)
            .zIndex(if (moving) 2f else if (lift) 1f else 0.5f)
            .graphicsLayer { this.alpha = alpha },
    ) {
        PieceArt(piece.side, piece.type, Modifier.fillMaxSize().padding(2.dp))
    }
}

@Composable
private fun rememberGhosts(pieces: List<TrackedPiece>, boardEpoch: Int): List<TrackedPiece> {
    val ghosts = remember { mutableStateListOf<TrackedPiece>() }
    var previous by remember { mutableStateOf(pieces) }
    var seenEpoch by remember { mutableIntStateOf(boardEpoch) }
    LaunchedEffect(pieces, boardEpoch) {
        if (seenEpoch != boardEpoch) {
            seenEpoch = boardEpoch
            previous = pieces
            ghosts.clear()
            return@LaunchedEffect
        }
        val ids = pieces.map { it.id }.toSet()
        val removed = previous.filter { it.id !in ids }
        previous = pieces
        ghosts.clear()
        if (removed.isNotEmpty()) {
            ghosts.addAll(removed)
            delay(280)
            ghosts.clear()
        }
    }
    return ghosts
}
