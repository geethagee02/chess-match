package dev.geetha.chessmatch.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test
    fun startingPositionHasTwentyLegalMovesAndRoundTrips() {
        val position = startingPosition()
        assertEquals(STARTING_FEN, position.toFen())
        assertEquals(20, legalMoves(position).size)
        val uci = legalMoves(position).map { it.uci() }.toSet()
        assertTrue(uci.contains("e2e4"))
        assertTrue(uci.contains("e2e3"))
        assertTrue(uci.contains("b1c3"))
        assertTrue(uci.contains("b1a3"))
        assertFalse(uci.contains("e2e5"))
        assertFalse(uci.contains("b1d2"))
        assertFalse(uci.contains("b1b3"))
        assertFalse(uci.contains("b1c2"))
    }

    @Test
    fun pawnPushCaptureAndBlock() {
        val start = startingPosition()
        val e4 = played(start, "e2e4")
        assertEquals("e4", e4.san)
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            e4.position.toFen(),
        )
        val black = legalMoves(e4.position).map { it.uci() }.toSet()
        assertEquals(20, black.size)
        assertTrue(black.contains("e7e5"))
        assertTrue(black.contains("e7e6"))
        assertFalse(black.contains("e7e4"))

        val blocked = parseFen("4k3/8/8/8/8/4p3/4P3/4K3 w - - 0 1")
        assertTrue(legalMoves(blocked).none { it.from == Square.parse("e2") })

        val single = parseFen("4k3/8/8/8/8/4P3/8/4K3 w - - 0 1")
        val fromE3 = legalMoves(single).filter { it.from == Square.parse("e3") }.map { it.uci() }
        assertEquals(listOf("e3e4"), fromE3.sorted())

        val capture = parseFen("4k3/8/8/8/3p4/4P3/8/4K3 w - - 0 1")
        val options = legalMoves(capture).filter { it.from == Square.parse("e3") }.map { it.uci() }.toSet()
        assertEquals(setOf("e3e4", "e3d4"), options)
        val taken = played(capture, "e3d4")
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), taken.position[Square.parse("d4")])
        assertNull(taken.position[Square.parse("e3")])

        val own = parseFen("4k3/8/8/8/3P4/4P3/8/4K3 w - - 0 1")
        assertTrue(play(own, "e3d4") is PlayResult.Rejected)
    }

    @Test
    fun enPassantRemovesThePawnBesideTheTarget() {
        val position = parseFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val sideways = play(position, "e5d5")
        assertTrue(sideways is PlayResult.Rejected)
        val taken = played(position, "e5d6")
        assertEquals("exd6", taken.san)
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), taken.position[Square.parse("d6")])
        assertNull(taken.position[Square.parse("d5")])
        assertNull(taken.position[Square.parse("e5")])
    }

    @Test
    fun promotionRequiresAPieceAndReplacesThePawn() {
        val position = parseFen("k7/4P3/8/8/8/8/8/4K3 w - - 0 1")
        val promotions = legalMoves(position).filter {
            it.from == Square.parse("e7") && it.to == Square.parse("e8")
        }
        assertEquals(
            setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            promotions.map { it.promotion }.toSet(),
        )
        val missing = play(position, "e7e8")
        assertTrue(missing is PlayResult.Rejected)
        assertEquals(
            "Pawn promotion needs a queen, rook, bishop, or knight.",
            (missing as PlayResult.Rejected).message,
        )
        val queen = played(position, "e7e8q")
        assertEquals(Piece(Side.WHITE, PieceType.QUEEN), queen.position[Square.parse("e8")])
        assertNull(queen.position[Square.parse("e7")])
        assertTrue(queen.san.startsWith("e8=Q"))
    }

    @Test
    fun knightJumpsAndCapturesButCannotLandOnItsOwnPiece() {
        val start = legalMoves(startingPosition()).map { it.uci() }.toSet()
        assertTrue(start.contains("b1c3"))
        assertTrue(start.contains("g1f3"))
        assertEquals("Nc3", san(startingPosition(), Move(Square.parse("b1"), Square.parse("c3"))))

        val crowded = parseFen("4k3/8/8/8/8/P1P5/8/1N2K3 w - - 0 1")
        val hops = legalMoves(crowded).filter { it.from == Square.parse("b1") }.map { it.uci() }.toSet()
        assertEquals(setOf("b1d2"), hops)

        val capture = parseFen("4k3/8/8/8/8/2p5/8/1N2K3 w - - 0 1")
        val taken = played(capture, "b1c3")
        assertEquals("Nxc3", taken.san)
        assertEquals(Piece(Side.WHITE, PieceType.KNIGHT), taken.position[Square.parse("c3")])
        assertNull(taken.position[Square.parse("b1")])
        assertTrue(play(capture, "b1b3") is PlayResult.Rejected)
        assertTrue(play(capture, "b1c4") is PlayResult.Rejected)
    }

    @Test
    fun checkRejectsAMoveThatLeavesTheKingInCheck() {
        val checked = line("e2e4", "e7e5", "d1h5", "b8c6", "h5f7")
        assertEquals(GameStatus.CHECK, checked.outcome.status)
        assertTrue(checked.outcome.inCheck)
        assertEquals("Qxf7+", checked.san)
        assertEquals(
            "r1bqkbnr/pppp1Qpp/2n5/4p3/4P3/8/PPPP1PPP/RNB1KBNR b KQkq - 0 3",
            checked.position.toFen(),
        )
        val ignored = play(checked.position, "a7a6")
        assertTrue(ignored is PlayResult.Rejected)
        assertEquals("Illegal move.", (ignored as PlayResult.Rejected).message)

        val rookCheck = parseFen("4k3/8/8/8/8/8/8/r3K1N1 w - - 0 1")
        assertEquals(GameStatus.CHECK, outcome(rookCheck).status)
        assertTrue(legalMoves(rookCheck).none { it.from == Square.parse("g1") })
        assertTrue(legalMoves(rookCheck).all { it.from == Square.parse("e1") })

        val pinned = parseFen("4k3/8/8/8/4n3/8/8/4R2K b - - 0 1")
        val pinnedMoves = legalMoves(pinned)
        assertTrue(pinnedMoves.none { it.from == Square.parse("e4") })
        assertTrue(pinnedMoves.isNotEmpty())
    }

    @Test
    fun kingCannotStepBesideTheOtherKing() {
        val position = parseFen("8/8/8/4k3/8/3K4/8/8 w - - 0 1")
        val kingMoves = legalMoves(position).filter { it.from == Square.parse("d3") }.map { it.uci() }.toSet()
        assertFalse(kingMoves.contains("d3e4"))
        assertFalse(kingMoves.contains("d3d4"))
        assertTrue(kingMoves.contains("d3c4"))
        assertTrue(kingMoves.contains("d3c3"))
    }

    @Test
    fun checkmateAndStalemate() {
        val mate = line("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus.CHECKMATE, mate.outcome.status)
        assertEquals("0-1", mate.outcome.result)
        assertEquals("Qh4#", mate.san)
        assertEquals(
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
            mate.position.toFen(),
        )
        val later = play(mate.position, "e2e3")
        assertTrue(later is PlayResult.Rejected)
        assertEquals("The game is already over.", (later as PlayResult.Rejected).message)

        val stalemate = parseFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        val drawn = outcome(stalemate)
        assertEquals(GameStatus.STALEMATE, drawn.status)
        assertFalse(drawn.inCheck)
        assertEquals("1/2-1/2", drawn.result)
        assertTrue(legalMoves(stalemate).isEmpty())

        val backRank = parseFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.CHECKMATE, outcome(backRank).status)
        assertEquals("1-0", outcome(backRank).result)
    }

    @Test
    fun castlingMovesBothPiecesAndRefusesACheckedPath() {
        val open = parseFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castle = played(open, "e1g1")
        assertEquals("O-O", castle.san)
        assertEquals(Piece(Side.WHITE, PieceType.KING), castle.position[Square.parse("g1")])
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), castle.position[Square.parse("f1")])
        assertNull(castle.position[Square.parse("e1")])
        assertNull(castle.position[Square.parse("h1")])
        assertTrue(play(open, "e1c1") is PlayResult.Played)

        val through = parseFen("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertTrue(play(through, "e1g1") is PlayResult.Rejected)
        assertTrue(play(through, "e1c1") is PlayResult.Played)

        val outOfCheck = parseFen("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertEquals(GameStatus.CHECK, outcome(outOfCheck).status)
        assertTrue(play(outOfCheck, "e1g1") is PlayResult.Rejected)
        assertTrue(play(outOfCheck, "e1c1") is PlayResult.Rejected)
    }

    @Test
    fun trackedPiecesKeepTheirIdThroughAMoveACaptureAndCastling() {
        val start = startingTracked()
        val pawn = start.first { it.id == "wP-e" }
        assertEquals("e2", pawn.square.algebraic())
        val afterE4 = relocate(start, startingPosition(), Move(Square.parse("e2"), Square.parse("e4")))
        assertEquals("e4", afterE4.first { it.id == "wP-e" }.square.algebraic())

        val capturePosition = parseFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val capturePieces = listOf(
            TrackedPiece("wP-e", Side.WHITE, PieceType.PAWN, Square.parse("e4")),
            TrackedPiece("bP-d", Side.BLACK, PieceType.PAWN, Square.parse("d5")),
        )
        val captured = relocate(capturePieces, capturePosition, Move(Square.parse("e4"), Square.parse("d5")))
        assertEquals(listOf("wP-e"), captured.map { it.id })
        assertEquals("d5", captured.single().square.algebraic())

        val ep = parseFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val epPieces = listOf(
            TrackedPiece("wP-e", Side.WHITE, PieceType.PAWN, Square.parse("e5")),
            TrackedPiece("bP-d", Side.BLACK, PieceType.PAWN, Square.parse("d5")),
        )
        val passed = relocate(epPieces, ep, Move(Square.parse("e5"), Square.parse("d6")))
        assertNull(passed.find { it.id == "bP-d" })
        assertEquals("d6", passed.first { it.id == "wP-e" }.square.algebraic())

        val castle = parseFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castlePieces = listOf(
            TrackedPiece("wK", Side.WHITE, PieceType.KING, Square.parse("e1")),
            TrackedPiece("wR-h", Side.WHITE, PieceType.ROOK, Square.parse("h1")),
        )
        val castled = relocate(castlePieces, castle, Move(Square.parse("e1"), Square.parse("g1")))
        assertEquals("g1", castled.first { it.id == "wK" }.square.algebraic())
        assertEquals("f1", castled.first { it.id == "wR-h" }.square.algebraic())
    }

    private fun played(position: Position, uci: String): PlayResult.Played {
        return when (val result = play(position, uci)) {
            is PlayResult.Played -> result
            is PlayResult.Rejected -> error("$uci rejected: ${result.message}")
        }
    }

    private fun line(vararg uci: String): PlayResult.Played {
        var position = startingPosition()
        lateinit var last: PlayResult.Played
        for (move in uci) {
            last = played(position, move)
            position = last.position
        }
        return last
    }
}
