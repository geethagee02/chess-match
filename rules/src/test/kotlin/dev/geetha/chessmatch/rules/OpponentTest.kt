package dev.geetha.chessmatch.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentTest {
    private val opponent = DeviceOpponent()

    @Test
    fun openingMoveIsLegalAndCentralWhenNothingCanBeCaptured() {
        val position = startingPosition()
        val move = opponent.choose(position)
        assertNotNull(move)
        assertEquals("d2d4", move!!.uci())
        assertTrue(legalMoves(position).contains(move))
        assertNull(capturedType(position, move))
        assertEquals(move, opponent.choose(position))
    }

    @Test
    fun captureBeatsAQuietMoveAndTheQueenBeatsAPawn() {
        val position = parseFen("7k/8/8/5p2/3N4/8/2q5/K7 w - - 0 1")
        val move = opponent.choose(position)
        assertNotNull(move)
        assertEquals("d4c2", move!!.uci())
        assertEquals(PieceType.QUEEN, capturedType(position, move))
        assertTrue(legalMoves(position).any { it.uci() == "d4f5" })
        assertTrue(legalMoves(position).any { it.uci() == "d4e6" })
    }

    @Test
    fun aCheckIsAnsweredByALegalKingMove() {
        val position = parseFen("4k3/8/8/8/8/8/8/r3K1N1 w - - 0 1")
        val move = opponent.choose(position)
        assertNotNull(move)
        assertEquals(Square.parse("e1"), move!!.from)
        assertTrue(legalMoves(position).contains(move))
        assertTrue(legalMoves(position).none { it.from == Square.parse("g1") })
    }
}
