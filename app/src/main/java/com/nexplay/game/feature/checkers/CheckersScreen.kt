package com.nexplay.game.feature.checkers

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.OnlineRoom
import com.nexplay.game.data.Repository
import com.nexplay.game.data.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CheckersVM : ViewModel() {
    var state by mutableStateOf(CkState.initial()); private set
    var selected by mutableStateOf<Int?>(null); private set
    var lastMove by mutableStateOf<CkMove?>(null); private set
    var thinking by mutableStateOf(false); private set
    var result by mutableStateOf<Int?>(null); private set     // Ck.W, Ck.B or 0 (draw)
    var message by mutableStateOf<String?>(null); private set
    var myColor by mutableStateOf(Ck.W); private set
    var lastOnlinePath by mutableStateOf<List<Int>>(emptyList()); private set

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
        if (m == "online") return
        newGame()
    }

    fun newGame() {
        state = CkState.initial()
        selected = null
        lastMove = null
        result = null
        message = null
        thinking = false
        recorded = false
    }

    private fun humanTurn(online: OnlineRoom?): Boolean = when (mode) {
        "local" -> true
        "ai" -> state.turn == myColor
        else -> online != null && online.phase == "playing" && state.turn == myColor
    }

    fun applyRemote(room: OnlineRoom) {
        if (room.game != "checkers" || room.state.length < 65) return
        state = CkState.fromWire(room.state)
        myColor = if (room.color == "w") Ck.W else Ck.B
        selected = null
        lastOnlinePath = room.last?.split("-")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        result = if (room.phase == "over") when (room.result) { "w" -> Ck.W; "b" -> Ck.B; else -> 0 } else null
    }

    fun tap(sq: Int, room: OnlineRoom?) {
        if (result != null || thinking || !humanTurn(room)) return
        val moves = state.legalMoves()
        val sel = selected
        if (sel != null) {
            val m = moves.firstOrNull { it.from == sel && it.to == sq }
            if (m != null) {
                commit(m)
                return
            }
        }
        if (moves.any { it.from == sq }) {
            selected = sq
            message = null
        } else {
            selected = null
            if (Ck.owner(state.cells[sq]) == state.turn && moves.isNotEmpty() && moves[0].captured.isNotEmpty()) {
                message = S.t("must_capture")
            }
        }
    }

    fun destinations(): Set<Int> {
        val sel = selected ?: return emptySet()
        return state.legalMoves().filter { it.from == sel }.map { it.to }.toSet()
    }

    private fun commit(m: CkMove) {
        selected = null
        message = null
        if (mode == "online") {
            val arr = JSONArray()
            m.path.forEach { arr.put(it) }
            repo?.online?.sendMove(JSONObject().put("path", arr))
            return
        }
        state = state.apply(m)
        lastMove = m
        afterMove()
    }

    private fun afterMove() {
        val res = state.result()
        if (res != null) {
            result = res
            if (!recorded && mode == "ai") {
                recorded = true
                val outcome = when (res) { myColor -> "win"; 0 -> "draw"; else -> "loss" }
                repo?.launch { repo?.recordResult("checkers", "ai", outcome, "SONI-AI", level) }
            }
            return
        }
        if (mode == "ai" && state.turn != myColor) runAi()
    }

    private fun runAi() {
        thinking = true
        viewModelScope.launch {
            val snapshot = state
            val mv = withContext(Dispatchers.Default) { CkAI(level).choose(snapshot) }
            delay(250)
            thinking = false
            if (mv != null && result == null) {
                state = state.apply(mv)
                lastMove = mv
                afterMove()
            }
        }
    }

    fun resign() {
        if (result != null) return
        if (mode == "online") {
            repo?.online?.resign()
            return
        }
        result = if (mode == "ai") (if (myColor == Ck.W) Ck.B else Ck.W) else (if (state.turn == Ck.W) Ck.B else Ck.W)
        if (mode == "ai" && !recorded) {
            recorded = true
            repo?.launch { repo?.recordResult("checkers", "ai", "loss", "SONI-AI", level) }
        }
    }
}

@Composable
fun CheckersScreen(mode: String, level: Int, onExit: () -> Unit) {
    val repo = rememberRepo()
    val vm: CheckersVM = viewModel()
    val profile by repo.profile.collectAsState()
    val room by repo.online.room.collectAsState()
    val reconnecting by repo.online.reconnecting.collectAsState()
    LaunchedEffect(Unit) { vm.start(repo, mode, level) }
    LaunchedEffect(room) {
        val r = room
        if (mode == "online" && r != null) vm.applyRemote(r)
    }
    val result = vm.result
    LaunchedEffect(result) {
        if (result != null && profile.sound) {
            val won = result == vm.myColor
            if (mode == "local" || won) Sfx.win(true) else if (result != 0) Sfx.lose(true)
        }
    }
    val flip = mode == "online" && vm.myColor == Ck.B
    val dests = vm.destinations()
    val movable = remember(vm.state, vm.selected) {
        if (vm.result == null) vm.state.legalMoves().map { it.from }.toSet() else emptySet()
    }

    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t("checkers"), onBack = onExit)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val opp = when (mode) {
                    "ai" -> "SONI · " + levelName(level)
                    "online" -> room?.oppName?.ifEmpty { "…" } ?: "…"
                    else -> S.t("black")
                }
                PlayerStrip(opp, vm.state.count(if (flip) Ck.W else Ck.B), vm.state.turn == (if (flip) Ck.W else Ck.B) && vm.result == null)
                SquareBoard(
                    flip = flip,
                    onTap = { vm.tap(it, room) },
                    highlight = { sq ->
                        val lm = vm.lastMove
                        when {
                            sq == vm.selected -> Color(0x88FFD54F)
                            sq in dests -> Color(0x6600E5FF)
                            lm != null && (sq == lm.from || sq == lm.to) -> Color(0x449C7BFF)
                            mode == "online" && sq in vm.lastOnlinePath -> Color(0x449C7BFF)
                            else -> null
                        }
                    },
                    description = { sq ->
                        val p = vm.state.cells[sq]
                        "${sq / 8 + 1}-${sq % 8 + 1}" + when (p) { Ck.W -> " white"; Ck.B -> " black"; Ck.WK -> " white king"; Ck.BK -> " black king"; else -> "" }
                    }
                ) { sq, cell ->
                    val p = vm.state.cells[sq]
                    if (p != Ck.EMPTY) CheckerPiece(p, cell * 0.78f, sq in movable && vm.state.turn == Ck.owner(p) && vm.selected == null)
                    else if (sq in dests) Box(Modifier.size(cell * 0.28f).clip(CircleShape).background(Color(0xCC00E5FF)))
                }
                PlayerStrip(
                    when (mode) { "online" -> profile.onlineUser.ifEmpty { profile.username }; "local" -> S.t("white"); else -> profile.username },
                    vm.state.count(if (flip) Ck.B else Ck.W),
                    vm.state.turn == (if (flip) Ck.B else Ck.W) && vm.result == null
                )
                val status = when {
                    mode == "online" && room?.phase == "playing" && room?.oppOnline == false -> S.t("opp_offline")
                    reconnecting && mode == "online" -> S.t("reconnecting")
                    vm.message != null -> vm.message!!
                    vm.thinking -> S.t("thinking")
                    vm.result != null -> S.t("game_over")
                    mode == "ai" -> if (vm.state.turn == vm.myColor) S.t("your_turn") else S.t("opp_turn")
                    mode == "online" -> if (vm.state.turn == vm.myColor) S.t("your_turn") else S.t("opp_turn")
                    else -> (if (vm.state.turn == Ck.W) S.t("white") else S.t("black"))
                }
                Text(status, color = NexColors.Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode != "online") OutlineButton(S.t("new_game"), { vm.newGame() }, Modifier.weight(1f))
                    OutlineButton(S.t("resign"), { vm.resign() }, Modifier.weight(1f), enabled = vm.result == null)
                }
            }
        }
        if (result != null) {
            val won = if (mode == "local") true else result == vm.myColor
            val title = when {
                result == 0 -> S.t("draw")
                mode == "local" -> S.t("win_of") + ": " + (if (result == Ck.W) S.t("white") else S.t("black"))
                won -> S.t("you_win")
                else -> S.t("you_lose")
            }
            ResultDialog(title, if (mode == "online" && room != null) "${room!!.delta.let { if (it >= 0) "+$it" else "$it" }} ${S.t("rating")}" else null,
                onNew = if (mode == "online") null else ({ vm.newGame() }),
                onExit = { if (mode == "online") repo.online.leaveRoom(); onExit() },
                outcome = if (mode == "local") null else if (result == 0) "draw" else if (won) "win" else "loss")
        }
    }
}

fun levelName(level: Int): String = when (level) { 0 -> S.t("easy"); 1 -> S.t("medium"); 2 -> S.t("hard"); else -> S.t("expert") }

@Composable
fun PlayerStrip(name: String, pieces: Int, active: Boolean) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val glow by rememberInfiniteTransition(label = "ps").animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "psG")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (active) Brush.horizontalGradient(listOf(Color(0x667C4DFF), Color(0x2200E5FF)))
                else Brush.horizontalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF)))
            )
            .border(1.5.dp, if (active) NexColors.Cyan.copy(alpha = glow) else Color(0x22FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text((if (active) "▶ " else "") + name, color = if (active) NexColors.Cyan else NexColors.Text, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
        Text("● $pieces", color = NexColors.TextDim)
    }
}

@Composable
fun CheckerPiece(p: Int, size: androidx.compose.ui.unit.Dp, pulse: Boolean) {
    key(p) { PopIn { CheckerPieceBody(p, size, pulse) } }
}

@Composable
fun CheckerPieceBody(p: Int, size: androidx.compose.ui.unit.Dp, pulse: Boolean) {
    val white = Ck.owner(p) == Ck.W
    val brush = if (white) Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFB8C4E8)))
    else Brush.radialGradient(listOf(Color(0xFFFF6E9C), Color(0xFF8E1F4A)))
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(brush)
            .border(if (pulse) 2.dp else 1.dp, if (pulse) NexColors.Cyan else Color(0x55000000), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (Ck.isKing(p)) Text("♛", fontSize = (size.value * 0.55f).sp, color = NexColors.Gold)
    }
}
