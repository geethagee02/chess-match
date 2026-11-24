package dev.geetha.chessmatch.rules

import kotlin.math.abs

fun san(position: Position, move: Move): String {
    val piece = position[move.from] ?: return move.uci()
    val next = positionAfter(position, move)
    val suffix = when (outcome(next).status) {
        GameStatus.CHECKMATE -> "#"
        GameStatus.CHECK -> "+"
        else -> ""
    }
    if (piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
        return (if (move.to.file > move.from.file) "O-O" else "O-O-O") + suffix
    }
    val capture = victimSquare(position, move) != null
    val destination = move.to.algebraic()
    val promotion = move.promotion?.let { "=${it.san()}" } ?: ""
    if (piece.type == PieceType.PAWN) {
        val body = if (capture) "${'a' + move.from.file}x$destination" else destination
        return body + promotion + suffix
    }
    val others = legalMoves(position).filter { other ->
        other.to == move.to &&
            other.from != move.from &&
            other.promotion == move.promotion &&
            position[other.from]?.type == piece.type
    }
    val disambiguation = when {
        others.isEmpty() -> ""
        others.none { it.from.file == move.from.file } -> "${'a' + move.from.file}"
        others.none { it.from.rank == move.from.rank } -> "${move.from.rank + 1}"
        else -> move.from.algebraic()
    }
    val takes = if (capture) "x" else ""
    return piece.type.san() + disambiguation + takes + destination + promotion + suffix
}
