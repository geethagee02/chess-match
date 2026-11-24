package dev.geetha.chessmatch.rules

import kotlin.math.abs

sealed class PlayResult {
    data class Played(
        val move: Move,
        val position: Position,
        val san: String,
        val outcome: Outcome,
    ) : PlayResult()

    data class Rejected(val message: String) : PlayResult()
}

private val knightDeltas = listOf(
    1 to 2,
    2 to 1,
    -1 to 2,
    -2 to 1,
    1 to -2,
    2 to -1,
    -1 to -2,
    -2 to -1,
)

private val rookDirections = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val bishopDirections = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

fun findKing(position: Position, side: Side): Square? {
    for (index in position.squares.indices) {
        val piece = position.squares[index]
        if (piece?.side == side && piece.type == PieceType.KING) return Square.at(index)
    }
    return null
}

fun isAttacked(position: Position, target: Square, by: Side): Boolean {
    for (index in position.squares.indices) {
        val piece = position.squares[index] ?: continue
        if (piece.side != by) continue
        if (attacks(position, Square.at(index), target, piece)) return true
    }
    return false
}

fun victimSquare(position: Position, move: Move): Square? {
    if (position[move.to] != null) return move.to
    val moving = position[move.from] ?: return null
    if (moving.type == PieceType.PAWN && move.from.file != move.to.file && position.enPassant == move.to) {
        return Square(move.to.file, move.from.rank)
    }
    return null
}

fun capturedType(position: Position, move: Move): PieceType? {
    val square = victimSquare(position, move) ?: return null
    return position[square]?.type
}

fun isCastle(position: Position, move: Move): Boolean {
    val moving = position[move.from] ?: return false
    return moving.type == PieceType.KING && abs(move.to.file - move.from.file) == 2
}

fun castleRookFrom(move: Move): Square = Square(if (move.to.file == 6) 7 else 0, move.from.rank)

fun castleRookTo(move: Move): Square = Square(if (move.to.file == 6) 5 else 3, move.from.rank)

fun givesCheck(position: Position, move: Move): Boolean {
    val next = positionAfter(position, move)
    val enemy = position.sideToMove.opposite()
    val king = findKing(next, enemy) ?: return false
    return isAttacked(next, king, position.sideToMove)
}

fun legalMoves(position: Position): List<Move> {
    val side = position.sideToMove
    val enemy = side.opposite()
    return pseudoLegal(position).filter { move ->
        val next = positionAfter(position, move)
        val king = findKing(next, side) ?: return@filter false
        !isAttacked(next, king, enemy)
    }
}

fun outcome(position: Position): Outcome {
    val king = findKing(position, position.sideToMove)
    val inCheck = king != null && isAttacked(position, king, position.sideToMove.opposite())
    val moves = legalMoves(position)
    val status = when {
        moves.isEmpty() && inCheck -> GameStatus.CHECKMATE
        moves.isEmpty() -> GameStatus.STALEMATE
        inCheck -> GameStatus.CHECK
        else -> GameStatus.ONGOING
    }
    val result = when (status) {
        GameStatus.CHECKMATE -> if (position.sideToMove == Side.WHITE) "0-1" else "1-0"
        GameStatus.STALEMATE -> "1/2-1/2"
        else -> "*"
    }
    return Outcome(status, inCheck, result)
}

fun positionAfter(position: Position, move: Move): Position {
    val moving = position[move.from] ?: error("No piece on ${move.from.algebraic()}")
    val victim = position[move.to]
    val squares = position.squares.toMutableList()
    val capturedAt = victimSquare(position, move)
    squares[move.from.index] = null
    if (capturedAt != null && capturedAt != move.to) {
        squares[capturedAt.index] = null
    }
    squares[move.to.index] = if (move.promotion != null) moving.copy(type = move.promotion) else moving
    if (isCastle(position, move)) {
        val rookFrom = castleRookFrom(move)
        val rookTo = castleRookTo(move)
        squares[rookTo.index] = squares[rookFrom.index]
        squares[rookFrom.index] = null
    }
    var rights = position.castling
    if (moving.type == PieceType.KING) {
        rights = if (moving.side == Side.WHITE) {
            rights.copy(whiteKingside = false, whiteQueenside = false)
        } else {
            rights.copy(blackKingside = false, blackQueenside = false)
        }
    }
    if (moving.type == PieceType.ROOK) rights = rights.without(move.from)
    if (victim?.type == PieceType.ROOK) rights = rights.without(move.to)
    val enPassant = if (moving.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
        Square(move.from.file, (move.from.rank + move.to.rank) / 2)
    } else {
        null
    }
    val halfmove = if (moving.type == PieceType.PAWN || capturedAt != null) 0 else position.halfmoveClock + 1
    val fullmove = if (position.sideToMove == Side.BLACK) position.fullmove + 1 else position.fullmove
    return Position(
        squares = squares,
        sideToMove = position.sideToMove.opposite(),
        castling = rights,
        enPassant = enPassant,
        halfmoveClock = halfmove,
        fullmove = fullmove,
    )
}

fun play(position: Position, uci: String): PlayResult {
    val requested = try {
        parseUci(uci)
    } catch (error: IllegalArgumentException) {
        return PlayResult.Rejected(error.message ?: "Squares are files a–h and ranks 1–8.")
    }
    return play(position, requested.from, requested.to, requested.promotion)
}

fun play(position: Position, from: Square, to: Square, promotion: PieceType?): PlayResult {
    val current = outcome(position)
    if (current.status == GameStatus.CHECKMATE || current.status == GameStatus.STALEMATE) {
        return PlayResult.Rejected("The game is already over.")
    }
    if (promotion == PieceType.KING || promotion == PieceType.PAWN) {
        return PlayResult.Rejected("Pawn promotion needs a queen, rook, bishop, or knight.")
    }
    val piece = position[from] ?: return PlayResult.Rejected("That square is empty.")
    if (piece.side != position.sideToMove) {
        return PlayResult.Rejected("It is not that piece's turn.")
    }
    val candidates = legalMoves(position).filter { it.from == from && it.to == to }
    if (candidates.isEmpty()) return PlayResult.Rejected("Illegal move.")
    val move = if (candidates.any { it.promotion != null }) {
        if (promotion == null) {
            return PlayResult.Rejected("Pawn promotion needs a queen, rook, bishop, or knight.")
        }
        candidates.firstOrNull { it.promotion == promotion }
            ?: return PlayResult.Rejected("Pawn promotion needs a queen, rook, bishop, or knight.")
    } else if (promotion != null) {
        return PlayResult.Rejected("That move is not a promotion.")
    } else {
        candidates.first()
    }
    val next = positionAfter(position, move)
    return PlayResult.Played(move, next, san(position, move), outcome(next))
}

private fun pseudoLegal(position: Position): List<Move> {
    val moves = mutableListOf<Move>()
    val side = position.sideToMove
    for (index in position.squares.indices) {
        val piece = position.squares[index] ?: continue
        if (piece.side != side) continue
        val from = Square.at(index)
        when (piece.type) {
            PieceType.PAWN -> addPawn(position, from, side, moves::add)
            PieceType.KNIGHT -> addKnight(position, from, side, moves::add)
            PieceType.BISHOP -> addSlides(position, from, side, bishopDirections, moves::add)
            PieceType.ROOK -> addSlides(position, from, side, rookDirections, moves::add)
            PieceType.QUEEN -> addSlides(position, from, side, rookDirections + bishopDirections, moves::add)
            PieceType.KING -> {
                addKingSteps(position, from, side, moves::add)
                addCastles(position, side, moves::add)
            }
        }
    }
    return moves
}

private fun addPawn(position: Position, from: Square, side: Side, add: (Move) -> Unit) {
    val forward = if (side == Side.WHITE) 1 else -1
    val startRank = if (side == Side.WHITE) 1 else 6
    val lastRank = if (side == Side.WHITE) 7 else 0
    val one = from.rank + forward
    if (one !in 0..7) return
    val push = Square(from.file, one)
    if (position[push] == null) {
        addPawnMove(from, push, one == lastRank, add)
        if (from.rank == startRank) {
            val two = Square(from.file, from.rank + forward * 2)
            if (position[two] == null) add(Move(from, two))
        }
    }
    for (fileDelta in listOf(-1, 1)) {
        val file = from.file + fileDelta
        if (file !in 0..7) continue
        val dest = Square(file, one)
        val occupant = position[dest]
        if (occupant != null && occupant.side != side) {
            addPawnMove(from, dest, one == lastRank, add)
        } else if (occupant == null && position.enPassant == dest) {
            val captured = position[Square(dest.file, from.rank)]
            if (captured?.type == PieceType.PAWN && captured.side != side) add(Move(from, dest))
        }
    }
}

private fun addPawnMove(from: Square, to: Square, promote: Boolean, add: (Move) -> Unit) {
    if (!promote) {
        add(Move(from, to))
        return
    }
    add(Move(from, to, PieceType.QUEEN))
    add(Move(from, to, PieceType.ROOK))
    add(Move(from, to, PieceType.BISHOP))
    add(Move(from, to, PieceType.KNIGHT))
}

private fun addKnight(position: Position, from: Square, side: Side, add: (Move) -> Unit) {
    for ((fileDelta, rankDelta) in knightDeltas) {
        val file = from.file + fileDelta
        val rank = from.rank + rankDelta
        if (file !in 0..7 || rank !in 0..7) continue
        val dest = Square(file, rank)
        val occupant = position[dest]
        if (occupant == null || occupant.side != side) add(Move(from, dest))
    }
}

private fun addKingSteps(position: Position, from: Square, side: Side, add: (Move) -> Unit) {
    for (fileDelta in -1..1) {
        for (rankDelta in -1..1) {
            if (fileDelta == 0 && rankDelta == 0) continue
            val file = from.file + fileDelta
            val rank = from.rank + rankDelta
            if (file !in 0..7 || rank !in 0..7) continue
            val dest = Square(file, rank)
            val occupant = position[dest]
            if (occupant == null || occupant.side != side) add(Move(from, dest))
        }
    }
}

private fun addCastles(position: Position, side: Side, add: (Move) -> Unit) {
    val rank = if (side == Side.WHITE) 0 else 7
    val kingHome = Square(4, rank)
    val king = position[kingHome]
    if (king?.type != PieceType.KING || king.side != side) return
    val enemy = side.opposite()
    // The king may not castle out of check, or through a square the enemy attacks.
    if (isAttacked(position, kingHome, enemy)) return
    val rights = position.castling
    val kingSide = if (side == Side.WHITE) rights.whiteKingside else rights.blackKingside
    val queenSide = if (side == Side.WHITE) rights.whiteQueenside else rights.blackQueenside
    if (kingSide && rookReady(position, Square(7, rank), side)) {
        val passed = listOf(Square(5, rank), Square(6, rank))
        if (passed.all { position[it] == null && !isAttacked(position, it, enemy) }) {
            add(Move(kingHome, Square(6, rank)))
        }
    }
    if (queenSide && rookReady(position, Square(0, rank), side)) {
        val empty = listOf(Square(1, rank), Square(2, rank), Square(3, rank))
        val passed = listOf(Square(3, rank), Square(2, rank))
        if (empty.all { position[it] == null } && passed.all { !isAttacked(position, it, enemy) }) {
            add(Move(kingHome, Square(2, rank)))
        }
    }
}

private fun rookReady(position: Position, square: Square, side: Side): Boolean {
    val rook = position[square]
    return rook?.type == PieceType.ROOK && rook.side == side
}

private fun addSlides(
    position: Position,
    from: Square,
    side: Side,
    directions: List<Pair<Int, Int>>,
    add: (Move) -> Unit,
) {
    for ((fileDelta, rankDelta) in directions) {
        var file = from.file + fileDelta
        var rank = from.rank + rankDelta
        while (file in 0..7 && rank in 0..7) {
            val dest = Square(file, rank)
            val occupant = position[dest]
            if (occupant == null) {
                add(Move(from, dest))
            } else {
                if (occupant.side != side) add(Move(from, dest))
                break
            }
            file += fileDelta
            rank += rankDelta
        }
    }
}

private fun attacks(position: Position, from: Square, target: Square, piece: Piece): Boolean {
    if (from == target) return false
    val fileDelta = target.file - from.file
    val rankDelta = target.rank - from.rank
    return when (piece.type) {
        PieceType.KNIGHT ->
            (abs(fileDelta) == 1 && abs(rankDelta) == 2) || (abs(fileDelta) == 2 && abs(rankDelta) == 1)
        PieceType.KING -> abs(fileDelta) <= 1 && abs(rankDelta) <= 1
        PieceType.PAWN -> {
            val forward = if (piece.side == Side.WHITE) 1 else -1
            rankDelta == forward && abs(fileDelta) == 1
        }
        PieceType.BISHOP -> fileDelta != 0 && abs(fileDelta) == abs(rankDelta) && clearBetween(position, from, target)
        PieceType.ROOK ->
            (fileDelta == 0 || rankDelta == 0) && clearBetween(position, from, target)
        PieceType.QUEEN ->
            (fileDelta == 0 || rankDelta == 0 || abs(fileDelta) == abs(rankDelta)) &&
                clearBetween(position, from, target)
    }
}

private fun clearBetween(position: Position, from: Square, target: Square): Boolean {
    val stepFile = step(target.file - from.file)
    val stepRank = step(target.rank - from.rank)
    var file = from.file + stepFile
    var rank = from.rank + stepRank
    while (file != target.file || rank != target.rank) {
        if (position[Square(file, rank)] != null) return false
        file += stepFile
        rank += stepRank
    }
    return true
}

private fun step(delta: Int): Int = when {
    delta > 0 -> 1
    delta < 0 -> -1
    else -> 0
}
