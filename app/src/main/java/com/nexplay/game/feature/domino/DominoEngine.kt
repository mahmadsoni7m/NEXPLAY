package com.nexplay.game.feature.domino

import kotlin.random.Random

data class Tile(val a: Int, val b: Int) {
    val pips: Int get() = a + b
    val isDouble: Boolean get() = a == b
}

/** A tile placed on the table; [left] and [right] are the visible values in table order. */
data class Placed(val left: Int, val right: Int)

enum class Side { LEFT, RIGHT }

/** Double-six domino for two players. Index 0 / 1 are the players. */
class DominoRound(val starter: Int = -1) {
    val hands = arrayOf(mutableListOf<Tile>(), mutableListOf<Tile>())
    val boneyard = mutableListOf<Tile>()
    val table = ArrayDeque<Placed>()
    var turn = 0
    var passesInRow = 0
    var finished = false
    var winner = -1          // 0 / 1, or -1 for a tie
    var points = 0           // points awarded to the winner
    var blocked = false
    val passedValues = arrayOf(mutableSetOf<Int>(), mutableSetOf<Int>())

    init {
        val all = ArrayList<Tile>()
        for (i in 0..6) for (j in i..6) all.add(Tile(i, j))
        all.shuffle()
        for (p in 0..1) repeat(7) { hands[p].add(all.removeAt(all.size - 1)) }
        boneyard.addAll(all)
        turn = if (starter in 0..1) starter else firstPlayer()
    }

    private fun firstPlayer(): Int {
        var bestP = 0
        var best = -1
        for (p in 0..1) for (t in hands[p]) {
            val v = if (t.isDouble) 100 + t.a else t.pips
            if (v > best) { best = v; bestP = p }
        }
        return bestP
    }

    val leftEnd: Int get() = table.first().left
    val rightEnd: Int get() = table.last().right

    fun playableSides(t: Tile): List<Side> {
        if (table.isEmpty()) return listOf(Side.RIGHT)
        val res = ArrayList<Side>()
        if (t.a == leftEnd || t.b == leftEnd) res.add(Side.LEFT)
        if (t.a == rightEnd || t.b == rightEnd) res.add(Side.RIGHT)
        return res
    }

    fun playableTiles(p: Int): List<Tile> = hands[p].filter { playableSides(it).isNotEmpty() }

    fun play(p: Int, t: Tile, side: Side): Boolean {
        if (finished || p != turn || t !in hands[p]) return false
        if (side !in playableSides(t)) return false
        if (table.isEmpty()) table.addLast(Placed(t.a, t.b))
        else if (side == Side.LEFT) {
            val target = leftEnd
            table.addFirst(if (t.b == target) Placed(t.a, t.b) else Placed(t.b, t.a))
        } else {
            val target = rightEnd
            table.addLast(if (t.a == target) Placed(t.a, t.b) else Placed(t.b, t.a))
        }
        hands[p].remove(t)
        passesInRow = 0
        if (hands[p].isEmpty()) {
            finished = true
            winner = p
            points = hands[1 - p].sumOf { it.pips }
        } else turn = 1 - p
        return true
    }

    fun canDraw(p: Int): Boolean = !finished && p == turn && playableTiles(p).isEmpty() && boneyard.isNotEmpty()

    fun draw(p: Int): Tile? {
        if (!canDraw(p)) return null
        val t = boneyard.removeAt(boneyard.size - 1)
        hands[p].add(t)
        return t
    }

    fun canPass(p: Int): Boolean = !finished && p == turn && playableTiles(p).isEmpty() && boneyard.isEmpty()

    fun pass(p: Int): Boolean {
        if (!canPass(p)) return false
        if (!table.isEmpty()) {
            passedValues[p].add(leftEnd)
            passedValues[p].add(rightEnd)
        }
        passesInRow++
        if (passesInRow >= 2) {
            finished = true
            blocked = true
            val s0 = hands[0].sumOf { it.pips }
            val s1 = hands[1].sumOf { it.pips }
            when {
                s0 < s1 -> { winner = 0; points = s1 - s0 }
                s1 < s0 -> { winner = 1; points = s0 - s1 }
                else -> { winner = -1; points = 0 }
            }
        } else turn = 1 - p
        return true
    }
}

/** level: 0 Easy, 1 Medium, 2 Hard, 3 Expert. */
class DominoAI(private val level: Int) {
    data class Choice(val tile: Tile, val side: Side)

    fun choose(r: DominoRound, p: Int): Choice? {
        val options = ArrayList<Choice>()
        for (t in r.playableTiles(p)) for (s in r.playableSides(t)) options.add(Choice(t, s))
        if (options.isEmpty()) return null
        if (level == 0) return options.random()
        val opp = 1 - p
        var best = options[0]
        var bestScore = Double.NEGATIVE_INFINITY
        for (o in options) {
            var score = o.tile.pips.toDouble()
            if (o.tile.isDouble) score += 2.0
            if (level >= 2 && !r.table.isEmpty()) {
                val newLeft: Int
                val newRight: Int
                if (o.side == Side.LEFT) {
                    newLeft = if (o.tile.b == r.leftEnd) o.tile.a else o.tile.b
                    newRight = r.rightEnd
                } else {
                    newRight = if (o.tile.a == r.rightEnd) o.tile.b else o.tile.a
                    newLeft = r.leftEnd
                }
                for (e in intArrayOf(newLeft, newRight)) {
                    if (e in r.passedValues[opp]) score += 6.0
                    val mine = r.hands[p].count { it != o.tile && (it.a == e || it.b == e) }
                    score += mine * 2.0
                }
                if (level >= 3) {
                    // prefer keeping flexibility: avoid leaving both ends equal
                    if (newLeft == newRight) score -= 2.0
                }
            }
            score += Random.nextDouble() * (if (level == 1) 4.0 else 0.2)
            if (score > bestScore) { bestScore = score; best = o }
        }
        return best
    }
}
