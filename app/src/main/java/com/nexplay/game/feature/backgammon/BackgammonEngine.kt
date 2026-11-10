package com.nexplay.game.feature.backgammon

import kotlin.math.abs
import kotlin.random.Random

/**
 * Backgammon engine.
 * points[i] > 0 = white checkers, < 0 = black checkers (i in 0..23).
 * Each player moves toward "point number 1" in their own numbering:
 *  white: point n = index n-1, black: point n = index 24-n. Bar = point 25, borne off = point 0.
 */
data class BgMove(val from: Int, val to: Int, val die: Int)

class BgState(
    val points: IntArray,
    val bar: IntArray,      // [white, black]
    val off: IntArray,      // [white, black]
    val turn: Int,          // 1 = white, -1 = black
    val dice: List<Int>     // remaining dice to play; empty = must roll
) {
    private fun pi(p: Int) = if (p == 1) 0 else 1
    fun idx(p: Int, n: Int) = if (p == 1) n - 1 else 24 - n
    fun own(p: Int, n: Int) = maxOf(0, p * points[idx(p, n)])
    private fun opp(p: Int, n: Int) = maxOf(0, -p * points[idx(p, n)])

    fun pip(p: Int): Int {
        var s = bar[pi(p)] * 25
        for (n in 1..24) s += own(p, n) * n
        return s
    }

    private fun allHome(p: Int): Boolean {
        if (bar[pi(p)] > 0) return false
        for (n in 7..24) if (own(p, n) > 0) return false
        return true
    }

    private fun singleMoves(p: Int, diceSet: List<Int>): List<BgMove> {
        val res = ArrayList<BgMove>()
        val ds = diceSet.distinct()
        if (bar[pi(p)] > 0) {
            for (d in ds) {
                val to = 25 - d
                if (opp(p, to) <= 1) res.add(BgMove(25, to, d))
            }
            return res
        }
        val home = allHome(p)
        for (n in 24 downTo 1) {
            if (own(p, n) == 0) continue
            for (d in ds) {
                val to = n - d
                if (to >= 1) {
                    if (opp(p, to) <= 1) res.add(BgMove(n, to, d))
                } else if (home) {
                    if (to == 0) res.add(BgMove(n, 0, d))
                    else {
                        var higher = false
                        for (h in n + 1..6) if (own(p, h) > 0) higher = true
                        if (!higher) res.add(BgMove(n, 0, d))
                    }
                }
            }
        }
        return res
    }

    /** Applies one checker move for the player whose turn it is (no turn switching). */
    private fun applyRaw(m: BgMove): BgState {
        val p = turn
        val pts = points.copyOf()
        val b = bar.copyOf()
        val o = off.copyOf()
        if (m.from == 25) b[pi(p)]-- else pts[idx(p, m.from)] -= p
        if (m.to == 0) o[pi(p)]++
        else {
            val ti = idx(p, m.to)
            if (pts[ti] * p == -1) {
                pts[ti] = 0
                b[pi(-p)]++
            }
            pts[ti] += p
        }
        val rest = dice.toMutableList()
        rest.remove(m.die)
        return BgState(pts, b, o, p, rest)
    }

    /** All maximal move sequences obeying the "use as many dice as possible / larger die" rules. */
    fun sequences(): List<Pair<List<BgMove>, BgState>> {
        val all = ArrayList<Pair<List<BgMove>, BgState>>()
        fun rec(s: BgState, seq: List<BgMove>) {
            val moves = s.singleMoves(turn, s.dice)
            if (moves.isEmpty() || s.dice.isEmpty()) {
                all.add(Pair(seq, s))
                return
            }
            for (m in moves) rec(s.applyRaw(m), seq + m)
        }
        if (dice.isEmpty()) return emptyList()
        rec(this, emptyList())
        val maxLen = all.maxOfOrNull { it.first.size } ?: 0
        if (maxLen == 0) return emptyList()
        var best = all.filter { it.first.size == maxLen }
        if (maxLen == 1 && dice.size == 2 && dice[0] != dice[1]) {
            val big = maxOf(dice[0], dice[1])
            val withBig = best.filter { it.first[0].die == big }
            if (withBig.isNotEmpty()) best = withBig
        }
        return best
    }

    /** Legal first moves for the UI. */
    fun legalMoves(): List<BgMove> = sequences().map { it.first[0] }.distinct()

    fun canMove(): Boolean = dice.isNotEmpty() && legalMoves().isNotEmpty()

    fun roll(rng: Random = Random): BgState {
        val a = rng.nextInt(1, 7)
        val b = rng.nextInt(1, 7)
        return BgState(points, bar, off, turn, if (a == b) listOf(a, a, a, a) else listOf(a, b))
    }

    /** Plays [m]; automatically passes the turn when no dice/moves remain. */
    fun play(m: BgMove): BgState {
        val n = applyRaw(m)
        return if (n.off[pi(turn)] >= 15) n else n.settle()
    }

    /** Pass the turn if nothing more can be played. */
    fun settle(): BgState =
        if (dice.isEmpty() || !canMove()) BgState(points, bar, off, -turn, emptyList()) else this

    fun winner(): Int = when {
        off[0] >= 15 -> 1
        off[1] >= 15 -> -1
        else -> 0
    }

    fun blots(p: Int): Int = (1..24).count { own(p, it) == 1 }
    fun madePoints(p: Int): Int = (1..24).count { own(p, it) >= 2 }

    companion object {
        fun initial(): BgState {
            val pts = IntArray(24)
            pts[23] = 2; pts[12] = 5; pts[7] = 3; pts[5] = 5       // white
            pts[0] = -2; pts[11] = -5; pts[16] = -3; pts[18] = -5   // black
            return BgState(pts, IntArray(2), IntArray(2), 1, emptyList())
        }
    }
}

/** level: 0 Easy (random), 1 Medium, 2 Hard, 3 Expert. Picks a full-turn sequence. */
class BgAI(private val level: Int) {
    fun chooseTurn(s: BgState): List<BgMove> {
        val seqs = s.sequences()
        if (seqs.isEmpty()) return emptyList()
        if (level == 0) return seqs.random().first
        val me = s.turn
        var best = seqs[0]
        var bestScore = Double.NEGATIVE_INFINITY
        for (sq in seqs) {
            val e = evaluate(sq.second, me) + if (level >= 2) Random.nextDouble() * 0.01 else Random.nextDouble() * 3.0
            if (e > bestScore) {
                bestScore = e
                best = sq
            }
        }
        return best.first
    }

    private fun evaluate(s: BgState, me: Int): Double {
        val pi = if (me == 1) 0 else 1
        val oi = 1 - pi
        var score = (s.pip(-me) - s.pip(me)).toDouble()
        score += s.off[pi] * 4.0 - s.off[oi] * 4.0
        score += s.bar[oi] * 6.0 - s.bar[pi] * 8.0
        score -= s.blots(me) * (if (level >= 3) 5.0 else 3.0)
        score += s.madePoints(me) * 2.5
        // reward holding home-board points when opponent is on the bar
        if (level >= 2) for (n in 1..6) if (s.own(me, n) >= 2) score += 1.5
        return score
    }
}
