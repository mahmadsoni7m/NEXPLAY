package com.nexplay.game.data

/** Single source of truth for rewards (used by Prefs when recording and by the result screen). */
object Rewards {
    fun xp(result: String): Int = when (result) { "win" -> 30; "draw" -> 10; else -> 5 }
    fun coins(result: String): Int = when (result) { "win" -> 20; "draw" -> 8; else -> 3 }
    fun loginCoins(streak: Int): Int = 20 + 10 * minOf(maxOf(streak - 1, 0), 6)
    const val LOGIN_XP = 15
}
