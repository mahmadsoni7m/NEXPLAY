package com.nexplay.game.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun HomeScreen(onGame: (String) -> Unit, onSettings: () -> Unit, onNotifs: () -> Unit, onLeaderboard: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val p by repo.profile.collectAsState()
    var online by remember { mutableStateOf<Int?>(null) }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BrandText("NEXPLAY", 30)
                        Text(S.t("tagline"), color = NexColors.TextDim, fontSize = 12.sp)
                    }
                    BadgedBox(badge = { if (p.notifs.isNotEmpty()) Badge { Text("${p.notifs.size}") } }) {
                        IconButton(onClick = onNotifs) { Icon(Icons.Filled.Notifications, S.t("notifications"), tint = NexColors.Text) }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, S.t("settings"), tint = NexColors.Text) }
                }
            }
            item { ProfileCard(p) }
            item {
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🟢", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(S.t("online_players"), color = NexColors.TextDim, modifier = Modifier.weight(1f))
                        Text(online?.toString() ?: S.t("online_players_unknown"), color = NexColors.Green, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                }
            }
            item { SectionTitle(S.t("daily")) }
            item { DailyCard(p) { bit, xp -> scope.launch { repo.claimDaily(bit, xp) } } }
            item { SectionTitle(S.t("featured")) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(GameRegistry.games) { g ->
                        GameCard(g.emoji, S.t(g.id), if (g.onlineSupported) S.t("mode_online") + " · " + S.t("mode_ai") else S.t("mode_ai") + " · " + S.t("mode_local"),
                            g.colors, Modifier.width(160.dp)) { onGame(g.id) }
                    }
                }
            }
            item { SectionTitle(S.t("recent")) }
            if (p.matches.isEmpty()) item { NexCard(Modifier.fillMaxWidth()) { EmptyBox(S.t("no_matches")) } }
            else items(p.matches.take(5)) { m ->
                val df = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(S.t(m.game) + " · " + S.t("mode_" + m.mode), color = NexColors.Text, fontWeight = FontWeight.SemiBold)
                            Text(m.opponent + " · " + df.format(Date(m.ts)), color = NexColors.TextDim, fontSize = 12.sp)
                        }
                        val c = when (m.result) { "win" -> NexColors.Green; "loss" -> NexColors.Red; else -> NexColors.Amber }
                        Text(S.t("res_" + m.result), color = c, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(S.t("leaderboard"), Modifier.weight(1f))
                    TextButton(onClick = onLeaderboard) { Text(S.t("ranking"), color = NexColors.Cyan) }
                }
            }
            item { LeaderboardList(game = "overall", limit = 3) }
        }
    }
}

@Composable
private fun ProfileCard(p: Profile) {
    NexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatar, 60)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.username, color = NexColors.Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(S.t("level") + " ${p.level}", color = NexColors.Cyan, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { p.levelProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = NexColors.Violet, trackColor = NexColors.SurfaceHi)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(S.t("wins"), "${p.totalWins}", NexColors.Green, Modifier.weight(1f))
            StatChip(S.t("losses"), "${p.totalLosses}", NexColors.Red, Modifier.weight(1f))
            val t = p.totalWins + p.totalLosses
            StatChip(S.t("winrate"), if (t == 0) "—" else "${p.totalWins * 100 / t}%", NexColors.Amber, Modifier.weight(1f))
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
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color0x22)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = NexColors.Text, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { prog.first.toFloat() / prog.second }, modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = NexColors.Cyan, trackColor = NexColors.SurfaceHi)
                    Text("${prog.first}/${prog.second} · +$xp XP", color = NexColors.TextDim, fontSize = 11.sp)
                }
                if (claimed) Text(S.t("claimed"), color = NexColors.Green, fontSize = 12.sp)
                else OutlineButton(S.t("claim"), { onClaim(bit, xp) }, enabled = done)
            }
        }
    }
}

private val Color0x22 = androidx.compose.ui.graphics.Color(0x22FFFFFF)
