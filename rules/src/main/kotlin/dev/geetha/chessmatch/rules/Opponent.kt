package dev.geetha.chessmatch.rules

import kotlin.math.abs

/**
 * Picks one legal move. A capture always outranks a quiet move.
 * Among equal captures, a check and a promotion break the tie.
 * Quiet moves lean toward the centre so an opening is not always a2a3.
 */
class DeviceOpponent {
    fun choose(position: Position): Move? {
        val legal = legalMoves(position)
        if (legal.isEmpty()) return null
        val captures = legal.filter { capturedType(position, it) != null }
        val pool = (if (captures.isNotEmpty()) captures else legal).sortedBy { it.uci() }
        return pool.maxBy { score(position, it) }
    }

    private fun score(position: Position, move: Move): Int {
        val capture = (capturedType(position, move)?.value ?: 0) * 100
        val promotion = when (move.promotion) {
            PieceType.QUEEN -> 4
            PieceType.ROOK -> 3
            PieceType.BISHOP -> 2
            PieceType.KNIGHT -> 1
            else -> 0
        }
        val check = if (givesCheck(position, move)) 2 else 0
        val centrality = 8 - (abs(move.to.file - 3) + abs(move.to.file - 4))
        val advance = if (position.sideToMove == Side.WHITE) move.to.rank else 7 - move.to.rank
        return capture + check + promotion + centrality + advance
    }
}
