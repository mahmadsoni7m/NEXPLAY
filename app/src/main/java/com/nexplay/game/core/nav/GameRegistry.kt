package com.nexplay.game.core.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nexplay.game.core.ui.NexColors
import com.nexplay.game.feature.backgammon.BackgammonScreen
import com.nexplay.game.feature.checkers.CheckersScreen
import com.nexplay.game.feature.chess.ChessScreen
import com.nexplay.game.feature.domino.DominoScreen

/**
 * To add a new game: implement a screen taking (mode, level, onExit) and add one entry here.
 * Navigation, home cards, setup screen and leaderboard tabs pick it up automatically.
 */
data class GameInfo(
    val id: String,
    val emoji: String,
    val colors: List<Color>,
    val onlineSupported: Boolean,
    val screen: @Composable (mode: String, level: Int, onExit: () -> Unit) -> Unit
)

object GameRegistry {
    val games: List<GameInfo> = listOf(
        GameInfo("checkers", "⛀", NexColors.CardCheckers, true) { m, l, e -> CheckersScreen(m, l, e) },
        GameInfo("chess", "♞", NexColors.CardChess, true) { m, l, e -> ChessScreen(m, l, e) },
        GameInfo("backgammon", "🎲", NexColors.CardBackgammon, false) { m, l, e -> BackgammonScreen(m, l, e) },
        GameInfo("domino", "🁫", NexColors.CardDomino, false) { m, l, e -> DominoScreen(m, l, e) }
    )

    fun find(id: String): GameInfo? = games.firstOrNull { it.id == id }
}
