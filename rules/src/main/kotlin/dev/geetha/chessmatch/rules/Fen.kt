package dev.geetha.chessmatch.rules

const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

data class Position(
    val squares: List<Piece?>,
    val sideToMove: Side,
    val castling: CastlingRights,
    val enPassant: Square?,
    val halfmoveClock: Int,
    val fullmove: Int,
) {
    operator fun get(square: Square): Piece? = squares[square.index]

    fun toFen(): String {
        val board = buildString {
            for (rank in 7 downTo 0) {
                var empty = 0
                for (file in 0..7) {
                    val piece = squares[rank * 8 + file]
                    if (piece == null) {
                        empty += 1
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece.fen())
                    }
                }
                if (empty > 0) append(empty)
                if (rank > 0) append('/')
            }
        }
        val side = if (sideToMove == Side.WHITE) "w" else "b"
        val ep = enPassant?.algebraic() ?: "-"
        return "$board $side ${castling.toFen()} $ep $halfmoveClock $fullmove"
    }
}

fun startingPosition(): Position = parseFen(STARTING_FEN)

fun parseFen(fen: String): Position {
    val parts = fen.trim().split(Regex("\\s+"))
    require(parts.size >= 4) { "FEN needs a board, side, castling, and en-passant square." }
    val ranks = parts[0].split("/")
    require(ranks.size == 8) { "FEN board needs 8 ranks." }
    val squares = MutableList<Piece?>(64) { null }
    ranks.forEachIndexed { index, rank ->
        val rankIndex = 7 - index
        var file = 0
        for (char in rank) {
            if (char.isDigit()) {
                val skip = char.digitToInt()
                require(skip in 1..8) { "Bad FEN rank." }
                file += skip
            } else {
                val piece = Piece.fromFen(char) ?: throw IllegalArgumentException("Bad FEN piece.")
                require(file <= 7) { "FEN rank is too long." }
                squares[rankIndex * 8 + file] = piece
                file += 1
            }
        }
        require(file == 8) { "FEN rank does not fill the board." }
    }
    val side = when (parts[1]) {
        "w" -> Side.WHITE
        "b" -> Side.BLACK
        else -> throw IllegalArgumentException("FEN side must be w or b.")
    }
    val castling = parseCastling(parts[2])
    val enPassant = if (parts[3] == "-") null else Square.parse(parts[3])
    val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
    val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1
    return Position(squares, side, castling, enPassant, halfmove, fullmove)
}

fun parseCastling(text: String): CastlingRights {
    if (text == "-") return CastlingRights()
    require(text.all { it in "KQkq" }) { "Bad castling rights." }
    return CastlingRights(
        whiteKingside = 'K' in text,
        whiteQueenside = 'Q' in text,
        blackKingside = 'k' in text,
        blackQueenside = 'q' in text,
    )
}

fun parseUci(uci: String): Move {
    val raw = uci.trim().lowercase()
    if (raw.length !in 4..5) {
        throw IllegalArgumentException("Squares are files a–h and ranks 1–8.")
    }
    val from = Square.parse(raw.substring(0, 2))
    val to = Square.parse(raw.substring(2, 4))
    val promotion = if (raw.length == 5) {
        PieceType.fromLetter(raw[4]) ?: throw IllegalArgumentException(
            "Pawn promotion needs a queen, rook, bishop, or knight.",
        )
    } else {
        null
    }
    return Move(from, to, promotion)
}
