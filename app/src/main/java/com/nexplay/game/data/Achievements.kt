package com.nexplay.game.data

data class Achievement(val id: String, val emoji: String, val nameKey: String, val check: (Profile) -> Boolean)

/** Derived from existing stats, so no extra storage is needed. */
object Achievements {
    private fun games(p: Profile) = p.stats.values.sumOf { it.total }
    val all: List<Achievement> = listOf(
        Achievement("first_win", "🥇", "ach_first_win") { it.totalWins >= 1 },
        Achievement("ten_wins", "🔥", "ach_ten_wins") { it.totalWins >= 10 },
        Achievement("fifty_games", "🎮", "ach_fifty_games") { games(it) >= 50 },
        Achievement("all_games", "🌟", "ach_all_games") { p -> listOf("checkers", "chess", "backgammon", "domino").all { (p.stats[it]?.wins ?: 0) >= 1 } },
        Achievement("level5", "⚡", "ach_level5") { it.level >= 5 },
        Achievement("level10", "👑", "ach_level10") { it.level >= 10 },
        Achievement("streak3", "📅", "ach_streak3") { it.streak >= 3 },
        Achievement("rich", "🪙", "ach_rich") { it.coins >= 500 }
    )
}
