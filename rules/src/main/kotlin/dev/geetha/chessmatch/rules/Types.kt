package dev.geetha.chessmatch.rules

enum class Side {
    WHITE,
    BLACK,
}

fun Side.opposite(): Side = if (this == Side.WHITE) Side.BLACK else Side.WHITE

enum class PieceType(val letter: Char, val value: Int) {
    KING('k', 0),
    QUEEN('q', 9),
    ROOK('r', 5),
    BISHOP('b', 3),
    KNIGHT('n', 3),
    PAWN('p', 1),
    ;

    fun san(): String = when (this) {
        KNIGHT -> "N"
        BISHOP -> "B"
        ROOK -> "R"
        QUEEN -> "Q"
        KING -> "K"
        PAWN -> ""
    }

    companion object {
        fun fromLetter(letter: Char): PieceType? =
            entries.firstOrNull { it.letter == letter.lowercaseChar() }
    }
}

data class Piece(val side: Side, val type: PieceType) {
    fun fen(): Char = if (side == Side.WHITE) type.letter.uppercaseChar() else type.letter

    companion object {
        fun fromFen(char: Char): Piece? {
            val type = PieceType.fromLetter(char) ?: return null
            val side = if (char.isUpperCase()) Side.WHITE else Side.BLACK
            return Piece(side, type)
        }
    }
}

data class Square(val file: Int, val rank: Int) {
    val index: Int get() = rank * 8 + file

    fun algebraic(): String = "${'a' + file}${rank + 1}"

    companion object {
        fun parse(text: String): Square {
            val raw = text.trim().lowercase()
            if (raw.length != 2 || raw[0] !in 'a'..'h' || raw[1] !in '1'..'8') {
                throw IllegalArgumentException("Squares are files a–h and ranks 1–8.")
            }
            return Square(raw[0] - 'a', raw[1] - '1')
        }

        fun at(index: Int): Square = Square(index % 8, index / 8)
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
) {
    fun uci(): String = from.algebraic() + to.algebraic() + (promotion?.letter ?: "")
}

data class CastlingRights(
    val whiteKingside: Boolean = false,
    val whiteQueenside: Boolean = false,
    val blackKingside: Boolean = false,
    val blackQueenside: Boolean = false,
) {
    fun toFen(): String {
        val text = buildString {
            if (whiteKingside) append('K')
            if (whiteQueenside) append('Q')
            if (blackKingside) append('k')
            if (blackQueenside) append('q')
        }
        return text.ifEmpty { "-" }
    }

    fun without(square: Square): CastlingRights = when (square.algebraic()) {
        "a1" -> copy(whiteQueenside = false)
        "h1" -> copy(whiteKingside = false)
        "a8" -> copy(blackQueenside = false)
        "h8" -> copy(blackKingside = false)
        else -> this
    }
}

enum class GameStatus {
    ONGOING,
    CHECK,
    CHECKMATE,
    STALEMATE,
}

data class Outcome(
    val status: GameStatus,
    val inCheck: Boolean,
    val result: String,
)
