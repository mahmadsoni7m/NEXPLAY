package com.nexplay.game.feature.checkers

import kotlin.random.Random

/** Russian draughts (шашки) engine: 8x8, flying kings, mandatory captures, men capture backwards. */
object Ck {
    const val EMPTY = 0
    const val W = 1
    const val B = 2
    const val WK = 3
    const val BK = 4
    val DIRS = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
    fun owner(p: Int) = when (p) { W, WK -> W; B, BK -> B; else -> 0 }
    fun isKing(p: Int) = p == WK || p == BK
    fun inB(r: Int, c: Int) = r in 0..7 && c in 0..7
}

data class CkMove(val path: List<Int>, val captured: List<Int>) {
    val from: Int get() = path.first()
    val to: Int get() = path.last()
}

class CkState(val cells: IntArray, val turn: Int, val quiet: Int = 0) {

    fun legalMoves(): List<CkMove> {
        val caps = ArrayList<CkMove>()
        for (i in 0..63) {
            val p = cells[i]
            if (Ck.owner(p) == turn) {
                dfs(i, i, Ck.isKing(p), arrayListOf(i), arrayListOf(), caps)
            }
        }
        if (caps.isNotEmpty()) return caps
        val out = ArrayList<CkMove>()
        for (i in 0..63) {
            val p = cells[i]
            if (Ck.owner(p) != turn) continue
            val r = i / 8
            val c = i % 8
            if (Ck.isKing(p)) {
                for (d in Ck.DIRS) {
                    var rr = r + d[0]
                    var cc = c + d[1]
                    while (Ck.inB(rr, cc) && cells[rr * 8 + cc] == Ck.EMPTY) {
                        out.add(CkMove(listOf(i, rr * 8 + cc), emptyList()))
                        rr += d[0]
                        cc += d[1]
                    }
                }
            } else {
                val dr = if (turn == Ck.W) -1 else 1
                for (dc in intArrayOf(-1, 1)) {
                    val rr = r + dr
                    val cc = c + dc
                    if (Ck.inB(rr, cc) && cells[rr * 8 + cc] == Ck.EMPTY) {
                        out.add(CkMove(listOf(i, rr * 8 + cc), emptyList()))
                    }
                }
            }
        }
        return out
    }

    private fun isEnemy(p: Int) = p != Ck.EMPTY && Ck.owner(p) != turn

    private fun freeSq(sq: Int, origin: Int) = cells[sq] == Ck.EMPTY || sq == origin

    /** All (capturedSquare, landingSquare) jump options from [pos]. */
    private fun jumps(origin: Int, pos: Int, king: Boolean, cap: List<Int>): List<IntArray> {
        val res = ArrayList<IntArray>()
        val r0 = pos / 8
        val c0 = pos % 8
        for (d in Ck.DIRS) {
            var r = r0 + d[0]
            var c = c0 + d[1]
            if (!king) {
                if (!Ck.inB(r, c)) continue
                val sq = r * 8 + c
                if (isEnemy(cells[sq]) && sq !in cap) {
                    val r2 = r + d[0]
                    val c2 = c + d[1]
                    if (Ck.inB(r2, c2) && freeSq(r2 * 8 + c2, origin)) res.add(intArrayOf(sq, r2 * 8 + c2))
                }
            } else {
                while (Ck.inB(r, c) && freeSq(r * 8 + c, origin)) {
                    r += d[0]
                    c += d[1]
                }
                if (!Ck.inB(r, c)) continue
                val sq = r * 8 + c
                if (isEnemy(cells[sq]) && sq !in cap) {
                    var r2 = r + d[0]
                    var c2 = c + d[1]
                    while (Ck.inB(r2, c2) && freeSq(r2 * 8 + c2, origin)) {
                        res.add(intArrayOf(sq, r2 * 8 + c2))
                        r2 += d[0]
                        c2 += d[1]
                    }
                }
            }
        }
        return res
    }

    private fun promotesAt(sq: Int) = (turn == Ck.W && sq / 8 == 0) || (turn == Ck.B && sq / 8 == 7)

    private fun dfs(origin: Int, pos: Int, king: Boolean, path: ArrayList<Int>, cap: ArrayList<Int>, out: MutableList<CkMove>) {
        val js = jumps(origin, pos, king, cap)
        if (js.isEmpty()) {
            if (cap.isNotEmpty()) out.add(CkMove(path.toList(), cap.toList()))
            return
        }
        val capturedSquares = js.map { it[0] }.distinct()
        for (sq in capturedSquares) {
            val lands = js.filter { it[0] == sq }.map { it[1] }
            val newCap = ArrayList(cap).also { it.add(sq) }
            val use = if (king) {
                val withCont = lands.filter { l -> jumps(origin, l, true, newCap).isNotEmpty() }
                if (withCont.isNotEmpty()) withCont else lands
            } else lands
            for (l in use) {
                path.add(l)
                dfs(origin, l, king || promotesAt(l), path, newCap, out)
                path.removeAt(path.size - 1)
            }
        }
    }

    fun apply(m: CkMove): CkState {
        val n = cells.copyOf()
        var piece = n[m.from]
        n[m.from] = Ck.EMPTY
        for (c in m.captured) n[c] = Ck.EMPTY
        val wasMan = !Ck.isKing(piece)
        if (wasMan && m.path.any { promotesAt(it) }) piece = if (turn == Ck.W) Ck.WK else Ck.BK
        n[m.to] = piece
        val q = if (m.captured.isNotEmpty() || wasMan) 0 else quiet + 1
        return CkState(n, if (turn == Ck.W) Ck.B else Ck.W, q)
    }

    /** null = ongoing, otherwise Ck.W / Ck.B winner, or 0 for a draw. */
    fun result(): Int? {
        if (legalMoves().isEmpty()) return if (turn == Ck.W) Ck.B else Ck.W
        if (quiet >= 30) return 0
        return null
    }

    fun count(color: Int) = cells.count { Ck.owner(it) == color }

    fun toWire(): String {
        val sb = StringBuilder()
        for (p in cells) sb.append(when (p) { Ck.W -> 'w'; Ck.B -> 'b'; Ck.WK -> 'W'; Ck.BK -> 'B'; else -> '.' })
        sb.append(if (turn == Ck.W) 'w' else 'b')
        return sb.toString()
    }

    companion object {
        fun initial(): CkState {
            val c = IntArray(64)
            for (r in 0..7) for (col in 0..7) {
                if ((r + col) % 2 == 1) {
                    if (r <= 2) c[r * 8 + col] = Ck.B
                    if (r >= 5) c[r * 8 + col] = Ck.W
                }
            }
            return CkState(c, Ck.W)
        }

        fun fromWire(s: String): CkState {
            val c = IntArray(64)
            for (i in 0..63) c[i] = when (s[i]) { 'w' -> Ck.W; 'b' -> Ck.B; 'W' -> Ck.WK; 'B' -> Ck.BK; else -> Ck.EMPTY }
            return CkState(c, if (s[64] == 'w') Ck.W else Ck.B)
        }
    }
}

/** Minimax with alpha-beta pruning and iterative deepening. level: 0 Easy, 1 Medium, 2 Hard, 3 Expert (SONI). */
class CkAI(private val level: Int) {
    private var deadline = 0L
    private var timeUp = false

    fun choose(s: CkState): CkMove? {
        val moves = s.legalMoves()
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves[0]
        if (level == 0 && Random.nextInt(100) < 35) return moves.random()
        val maxDepth = when (level) { 0 -> 2; 1 -> 4; 2 -> 6; else -> 10 }
        val ms = when (level) { 0 -> 150L; 1 -> 600L; 2 -> 1500L; else -> 3500L }
        deadline = System.currentTimeMillis() + ms
        var best = moves[0]
        var ordered = moves
        for (d in 1..maxDepth) {
            timeUp = false
            var alpha = -INF
            var bestLocal: CkMove? = null
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

    private fun search(s: CkState, depth: Int, alphaIn: Int, beta: Int): Int {
        if (System.currentTimeMillis() > deadline) {
            timeUp = true
            return 0
        }
        val moves = s.legalMoves()
        if (moves.isEmpty()) return -WIN - depth
        if (s.quiet >= 30) return 0
        val hasCap = moves[0].captured.isNotEmpty()
        if (depth <= 0 && (!hasCap || depth < -6)) return eval(s)
        var alpha = alphaIn
        var best = -INF
        for (m in moves) {
            val v = -search(s.apply(m), depth - 1, -beta, -alpha)
            if (timeUp) return 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    private fun eval(s: CkState): Int {
        var score = 0
        for (i in 0..63) {
            val p = s.cells[i]
            if (p == Ck.EMPTY) continue
            val r = i / 8
            val c = i % 8
            val center = if (c in 2..5 && r in 2..5) 3 else 0
            val v = when (p) {
                Ck.W -> 100 + (7 - r) * 5 + center + (if (r == 7) 6 else 0)
                Ck.B -> -(100 + r * 5 + center + (if (r == 0) 6 else 0))
                Ck.WK -> 320 + center
                else -> -(320 + center)
            }
            score += v
        }
        return if (s.turn == Ck.W) score else -score
    }

    companion object {
        private const val INF = 1_000_000
        private const val WIN = 100_000
    }
}
