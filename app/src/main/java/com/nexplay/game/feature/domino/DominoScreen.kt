package com.nexplay.game.feature.domino

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.Repository
import com.nexplay.game.data.S
import com.nexplay.game.feature.checkers.levelName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DominoVM : ViewModel() {
    var round by mutableStateOf(DominoRound()); private set
    var tick by mutableIntStateOf(0); private set
    var scores by mutableStateOf(intArrayOf(0, 0)); private set
    var selected by mutableStateOf<Tile?>(null); private set
    var matchWinner by mutableIntStateOf(-1); private set
    var cover by mutableStateOf(false); private set     // hot-seat privacy cover
    var busy by mutableStateOf(false); private set
    var sound = true

    private var repo: Repository? = null
    private var mode = "ai"
    private var level = 1
    private var started = false
    private var recorded = false

    fun start(r: Repository, m: String, l: Int) {
        if (started) return
        started = true
        repo = r
        mode = m
        level = l
        newMatch()
    }

    fun newMatch() {
        scores = intArrayOf(0, 0)
        matchWinner = -1
        recorded = false
        newRound(-1)
    }

    fun nextRound() {
        val w = round.winner
        newRound(if (w in 0..1) w else -1)
    }

    private fun newRound(starter: Int) {
        round = DominoRound(starter)
        selected = null
        busy = false
        cover = mode == "local"
        tick++
        if (mode == "ai" && round.turn == 1) runAi()
    }

    /** Index of the player whose hand is shown at the bottom. */
    val viewer: Int get() = if (mode == "local") round.turn else 0

    fun readyFromCover() { cover = false }

    fun select(t: Tile) {
        val r = round
        if (r.finished || busy || cover) return
        if (mode == "ai" && r.turn != 0) return
        val sides = r.playableSides(t)
        if (sides.isEmpty()) return
        if (sides.size == 1) play(t, sides[0]) else selected = t
    }

    fun play(t: Tile, side: Side) {
        val r = round
        if (!r.play(r.turn, t, side)) return
        selected = null
        Sfx.move(sound)
        after()
    }

    fun draw() {
        val r = round
        if (busy || cover) return
        if (mode == "ai" && r.turn != 0) return
        r.draw(r.turn)
        tick++
    }

    fun pass() {
        val r = round
        if (busy || cover) return
        if (mode == "ai" && r.turn != 0) return
        if (r.pass(r.turn)) after()
    }

    private fun after() {
        tick++
        val r = round
        if (r.finished) {
            if (r.winner in 0..1) {
                val s = scores.copyOf()
                s[r.winner] += r.points
                scores = s
                if (s[r.winner] >= 100) {
                    matchWinner = r.winner
                    Sfx.win(sound)
                    if (mode == "ai" && !recorded) {
                        recorded = true
                        repo?.launch { repo?.recordResult("domino", "ai", if (r.winner == 0) "win" else "loss", "AI", level) }
                    }
                }
            }
            return
        }
        if (mode == "local") cover = true
        else if (mode == "ai" && r.turn == 1) runAi()
    }

    private fun runAi() {
        busy = true
        viewModelScope.launch {
            val ai = DominoAI(level)
            while (!round.finished && round.turn == 1) {
                delay(900)
                val r = round
                val c = ai.choose(r, 1)
                if (c != null) {
                    r.play(1, c.tile, c.side)
                    Sfx.move(sound)
                    tick++
                } else if (r.canDraw(1)) {
                    r.draw(1)
                    tick++
                } else {
                    r.pass(1)
                    tick++
                }
            }
            busy = false
            after()
        }
    }

    fun resign() {
        if (matchWinner != -1) return
        matchWinner = if (mode == "ai") 1 else 1 - round.turn
        if (mode == "ai" && !recorded) {
            recorded = true
            repo?.launch { repo?.recordResult("domino", "ai", "loss", "AI", level) }
        }
    }
}

@Composable
fun DominoScreen(mode: String, level: Int, onExit: () -> Unit) {
    val repo = rememberRepo()
    val vm: DominoVM = viewModel()
    val profile by repo.profile.collectAsState()
    LaunchedEffect(Unit) { vm.start(repo, mode, level) }
    vm.sound = profile.sound
    @Suppress("UNUSED_VARIABLE") val t = vm.tick   // subscribe to mutations
    val r = vm.round
    val me = vm.viewer
    val opp = 1 - me
    val myTurn = !r.finished && r.turn == me && !vm.busy

    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t("domino"), onBack = onExit)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (mode == "ai") "AI · " + levelName(level) else S.t("player") + " " + (opp + 1), color = NexColors.TextDim, fontSize = 12.sp)
                            Text("${vm.scores[opp]}", color = NexColors.Pink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        }
                        Text(S.t("target"), color = NexColors.TextDim, fontSize = 12.sp)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(if (mode == "ai") profile.username else S.t("player") + " " + (me + 1), color = NexColors.TextDim, fontSize = 12.sp)
                            Text("${vm.scores[me]}", color = NexColors.Cyan, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${S.t("opponent")}: ", color = NexColors.TextDim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        repeat(r.hands[opp].size) { TileBack() }
                    }
                    Text("${S.t("tiles_left")}: ${r.boneyard.size}", color = NexColors.TextDim, fontSize = 12.sp)
                }
                // Table
                val scroll = rememberScrollState()
                LaunchedEffect(r.table.size) { scroll.animateScrollTo(scroll.maxValue) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F5132).copy(alpha = 0.55f))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        .horizontalScroll(scroll),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (p in r.table.toList()) TileView(p.left, p.right, vertical = false, width = 76.dp, height = 40.dp)
                    }
                }
                val status = when {
                    r.finished -> S.t("game_over")
                    vm.busy && mode == "ai" -> S.t("thinking")
                    mode == "local" -> S.t("player") + " " + (r.turn + 1)
                    else -> S.t("your_turn")
                }
                Text(status, color = NexColors.Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                // Hand
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t2 in r.hands[me].toList()) {
                        val playable = myTurn && r.playableSides(t2).isNotEmpty()
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (vm.selected == t2) 3.dp else 0.dp, NexColors.Cyan, RoundedCornerShape(8.dp))
                                .clickable(enabled = playable) { vm.select(t2) }
                                .semantics { contentDescription = "Tile ${t2.a}-${t2.b}" }
                        ) {
                            TileView(t2.a, t2.b, vertical = true, width = 44.dp, height = 86.dp, dim = myTurn && !playable)
                        }
                    }
                }
                val sel = vm.selected
                if (sel != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowButton("◀ ${r.leftEnd}", { vm.play(sel, Side.LEFT) }, Modifier.weight(1f))
                        GlowButton("${r.rightEnd} ▶", { vm.play(sel, Side.RIGHT) }, Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(S.t("draw_tile"), { vm.draw() }, Modifier.weight(1f), enabled = myTurn && r.canDraw(me))
                    OutlineButton(S.t("pass"), { vm.pass() }, Modifier.weight(1f), enabled = myTurn && r.canPass(me))
                    OutlineButton(S.t("resign"), { vm.resign() }, Modifier.weight(1f), enabled = vm.matchWinner == -1)
                }
            }
        }

        if (vm.cover && !r.finished) {
            Box(Modifier.fillMaxSize().background(NexColors.Bg).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(24.dp)) {
                    Text(S.t("handoff"), color = NexColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(S.t("player") + " " + (r.turn + 1), color = NexColors.Cyan, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    GlowButton(S.t("ready"), { vm.readyFromCover() })
                }
            }
        }

        if (r.finished && vm.matchWinner == -1) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                containerColor = NexColors.Surface,
                title = { Text(if (r.winner == -1) S.t("draw") else if (mode == "ai") (if (r.winner == 0) S.t("you_win") else S.t("you_lose")) else S.t("win_of") + ": " + S.t("player") + " " + (r.winner + 1), color = NexColors.Text) },
                text = { Text(S.t("score") + ": ${vm.scores[0]} : ${vm.scores[1]}  (+${r.points})", color = NexColors.TextDim) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.nextRound() }) { Text(S.t("next_round"), color = NexColors.Cyan) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = onExit) { Text(S.t("back"), color = NexColors.TextDim) } }
            )
        }
        if (vm.matchWinner != -1) {
            val w = vm.matchWinner
            val title = if (mode == "ai") (if (w == 0) S.t("you_win") else S.t("you_lose")) else S.t("win_of") + ": " + S.t("player") + " " + (w + 1)
            ResultDialog(title, "${vm.scores[0]} : ${vm.scores[1]}", onNew = { vm.newMatch() }, onExit = onExit)
        }
    }
}

@Composable
fun TileBack() {
    Box(Modifier.size(width = 18.dp, height = 32.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3D5AFE)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp)))
}

@Composable
fun TileView(a: Int, b: Int, vertical: Boolean, width: Dp, height: Dp, dim: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (dim) Color(0xFFB9BCCB) else Color(0xFFF7F4EA)
    if (vertical) {
        Column(Modifier.size(width, height).clip(shape).background(bg).border(1.dp, Color(0xFF444444), shape), verticalArrangement = Arrangement.SpaceEvenly) {
            Pips(a, Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF888888)))
            Pips(b, Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(Modifier.size(width, height).clip(shape).background(bg).border(1.dp, Color(0xFF444444), shape)) {
            Pips(a, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF888888)))
            Pips(b, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private val PIPS = arrayOf(
    emptyList<Pair<Int, Int>>(),
    listOf(1 to 1),
    listOf(0 to 0, 2 to 2),
    listOf(0 to 0, 1 to 1, 2 to 2),
    listOf(0 to 0, 0 to 2, 2 to 0, 2 to 2),
    listOf(0 to 0, 0 to 2, 1 to 1, 2 to 0, 2 to 2),
    listOf(0 to 0, 0 to 2, 1 to 0, 1 to 2, 2 to 0, 2 to 2)
)

@Composable
private fun Pips(v: Int, modifier: Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val r = s * 0.09f
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        for ((row, col) in PIPS[v]) {
            drawCircle(Color(0xFF1B1F3B), r, Offset(ox + s * (0.2f + 0.3f * col), oy + s * (0.2f + 0.3f * row)))
        }
    }
}
