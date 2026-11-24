package dev.geetha.chessmatch.rules

data class TrackedPiece(
    val id: String,
    val side: Side,
    val type: PieceType,
    val square: Square,
)

fun startingTracked(): List<TrackedPiece> {
    val position = startingPosition()
    val pieces = mutableListOf<TrackedPiece>()
    for (rank in 0..7) {
        for (file in 0..7) {
            val square = Square(file, rank)
            val piece = position[square] ?: continue
            val tag = if (piece.side == Side.WHITE) "w" else "b"
            val id = when (piece.type) {
                PieceType.PAWN -> "$tag${piece.type.san().ifEmpty { "P" }}-${'a' + file}"
                PieceType.KING -> "${tag}K"
                PieceType.QUEEN -> "${tag}Q"
                else -> "$tag${piece.type.san()}-${'a' + file}"
            }
            pieces += TrackedPiece(id, piece.side, piece.type, square)
        }
    }
    return pieces
}

fun relocate(pieces: List<TrackedPiece>, position: Position, move: Move): List<TrackedPiece> {
    val mover = pieces.firstOrNull { it.square == move.from }
        ?: error("No piece on ${move.from.algebraic()}")
    val capturedAt = victimSquare(position, move)
    val kept = pieces.filter { piece ->
        piece.id != mover.id && (capturedAt == null || piece.square != capturedAt)
    }
    val moved = mover.copy(square = move.to, type = move.promotion ?: mover.type)
    val placed = kept + moved
    if (!isCastle(position, move)) return placed
    val rookFrom = castleRookFrom(move)
    val rookTo = castleRookTo(move)
    return placed.map { piece ->
        if (piece.square == rookFrom && piece.type == PieceType.ROOK && piece.side == mover.side) {
            piece.copy(square = rookTo)
        } else {
            piece
        }
    }
}
