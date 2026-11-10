package com.nexplay.game.feature.chess

import kotlin.math.abs
import kotlin.random.Random

/**
 * Compact chess rules engine.
 * Board index = row * 8 + col, row 0 = rank 8 (FEN order). White pieces positive, black negative.
 * 1 = pawn, 2 = knight, 3 = bishop, 4 = rook, 5 = queen, 6 = king.
 */
data class ChessMove(val from: Int, val to: Int, val promo: Int = 0) {
    fun uci(): String = sqName(from) + sqName(to) + (if (promo != 0) "nbrq"[promo - 2].toString() else "")

    companion object {
        fun sqName(i: Int): String = "${'a' + (i % 8)}${8 - i / 8}"
        fun sqIndex(s: String): Int = (s[0] - 'a') + (8 - (s[1] - '0')) * 8
    }
}

class ChessState(
    val board: IntArray,
    val whiteToMove: Boolean,
    val castling: Int,      // bits: 1 = white king side, 2 = white queen side, 4 = black king side, 8 = black queen side
    val ep: Int,            // en passant target square or -1
    val halfmove: Int,
    val fullmove: Int
) {
    private val side get() = if (whiteToMove) 1 else -1

    fun kingSquare(white: Boolean): Int {
        val k = if (white) 6 else -6
        for (i in 0..63) if (board[i] == k) return i
        return -1
    }

    fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val s = if (byWhite) 1 else -1
        val r = sq / 8
        val c = sq % 8
        // pawns
        val pr = r + (if (byWhite) 1 else -1)
        for (dc in intArrayOf(-1, 1)) {
            val pc = c + dc
            if (pr in 0..7 && pc in 0..7 && board[pr * 8 + pc] == s * 1) return true
        }
        for (d in KNIGHT) {
            val rr = r + d[0]
            val cc = c + d[1]
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == s * 2) return true
        }
        for (d in KING) {
            val rr = r + d[0]
            val cc = c + d[1]
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == s * 6) return true
        }
        for (d in DIAG) {
            var rr = r + d[0]
            var cc = c + d[1]
            while (rr in 0..7 && cc in 0..7) {
                val p = board[rr * 8 + cc]
                if (p != 0) {
                    if (p == s * 3 || p == s * 5) return true
                    break
                }
                rr += d[0]
                cc += d[1]
            }
        }
        for (d in ORTH) {
            var rr = r + d[0]
            var cc = c + d[1]
            while (rr in 0..7 && cc in 0..7) {
                val p = board[rr * 8 + cc]
                if (p != 0) {
                    if (p == s * 4 || p == s * 5) return true
                    break
                }
                rr += d[0]
                cc += d[1]
            }
        }
        return false
    }

    fun inCheck(white: Boolean = whiteToMove): Boolean {
        val k = kingSquare(white)
        return k >= 0 && isAttacked(k, !white)
    }

    private fun pseudoMoves(): List<ChessMove> {
        val out = ArrayList<ChessMove>(48)
        for (i in 0..63) {
            val p = board[i]
            if (p == 0 || (p > 0) != whiteToMove) continue
            val r = i / 8
            val c = i % 8
            when (abs(p)) {
                1 -> {
                    val dir = -side
                    val startRow = if (whiteToMove) 6 else 1
                    val lastRow = if (whiteToMove) 0 else 7
                    val r1 = r + dir
                    if (r1 in 0..7 && board[r1 * 8 + c] == 0) {
                        addPawn(out, i, r1 * 8 + c, r1 == lastRow)
                        val r2 = r + 2 * dir
                        if (r == startRow && board[r2 * 8 + c] == 0) out.add(ChessMove(i, r2 * 8 + c))
                    }
                    for (dc in intArrayOf(-1, 1)) {
                        val cc = c + dc
                        if (r1 !in 0..7 || cc !in 0..7) continue
                        val t = r1 * 8 + cc
                        val tp = board[t]
                        if (tp != 0 && (tp > 0) != whiteToMove) addPawn(out, i, t, r1 == lastRow)
                        else if (t == ep && tp == 0) out.add(ChessMove(i, t))
                    }
                }
                2 -> for (d in KNIGHT) step(out, i, r + d[0], c + d[1])
                6 -> {
                    for (d in KING) step(out, i, r + d[0], c + d[1])
                    castle(out, i)
                }
                else -> {
                    val dirs = when (abs(p)) { 3 -> DIAG; 4 -> ORTH; else -> DIAG + ORTH }
                    for (d in dirs) {
                        var rr = r + d[0]
                        var cc = c + d[1]
                        while (rr in 0..7 && cc in 0..7) {
                            val t = board[rr * 8 + cc]
                            if (t == 0) out.add(ChessMove(i, rr * 8 + cc))
                            else {
                                if ((t > 0) != whiteToMove) out.add(ChessMove(i, rr * 8 + cc))
                                break
                            }
                            rr += d[0]
                            cc += d[1]
                        }
                    }
                }
            }
        }
        return out
    }

    private fun addPawn(out: MutableList<ChessMove>, from: Int, to: Int, promo: Boolean) {
        if (promo) for (pc in 5 downTo 2) out.add(ChessMove(from, to, pc)) else out.add(ChessMove(from, to))
    }

    private fun step(out: MutableList<ChessMove>, from: Int, r: Int, c: Int) {
        if (r !in 0..7 || c !in 0..7) return
        val t = board[r * 8 + c]
        if (t == 0 || (t > 0) != whiteToMove) out.add(ChessMove(from, r * 8 + c))
    }

    private fun castle(out: MutableList<ChessMove>, from: Int) {
        val homeRow = if (whiteToMove) 7 else 0
        if (from != homeRow * 8 + 4) return
        val ks = if (whiteToMove) 1 else 4
        val qs = if (whiteToMove) 2 else 8
        val rook = if (whiteToMove) 4 else -4
        if (inCheck(whiteToMove)) return
        val base = homeRow * 8
        if (castling and ks != 0 && board[base + 7] == rook && board[base + 5] == 0 && board[base + 6] == 0 &&
            !isAttacked(base + 5, !whiteToMove) && !isAttacked(base + 6, !whiteToMove)
        ) out.add(ChessMove(from, base + 6))
        if (castling and qs != 0 && board[base] == rook && board[base + 1] == 0 && board[base + 2] == 0 && board[base + 3] == 0 &&
            !isAttacked(base + 3, !whiteToMove) && !isAttacked(base + 2, !whiteToMove)
        ) out.add(ChessMove(from, base + 2))
    }

    fun legalMoves(): List<ChessMove> {
        val res = ArrayList<ChessMove>()
        for (m in pseudoMoves()) {
            val n = apply(m)
            if (!n.inCheck(whiteToMove)) res.add(m)
        }
        return res
    }

    fun apply(m: ChessMove): ChessState {
        val b = board.copyOf()
        val p = b[m.from]
        val captured = b[m.to]
        var newEp = -1
        b[m.from] = 0
        b[m.to] = if (m.promo != 0) side * m.promo else p
        if (abs(p) == 1) {
            if (m.to == ep && captured == 0 && m.from % 8 != m.to % 8) {
                b[m.to + (if (whiteToMove) 8 else -8)] = 0
            }
            if (abs(m.to - m.from) == 16) newEp = (m.from + m.to) / 2
        }
        if (abs(p) == 6 && abs(m.to - m.from) == 2) {
            val base = (m.from / 8) * 8
            if (m.to % 8 == 6) { b[base + 5] = b[base + 7]; b[base + 7] = 0 }
            else { b[base + 3] = b[base]; b[base] = 0 }
        }
        var cr = castling
        if (p == 6) cr = cr and 3.inv()
        if (p == -6) cr = cr and 12.inv()
        if (m.from == 63 || m.to == 63) cr = cr and 1.inv()
        if (m.from == 56 || m.to == 56) cr = cr and 2.inv()
        if (m.from == 7 || m.to == 7) cr = cr and 4.inv()
        if (m.from == 0 || m.to == 0) cr = cr and 8.inv()
        val hm = if (abs(p) == 1 || captured != 0) 0 else halfmove + 1
        return ChessState(b, !whiteToMove, cr, newEp, hm, if (whiteToMove) fullmove else fullmove + 1)
    }

    fun insufficientMaterial(): Boolean {
        var minors = 0
        for (p in board) {
            when (abs(p)) {
                1, 4, 5 -> return false
                2, 3 -> minors++
            }
        }
        return minors <= 1
    }

    /** Returns null while ongoing, otherwise "checkmate:<winner w/b>", "stalemate", "insufficient", "fifty". */
    fun status(): String? {
        if (legalMoves().isEmpty()) return if (inCheck()) "checkmate:" + (if (whiteToMove) "b" else "w") else "stalemate"
        if (insufficientMaterial()) return "insufficient"
        if (halfmove >= 100) return "fifty"
        return null
    }

    fun positionKey(): String = toFen().split(" ").take(4).joinToString(" ")

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val p = board[r * 8 + c]
                if (p == 0) empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val ch = "pnbrqk"[abs(p) - 1]
                    sb.append(if (p > 0) ch.uppercaseChar() else ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        var cs = ""
        if (castling and 1 != 0) cs += "K"
        if (castling and 2 != 0) cs += "Q"
        if (castling and 4 != 0) cs += "k"
        if (castling and 8 != 0) cs += "q"
        sb.append(if (cs.isEmpty()) "-" else cs)
        sb.append(' ')
        sb.append(if (ep >= 0) ChessMove.sqName(ep) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    companion object {
        val KNIGHT = arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
            intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1))
        val KING = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1),
            intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))
        val DIAG = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        val ORTH = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): ChessState = fromFen(START_FEN)

        fun fromFen(fen: String): ChessState {
            val parts = fen.trim().split(" ")
            val b = IntArray(64)
            var idx = 0
            for (ch in parts[0]) {
                when {
                    ch == '/' -> {}
                    ch.isDigit() -> idx += ch - '0'
                    else -> {
                        val t = "pnbrqk".indexOf(ch.lowercaseChar()) + 1
                        b[idx++] = if (ch.isUpperCase()) t else -t
                    }
                }
            }
            val white = parts.getOrElse(1) { "w" } == "w"
            val cs = parts.getOrElse(2) { "-" }
            var cr = 0
            if ('K' in cs) cr = cr or 1
            if ('Q' in cs) cr = cr or 2
            if ('k' in cs) cr = cr or 4
            if ('q' in cs) cr = cr or 8
            val epS = parts.getOrElse(3) { "-" }
            val ep = if (epS == "-") -1 else ChessMove.sqIndex(epS)
            return ChessState(b, white, cr, ep, parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0, parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1)
        }
    }
}

/** Alpha-beta chess AI. level: 0 Easy, 1 Medium, 2 Hard, 3 Expert. */
class ChessAI(private val level: Int) {
    private var deadline = 0L
    private var timeUp = false

    fun choose(s: ChessState): ChessMove? {
        val moves = s.legalMoves()
        if (moves.isEmpty()) return null
        if (level == 0 && Random.nextInt(100) < 40) return moves.random()
        val maxDepth = when (level) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 5 }
        val ms = when (level) { 0 -> 300L; 1 -> 800L; 2 -> 2000L; else -> 4000L }
        deadline = System.currentTimeMillis() + ms
        var ordered = order(s, moves)
        var best = ordered[0]
        for (d in 1..maxDepth) {
            timeUp = false
            var alpha = -INF
            var bestLocal: ChessMove? = null
            for (m in ordered) {
                val v = -search(s.apply(m), d - 1, -INF, -alpha)
                if (timeUp) break
                if (v > alpha) {
                    alpha = v
                    bestLocal = m
                }
            }
            if (timeUp) break
            if (bestLocal != null) {
                best = bestLocal
                ordered = listOf(bestLocal) + ordered.filter { it !== bestLocal }
            }
        }
        return best
    }

    private fun order(s: ChessState, moves: List<ChessMove>): List<ChessMove> =
        moves.sortedByDescending { m ->
            val victim = abs(s.board[m.to])
            val attacker = abs(s.board[m.from])
            (if (victim != 0) 10 * VAL[victim] - VAL[attacker] else 0) + (if (m.promo != 0) 800 else 0)
        }

    private fun search(s: ChessState, depth: Int, alphaIn: Int, beta: Int): Int {
        if (System.currentTimeMillis() > deadline) {
            timeUp = true
            return 0
        }
        val moves = s.legalMoves()
        if (moves.isEmpty()) return if (s.inCheck()) -MATE - depth else 0
        if (s.halfmove >= 100 || s.insufficientMaterial()) return 0
        if (depth <= 0) return eval(s)
        var alpha = alphaIn
        var best = -INF
        for (m in order(s, moves)) {
            val v = -search(s.apply(m), depth - 1, -beta, -alpha)
            if (timeUp) return 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    private fun eval(s: ChessState): Int {
        var score = 0
        for (i in 0..63) {
            val p = s.board[i]
            if (p == 0) continue
            val t = abs(p)
            val r = i / 8
            val c = i % 8
            val centerDist = abs(3.5 - r) + abs(3.5 - c)
            var v = VAL[t]
            when (t) {
                1 -> v += if (p > 0) (6 - r) * 6 else (r - 1) * 6
                2, 3 -> v += (7 - centerDist * 2).toInt() * 3
                6 -> v += 0
                else -> v += (4 - centerDist).toInt()
            }
            score += if (p > 0) v else -v
        }
        return if (s.whiteToMove) score else -score
    }

    companion object {
        private const val INF = 10_000_000
        private const val MATE = 1_000_000
        private val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 20000)
    }
}
