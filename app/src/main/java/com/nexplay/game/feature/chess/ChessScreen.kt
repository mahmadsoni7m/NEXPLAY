package com.nexplay.game.feature.chess

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
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
import com.nexplay.game.feature.checkers.PlayerStrip
import com.nexplay.game.feature.checkers.levelName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

class ChessVM : ViewModel() {
    var state by mutableStateOf(ChessState.initial()); private set
    var selected by mutableStateOf<Int?>(null); private set
    var lastMove by mutableStateOf<ChessMove?>(null); private set
    var thinking by mutableStateOf(false); private set
    var result by mutableStateOf<String?>(null); private set   // "w", "b", "draw"
    var reason by mutableStateOf(""); private set
    var promoChoices by mutableStateOf<List<ChessMove>>(emptyList()); private set
    var myWhite by mutableStateOf(true); private set
    var lastOnline by mutableStateOf<String?>(null); private set

    private var repo: Repository? = null
    private var mode = "ai"
    private var level = 1
    private var started = false
    private var recorded = false
    private val history = ArrayList<String>()

    fun start(r: Repository, m: String, l: Int) {
        if (started) return
        started = true
        repo = r
        mode = m
        level = l
        if (m != "online") newGame()
    }

    fun newGame() {
        state = ChessState.initial()
        selected = null
        lastMove = null
        thinking = false
        result = null
        reason = ""
        promoChoices = emptyList()
        recorded = false
        history.clear()
        history.add(state.positionKey())
    }

    private fun humanTurn(room: OnlineRoom?): Boolean = when (mode) {
        "local" -> true
        "ai" -> state.whiteToMove == myWhite
        else -> room != null && room.phase == "playing" && state.whiteToMove == myWhite
    }

    fun applyRemote(room: OnlineRoom) {
        if (room.game != "chess" || room.state.isEmpty()) return
        state = ChessState.fromFen(room.state)
        myWhite = room.color == "w"
        selected = null
        promoChoices = emptyList()
        lastOnline = room.last
        result = if (room.phase == "over") room.result else null
    }

    fun inCheckNow(): Boolean = result == null && state.inCheck()

    fun legalFrom(sq: Int): List<ChessMove> = state.legalMoves().filter { it.from == sq }

    fun destinations(): Set<Int> {
        val s = selected ?: return emptySet()
        return legalFrom(s).map { it.to }.toSet()
    }

    fun tap(sq: Int, room: OnlineRoom?) {
        if (result != null || thinking || !humanTurn(room)) return
        val sel = selected
        if (sel != null) {
            val cands = legalFrom(sel).filter { it.to == sq }
            if (cands.size == 1) {
                commit(cands[0])
                return
            } else if (cands.size > 1) {
                promoChoices = cands
                return
            }
        }
        val p = state.board[sq]
        if (p != 0 && (p > 0) == state.whiteToMove && legalFrom(sq).isNotEmpty()) selected = sq else selected = null
    }

    fun choosePromotion(m: ChessMove) {
        promoChoices = emptyList()
        commit(m)
    }

    fun dismissPromotion() { promoChoices = emptyList() }

    private fun commit(m: ChessMove) {
        selected = null
        if (mode == "online") {
            val o = JSONObject().put("from", ChessMove.sqName(m.from)).put("to", ChessMove.sqName(m.to))
            if (m.promo != 0) o.put("promo", "nbrq"[m.promo - 2].toString())
            repo?.online?.sendMove(o)
            return
        }
        state = state.apply(m)
        lastMove = m
        history.add(state.positionKey())
        afterMove()
    }

    private fun finish(res: String, why: String) {
        result = res
        reason = why
        if (mode == "ai" && !recorded) {
            recorded = true
            val out = when (res) { "draw" -> "draw"; (if (myWhite) "w" else "b") -> "win"; else -> "loss" }
            repo?.launch { repo?.recordResult("chess", "ai", out, "AI", level) }
        }
    }

    private fun afterMove() {
        val st = state.status()
        if (st != null) {
            when {
                st.startsWith("checkmate") -> finish(st.substringAfter(":"), "checkmate")
                st == "stalemate" -> finish("draw", "stalemate")
                else -> finish("draw", st)
            }
            return
        }
        if (history.count { it == state.positionKey() } >= 3) {
            finish("draw", "repetition")
            return
        }
        if (mode == "ai" && state.whiteToMove != myWhite) runAi()
    }

    private fun runAi() {
        thinking = true
        viewModelScope.launch {
            val snap = state
            val mv = withContext(Dispatchers.Default) { ChessAI(level).choose(snap) }
            delay(200)
            thinking = false
            if (mv != null && result == null) {
                state = state.apply(mv)
                lastMove = mv
                history.add(state.positionKey())
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
        finish(if (mode == "ai") (if (myWhite) "b" else "w") else (if (state.whiteToMove) "b" else "w"), "resign")
    }
}

private val GLYPH = "♟♞♝♜♛♚"

@Composable
fun ChessScreen(mode: String, level: Int, onExit: () -> Unit) {
    val repo = rememberRepo()
    val vm: ChessVM = viewModel()
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
            val mine = if (vm.myWhite) "w" else "b"
            if (mode == "local" || result == mine) Sfx.win(true) else if (result != "draw") Sfx.lose(true)
        }
    }
    val flip = mode == "online" && !vm.myWhite
    val dests = vm.destinations()
    val st = vm.state
    val kingSq = if (vm.inCheckNow()) st.kingSquare(st.whiteToMove) else -1
    val lastOnlineSquares = remember(vm.lastOnline) {
        val s = vm.lastOnline
        if (s != null && s.length >= 4) listOf(ChessMove.sqIndex(s.substring(0, 2)), ChessMove.sqIndex(s.substring(2, 4))) else emptyList()
    }

    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t("chess"), onBack = onExit)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val oppName = when (mode) {
                    "ai" -> "AI · " + levelName(level)
                    "online" -> room?.oppName?.ifEmpty { "…" } ?: "…"
                    else -> S.t("black")
                }
                val topWhite = flip
                PlayerStrip(oppName, 0, st.whiteToMove == topWhite && result == null)
                SquareBoard(
                    flip = flip,
                    onTap = { vm.tap(it, room) },
                    light = Color(0xFF4A5290),
                    dark = Color(0xFF1F2551),
                    highlight = { sq ->
                        val lm = vm.lastMove
                        when {
                            sq == vm.selected -> Color(0x88FFD54F)
                            sq == kingSq -> Color(0x99FF5252)
                            lm != null && (sq == lm.from || sq == lm.to) -> Color(0x449C7BFF)
                            sq in lastOnlineSquares -> Color(0x449C7BFF)
                            else -> null
                        }
                    },
                    description = { sq ->
                        val p = st.board[sq]
                        ChessMove.sqName(sq) + if (p == 0) "" else (if (p > 0) " white " else " black ") + listOf("pawn", "knight", "bishop", "rook", "queen", "king")[abs(p) - 1]
                    }
                ) { sq, cell ->
                    val p = st.board[sq]
                    if (p != 0) {
                        val white = p > 0
                        Text(
                            GLYPH[abs(p) - 1].toString(),
                            style = TextStyle(
                                fontSize = (cell.value * 0.82f).sp,
                                color = if (white) Color(0xFFFFF8E7) else Color(0xFF0E0A1A),
                                shadow = Shadow(if (white) Color(0xFF000000) else Color(0xFFB39DFF), Offset(0f, 0f), 6f)
                            )
                        )
                    }
                    if (sq in dests) {
                        if (p == 0) Box(Modifier.size(cell * 0.3f).clip(CircleShape).background(Color(0xCC00E5FF)))
                        else Box(Modifier.fillMaxSize().background(Color(0x55FF4081)))
                    }
                }
                PlayerStrip(
                    when (mode) { "online" -> profile.onlineUser.ifEmpty { profile.username }; "local" -> S.t("white"); else -> profile.username },
                    0,
                    st.whiteToMove != topWhite && result == null
                )
                val status = when {
                    mode == "online" && room?.phase == "playing" && room?.oppOnline == false -> S.t("opp_offline")
                    reconnecting && mode == "online" -> S.t("reconnecting")
                    vm.thinking -> S.t("thinking")
                    result != null -> S.t("game_over")
                    vm.inCheckNow() -> S.t("check")
                    mode == "local" -> if (st.whiteToMove) S.t("white") else S.t("black")
                    else -> if (st.whiteToMove == vm.myWhite) S.t("your_turn") else S.t("opp_turn")
                }
                Text(status, color = if (vm.inCheckNow()) NexColors.Red else NexColors.Cyan, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode != "online") OutlineButton(S.t("new_game"), { vm.newGame() }, Modifier.weight(1f))
                    OutlineButton(S.t("resign"), { vm.resign() }, Modifier.weight(1f), enabled = result == null)
                }
            }
        }

        if (vm.promoChoices.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { vm.dismissPromotion() },
                containerColor = NexColors.Surface,
                title = { Text(S.t("promote"), color = NexColors.Text) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (m in vm.promoChoices) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NexColors.SurfaceHi)
                                    .clickable { vm.choosePromotion(m) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(GLYPH[m.promo - 1].toString(), fontSize = 34.sp, color = if (st.whiteToMove) Color(0xFFFFF8E7) else Color(0xFF8E7CFF))
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (result != null) {
            val mine = if (vm.myWhite) "w" else "b"
            val title = when {
                result == "draw" -> S.t("draw")
                mode == "local" -> S.t("win_of") + ": " + (if (result == "w") S.t("white") else S.t("black"))
                result == mine -> S.t("you_win")
                else -> S.t("you_lose")
            }
            val sub = when (vm.reason) {
                "checkmate" -> S.t("checkmate")
                "stalemate" -> S.t("stalemate")
                else -> if (mode == "online" && room != null) "${room!!.delta.let { if (it >= 0) "+$it" else "$it" }} ${S.t("rating")}" else null
            }
            ResultDialog(title, sub,
                onNew = if (mode == "online") null else ({ vm.newGame() }),
                onExit = { if (mode == "online") repo.online.leaveRoom(); onExit() })
        }
    }
}
