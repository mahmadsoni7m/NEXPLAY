package com.nexplay.game.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.nav.GameRegistry
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.Profile
import com.nexplay.game.data.S
import com.nexplay.game.feature.leaderboard.LeaderboardList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onGame: (String) -> Unit,
    onSettings: () -> Unit,
    onNotifs: () -> Unit,
    onLeaderboard: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val p by repo.profile.collectAsState()
    var online by remember { mutableStateOf<Int?>(null) }
    var reward by remember { mutableStateOf(0) }
    LaunchedEffect(p.serverUrl) {
        while (true) {
            online = repo.onlineCount()
            delay(30_000)
        }
    }
    NexBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HudHeader(p, online, onSettings, onNotifs) }
            item { QuickPlayHero(onQuickPlay) }
            item {
                ChestCard(p, reward) { scope.launch { reward = repo.claimLogin() } }
            }
            item { SectionTitle(S.t("games")) }
            items(GameRegistry.games.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (g in row) {
                        GameCard(
                            g.emoji, S.t(g.id),
                            if (g.onlineSupported) S.t("mode_online") + " · " + S.t("mode_ai") else S.t("mode_ai") + " · " + S.t("mode_local"),
                            g.colors, Modifier.weight(1f).height(150.dp)
                        ) { onGame(g.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { SectionTitle(S.t("daily")) }
            item { DailyCard(p) { bit, xp -> scope.launch { repo.claimDaily(bit, xp) } } }
            item { SectionTitle(S.t("recent")) }
            if (p.matches.isEmpty()) item { NexCard(Modifier.fillMaxWidth()) { EmptyBox(S.t("no_matches")) } }
            else items(p.matches.take(5)) { m ->
                val df = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(S.t(m.game) + " · " + S.t("mode_" + m.mode), color = NexColors.Text, fontWeight = FontWeight.Bold)
                            Text(m.opponent + " · " + df.format(Date(m.ts)), color = NexColors.TextDim, fontSize = 12.sp)
                        }
                        val c = when (m.result) { "win" -> NexColors.Green; "loss" -> NexColors.Red; else -> NexColors.Amber }
                        Text(S.t("res_" + m.result).uppercase(), color = c, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(S.t("ranking_title"), Modifier.weight(1f))
                    TextButton(onClick = onLeaderboard) { Text(S.t("ranking").uppercase(), color = NexColors.Cyan, fontWeight = FontWeight.Black) }
                }
            }
            item { LeaderboardList(game = "overall", limit = 3) }
        }
    }
}

@Composable
private fun HudHeader(p: Profile, online: Int?, onSettings: () -> Unit, onNotifs: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandText("NEXPLAY", 26, Modifier.weight(1f))
            HudPill("🪙", "${p.coins}", NexColors.Amber)
            Spacer(Modifier.width(6.dp))
            BadgedBox(badge = { if (p.notifs.isNotEmpty()) Badge { Text("${p.notifs.size}") } }) {
                IconButton(onClick = onNotifs) { Icon(Icons.Filled.Notifications, S.t("notifications"), tint = NexColors.Text) }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, S.t("settings"), tint = NexColors.Text) }
        }
        Spacer(Modifier.height(8.dp))
        NexCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.avatar, 64)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.username.uppercase(), color = NexColors.Text, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)).background(NexColors.Brand).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("${S.t("level").uppercase()} ${p.level}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("XP ${p.xp % 100}/100", color = NexColors.TextDim, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { p.levelProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = NexColors.Cyan, trackColor = Color(0x33FFFFFF)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(S.t("wins"), "${p.totalWins}", NexColors.Green, Modifier.weight(1f))
                StatChip(S.t("losses"), "${p.totalLosses}", NexColors.Red, Modifier.weight(1f))
                val t = p.totalWins + p.totalLosses
                StatChip(S.t("winrate"), if (t == 0) "—" else "${p.totalWins * 100 / t}%", NexColors.Amber, Modifier.weight(1f))
                StatChip("ONLINE", online?.toString() ?: "—", NexColors.Cyan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickPlayHero(onQuickPlay: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "hero").animateFloat(0.5f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "heroG")
    val shape = CutCornerShape(topStart = 8.dp, topEnd = 30.dp, bottomEnd = 8.dp, bottomStart = 30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2A1B6B), Color(0xFF0E3A5C))))
            .border(2.dp, Brush.linearGradient(listOf(NexColors.Pink.copy(alpha = glow), NexColors.Cyan.copy(alpha = glow))), shape)
            .padding(18.dp)
    ) {
        Text("⚔️", fontSize = 90.sp, modifier = Modifier.align(Alignment.CenterEnd).androidxAlpha(0.35f))
        Column {
            Text(S.t("tagline").uppercase(), color = NexColors.Cyan, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(S.t("quick_play").uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            GlowButton("▶ " + S.t("play"), onQuickPlay, Modifier.fillMaxWidth(0.6f), colors = listOf(NexColors.Pink, NexColors.Violet))
        }
    }
}

private fun Modifier.androidxAlpha(a: Float): Modifier = this.then(Modifier.alpha(a))

@Composable
private fun ChestCard(p: Profile, reward: Int, onOpen: () -> Unit) {
    NexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.canClaimLogin) "🎁" else "📦", fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(S.t("login_reward").uppercase(), color = NexColors.Text, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(S.t("streak") + ": ${p.streak} 🔥", color = NexColors.TextDim, fontSize = 12.sp)
                if (reward > 0) Text("+$reward 🪙", color = NexColors.Amber, fontWeight = FontWeight.Black)
            }
            if (p.canClaimLogin) GlowButton(S.t("open_chest"), onOpen, colors = listOf(NexColors.Amber, Color(0xFFFF6D00)))
            else Text(S.t("come_back"), color = NexColors.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DailyCard(p: Profile, onClaim: (Int, Int) -> Unit) {
    val d = p.daily
    val items = listOf(
        Triple(S.t("d_play3"), minOf(d.played, 3) to 3, 30),
        Triple(S.t("d_win1"), minOf(d.wins, 1) to 1, 40),
        Triple(S.t("d_beat_med"), minOf(d.beatMedium, 1) to 1, 60)
    )
    NexCard(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (title, prog, xp) ->
            val bit = 1 shl i
            val done = prog.first >= prog.second
            val claimed = d.claimed and bit != 0
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x22FFFFFF))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = NexColors.Text, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { prog.first.toFloat() / prog.second },
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = NexColors.Cyan, trackColor = Color(0x33FFFFFF)
                    )
                    Text("${prog.first}/${prog.second} · +$xp XP", color = NexColors.TextDim, fontSize = 11.sp)
                }
                if (claimed) Text(S.t("claimed"), color = NexColors.Green, fontSize = 12.sp)
                else OutlineButton(S.t("claim"), { onClaim(bit, xp) }, enabled = done)
            }
        }
    }
}
