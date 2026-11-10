package com.nexplay.game.feature.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.nav.GameRegistry
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.LbEntry
import com.nexplay.game.data.S

@Composable
fun LeaderboardScreen() {
    var game by rememberSaveable { mutableStateOf("overall") }
    NexBackground {
        Column(Modifier.fillMaxSize()) {
            NexTopBar(S.t("leaderboard"))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf("overall") + GameRegistry.games.map { it.id }
                for (t in tabs) FilterChip(selected = game == t, onClick = { game = t }, label = { Text(S.t(t)) })
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                LeaderboardList(game = game, limit = 50)
                Text(S.t("lb_hint"), color = NexColors.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun LeaderboardList(game: String, limit: Int) {
    val repo = rememberRepo()
    val profile by repo.profile.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<List<LbEntry>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(game, reload, profile.serverUrl) {
        loading = true
        error = null
        try {
            data = repo.leaderboard(game)
        } catch (e: Exception) {
            error = if (profile.serverUrl.isEmpty()) S.t("server_hint") else (e.message ?: S.t("error"))
        }
        loading = false
    }
    when {
        loading -> LoadingBox()
        error != null -> ErrorBox(error!!, { reload++ })
        data.isEmpty() -> EmptyBox(S.t("empty_lb"), "🏆")
        else -> {
            val rows = data.take(limit)
            if (limit <= 3) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rows.forEachIndexed { i, e -> LbRow(i, e) } }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                itemsIndexed(rows) { i, e -> LbRow(i, e) }
            }
        }
    }
}

@Composable
private fun LbRow(rank: Int, e: LbEntry) {
    val medal = when (rank) { 0 -> "🥇" to NexColors.Gold; 1 -> "🥈" to NexColors.Silver; 2 -> "🥉" to NexColors.Bronze; else -> null }
    NexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                if (medal != null) Text(medal.first, fontSize = 26.sp) else Text("${rank + 1}", color = NexColors.TextDim, fontWeight = FontWeight.Bold)
            }
            Avatar(e.avatar, 42)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = medal?.second ?: NexColors.Text, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${S.t("wins")}: ${e.wins} · ${S.t("winrate")}: ${"%.0f".format(e.winRate * 100)}%", color = NexColors.TextDim, fontSize = 11.sp)
            }
            Text("${e.rating}", color = medal?.second ?: NexColors.Cyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Suppress("unused") private val unusedColor = Color.Unspecified
