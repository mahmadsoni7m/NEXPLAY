package com.nexplay.game

import com.nexplay.game.feature.backgammon.BgState
import com.nexplay.game.feature.checkers.CkAI
import com.nexplay.game.feature.checkers.CkState
import com.nexplay.game.feature.chess.ChessAI
import com.nexplay.game.feature.chess.ChessState
import com.nexplay.game.feature.domino.DominoRound
import com.nexplay.game.feature.domino.Side
import com.nexplay.game.data.Achievements
import com.nexplay.game.data.Profile
import com.nexplay.game.data.Rewards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginesTest {
    @Test fun checkersInitialMoves() {
        assertEquals(7, CkState.initial().legalMoves().size)
    }

    @Test fun checkersAiReturnsLegalMove() {
        val s = CkState.initial()
        val m = CkAI(1).choose(s)
        assertNotNull(m)
        assertTrue(s.legalMoves().any { it.path == m!!.path })
    }

    @Test fun checkersWireRoundTrip() {
        val s = CkState.initial()
        assertEquals(s.toWire(), CkState.fromWire(s.toWire()).toWire())
    }

    @Test fun chessInitial20Moves() {
        assertEquals(20, ChessState.initial().legalMoves().size)
    }

    @Test fun chessFenRoundTrip() {
        assertEquals(ChessState.START_FEN, ChessState.initial().toFen())
    }

    @Test fun chessFoolsMate() {
        var s = ChessState.initial()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) {
            val m = s.legalMoves().first { it.uci() == uci }
            s = s.apply(m)
        }
        assertEquals("checkmate:b", s.status())
    }

    @Test fun chessAiReturnsLegalMove() {
        val s = ChessState.initial()
        val m = ChessAI(1).choose(s)
        assertNotNull(m)
        assertTrue(s.legalMoves().contains(m))
    }

    @Test fun backgammonStartPipCount() {
        val s = BgState.initial()
        assertEquals(167, s.pip(1))
        assertEquals(167, s.pip(-1))
    }

    @Test fun backgammonRollProducesMoves() {
        val s = BgState.initial().roll()
        assertTrue(s.dice.isNotEmpty())
        assertTrue(s.canMove())
    }

    @Test fun dominoDealsSevenEach() {
        val r = DominoRound()
        assertEquals(7, r.hands[0].size)
        assertEquals(7, r.hands[1].size)
        assertEquals(14, r.boneyard.size)
    }

    @Test fun dominoFirstMoveIsPlayable() {
        val r = DominoRound()
        val t = r.hands[r.turn].first()
        assertTrue(r.play(r.turn, t, Side.RIGHT))
        assertEquals(1, r.table.size)
    }

    @Test fun rewardsAreOrdered() {
        assertTrue(Rewards.xp("win") > Rewards.xp("draw"))
        assertTrue(Rewards.coins("win") > Rewards.coins("loss"))
        assertEquals(20, Rewards.loginCoins(1))
        assertEquals(80, Rewards.loginCoins(99))
    }

    @Test fun newPlayerHasNoAchievements() {
        assertTrue(Achievements.all.none { it.check(Profile()) })
    }
}
