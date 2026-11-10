package com.nexplay.game.feature.backgammon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.Repository
import com.nexplay.game.data.S
import com.nexplay.game.feature.checkers.levelName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgammonVM : ViewModel() {
    var state by mutableStateOf(BgState.initial()); private set
    var selected by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var winner by mutableStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var shownDice by mutableStateOf<List<Int>>(emptyList()); private set

    private var repo: Repository? = null
    private var mode = "ai"
    private var level = 1
    private var started = false
    private var recorded = false
    var sound = true

    fun start(r: Repository, m: String, l: Int) {
        if (started) return
        started = true
        repo = r
        mode = m
        level = l
        newGame()
    }

    fun newGame() {
        state = BgState.initial()
        selected = null
        message = null
        winner = 0
        busy = false
        shownDice = emptyList()
        recorded = false
    }

    private fun humanTurn() = winner == 0 && !busy && (mode == "local" || state.turn == 1)

    fun roll() {
        if (!humanTurn() || state.dice.isNotEmpty()) return
        state = state.roll()
        shownDice = state.dice
        selected = null
        if (!state.canMove()) {
            message = S.t("no_moves")
            busy = true
            viewModelScope.launch {
                delay(1100)
                state = state.settle()
                message = null
                busy = false
                turnChanged()
            }
        } else message = null
    }

    /** [n] is the point in the current player's numbering (25 = bar). */
    fun tapPoint(n: Int) {
        if (!humanTurn()) return
        if (state.dice.isEmpty()) {
            message = S.t("roll")
            return
        }
        val moves = state.legalMoves()
        val sel = selected
        if (sel != null) {
            val m = moves.filter { it.from == sel && it.to == n }.minByOrNull { it.die }
            if (m != null) {
                doMove(m)
                return
            }
        }
        selected = if (moves.any { it.from == n }) n else null
    }

    fun tapOff() {
        if (!humanTurn()) return
        val sel = selected ?: return
        val m = state.legalMoves().filter { it.from == sel && it.to == 0 }.minByOrNull { it.die } ?: return
        doMove(m)
    }

    fun destinations(): Set<Int> {
        val sel = selected ?: return emptySet()
        return state.legalMoves().filter { it.from == sel }.map { it.to }.toSet()
    }

    private fun doMove(m: BgMove) {
        state = state.play(m)
        selected = null
        Sfx.move(sound)
        checkEnd()
        if (winner == 0 && state.dice.isEmpty()) turnChanged()
    }

    private fun checkEnd() {
        val w = state.winner()
        if (w != 0) {
            winner = w
            Sfx.win(sound)
            if (mode == "ai" && !recorded) {
                recorded = true
                repo?.launch { repo?.recordResult("backgammon", "ai", if (w == 1) "win" else "loss", "AI", level) }
            }
        }
    }

    private fun turnChanged() {
        if (winner != 0) return
        if (mode == "ai" && state.turn == -1) runAi()
    }

    private fun runAi() {
        busy = true
        viewModelScope.launch {
            delay(700)
            state = state.roll()
            shownDice = state.dice
            delay(900)
            if (!state.canMove()) {
                message = S.t("no_moves")
                delay(1000)
                message = null
                state = state.settle()
                busy = false
                return@launch
            }
            val snap = state
            val seq = withContext(Dispatchers.Default) { BgAI(level).chooseTurn(snap) }
            for (m in seq) {
                delay(550)
                state = state.play(m)
                Sfx.move(sound)
                checkEnd()
                if (winner != 0) break
            }
            if (winner == 0 && state.turn == -1) state = state.settle()
            busy = false
        }
    }

    fun resign() {
        if (winner != 0) return
        winner = if (mode == "ai") -1 else -state.turn
        if (mode == "ai" && !recorded) {
            recorded = true
            repo?.launch { repo?.recordResult("backgammon", "ai", "loss", "AI", level) }
        }
    }
}

private fun colOfPoint(p: Int): Int = if (p >= 13) (p - 13) + (if (p >= 19) 1 else 0) else (12 - p) + (if (p <= 6) 1 else 0)

private fun pointOfCol(col: Int, top: Boolean): Int =
    if (top) (if (col < 6) 13 + col else 12 + col) else (if (col < 6) 12 - col else 13 - col)

@Composable
fun BackgammonScreen(mode: String, level: Int, onExit: () -> Unit) {
    val repo = rememberRepo()
    val vm: BackgammonVM = viewModel()
    val profile by repo.profile.collectAsState()
    LaunchedEffect(Unit) { vm.start(repo, mode, level) }
    vm.sound = profile.sound
    val st = vm.state
    val dests = vm.destinations()
    val canOff = 0 in dests

    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t("backgammon"), onBack = onExit)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val oppLabel = if (mode == "ai") "AI · " + levelName(level) else S.t("black")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚫ $oppLabel", color = NexColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${S.t("off")}: ${st.off[1]}   ${S.t("bar")}: ${st.bar[1]}", color = NexColors.TextDim, fontSize = 12.sp)
                }
                BackgammonBoard(st, vm.selected, dests, onPoint = { idx ->
                    val n = if (st.turn == 1) idx + 1 else 24 - idx
                    vm.tapPoint(n)
                }, onBar = { vm.tapPoint(25) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚪ ${if (mode == "ai") profile.username else S.t("white")}", color = NexColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${S.t("off")}: ${st.off[0]}   ${S.t("bar")}: ${st.bar[0]}", color = NexColors.TextDim, fontSize = 12.sp)
                }
                DiceRow(vm.shownDice, st.dice)
                val turnName = if (st.turn == 1) S.t("white") else S.t("black")
                val status = when {
                    vm.message != null -> vm.message!!
                    vm.winner != 0 -> S.t("game_over")
                    vm.busy && mode == "ai" -> S.t("thinking")
                    mode == "ai" -> S.t("your_turn")
                    else -> turnName
                }
                Text(status, color = NexColors.Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton(S.t("roll"), { vm.roll() }, Modifier.weight(1.2f),
                        enabled = st.dice.isEmpty() && vm.winner == 0 && !vm.busy && (mode == "local" || st.turn == 1),
                        colors = NexColors.CardBackgammon)
                    if (canOff) GlowButton(S.t("off") + " ⬆", { vm.tapOff() }, Modifier.weight(1f), colors = listOf(NexColors.Green, NexColors.Cyan))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(S.t("new_game"), { vm.newGame() }, Modifier.weight(1f))
                    OutlineButton(S.t("resign"), { vm.resign() }, Modifier.weight(1f), enabled = vm.winner == 0)
                }
            }
        }
        if (vm.winner != 0) {
            val title = when {
                mode == "local" -> S.t("win_of") + ": " + (if (vm.winner == 1) S.t("white") else S.t("black"))
                vm.winner == 1 -> S.t("you_win")
                else -> S.t("you_lose")
            }
            ResultDialog(title, null, onNew = { vm.newGame() }, onExit = onExit)
        }
    }
}

@Composable
private fun DiceRow(shown: List<Int>, remaining: List<Int>) {
    val faces = "⚀⚁⚂⚃⚄⚅"
    val rem = remaining.toMutableList()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (shown.isEmpty()) {
            Text("🎲", fontSize = 40.sp)
        } else {
            for (d in shown) {
                val available = rem.remove(d)
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (available) Color(0xFFF5F5FF) else Color(0x33FFFFFF))
                        .semantics { contentDescription = "Die $d" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(faces[d - 1].toString(), fontSize = 40.sp, color = if (available) Color(0xFF1B1F3B) else Color(0x66FFFFFF), modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun BackgammonBoard(st: BgState, selected: Int?, dests: Set<Int>, onPoint: (Int) -> Unit, onBar: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = wPx * 1.08f
        val cw = wPx / 13f
        val th = hPx * 0.42f
        val hDp = with(density) { hPx.toDp() }
        val turn = st.turn
        val pi = if (turn == 1) 0 else 1
        // highlighted absolute indices
        val destIdx = dests.filter { it >= 1 }.map { st.idx(turn, it) }.toSet()
        val selIdx = if (selected != null && selected in 1..24) st.idx(turn, selected) else -1
        val barSelected = selected == 25

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(hDp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141A3A))
                .pointerInput(st.turn, st.dice, st.points.toList(), st.bar.toList()) {
                    detectTapGestures { o ->
                        val col = (o.x / cw).toInt().coerceIn(0, 12)
                        if (col == 6) {
                            onBar()
                        } else {
                            val top = o.y < hPx / 2f
                            onPoint(pointOfCol(col, top) - 1)
                        }
                    }
                }
                .semantics { contentDescription = "Backgammon board" }
        ) {
            val barPaint = Color(0xFF0B0F22)
            drawRect(barPaint, Offset(6 * cw, 0f), androidx.compose.ui.geometry.Size(cw, hPx))
            for (p in 1..24) {
                val top = p >= 13
                val col = colOfPoint(p)
                val x0 = col * cw
                val idx = p - 1
                val base = if (top) 0f else hPx
                val tip = if (top) th else hPx - th
                val path = Path().apply {
                    moveTo(x0 + 0.04f * cw, base)
                    lineTo(x0 + 0.96f * cw, base)
                    lineTo(x0 + cw / 2f, tip)
                    close()
                }
                val dark = (p % 2 == 0)
                val color = when {
                    idx == selIdx -> Color(0xFFFFD54F)
                    idx in destIdx -> Color(0xFF00E5FF)
                    dark -> Color(0xFF5C3BD1)
                    else -> Color(0xFF2B3A8C)
                }
                drawPath(path, color)
                val v = st.points[idx]
                val cnt = kotlin.math.abs(v)
                if (cnt > 0) {
                    val d = cw * 0.86f
                    val shown = minOf(cnt, 5)
                    for (k in 0 until shown) {
                        val cy = if (top) d / 2f + k * d * 0.98f + 2f else hPx - d / 2f - k * d * 0.98f - 2f
                        drawChecker(Offset(x0 + cw / 2f, cy), d / 2f, v > 0)
                    }
                    if (cnt > 5) {
                        val cy = if (top) d / 2f + 4 * d * 0.98f + 2f else hPx - d / 2f - 4 * d * 0.98f - 2f
                        drawLabel(cnt.toString(), x0 + cw / 2f, cy + d * 0.2f, d * 0.55f, if (v > 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
            // bar checkers (white at bottom, black at top)
            val d = cw * 0.86f
            if (barSelected) drawRect(Color(0x55FFD54F), Offset(6 * cw, 0f), androidx.compose.ui.geometry.Size(cw, hPx))
            for (k in 0 until minOf(st.bar[0], 4)) drawChecker(Offset(6.5f * cw, hPx / 2f + d * 0.6f + k * d * 0.98f), d / 2f, true)
            for (k in 0 until minOf(st.bar[1], 4)) drawChecker(Offset(6.5f * cw, hPx / 2f - d * 0.6f - k * d * 0.98f), d / 2f, false)
            @Suppress("UNUSED_VARIABLE") val unused = pi
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(center: Offset, r: Float, white: Boolean) {
    drawCircle(Color(0x66000000), r, center + Offset(2f, 3f))
    drawCircle(if (white) Color(0xFFF3F5FF) else Color(0xFF1A0D2E), r, center)
    drawCircle(if (white) Color(0xFFB8C4E8) else Color(0xFFFF4081), r * 0.72f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(text: String, x: Float, y: Float, size: Float, color: Int) {
    drawIntoCanvas { c ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size
            this.color = color
            isFakeBoldText = true
        }
        c.nativeCanvas.drawText(text, x, y, paint)
    }
}
