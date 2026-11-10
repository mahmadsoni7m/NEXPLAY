package com.nexplay.game.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.nav.GameRegistry
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.S
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val p by repo.profile.collectAsState()
    var name by rememberSaveable(p.username) { mutableStateOf(p.username) }
    NexBackground {
        Column(Modifier.fillMaxSize()) {
            NexTopBar(S.t("profile"))
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    NexCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.avatar, 64)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(S.t("level") + " ${p.level}", color = NexColors.Cyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text("XP ${p.xp}", color = NexColors.TextDim)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(S.t("avatar"), color = NexColors.TextDim, fontSize = 12.sp)
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AVATARS.forEachIndexed { i, a ->
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .border(if (i == p.avatar) 2.dp else 0.dp, NexColors.Cyan, CircleShape)
                                        .clickable { scope.launch { repo.setAvatar(i) } },
                                    contentAlignment = Alignment.Center
                                ) { Text(a, fontSize = 18.sp) }
                            }
                        }
                        OutlinedTextField(name, { name = it.take(20) }, label = { Text(S.t("username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        GlowButton(S.t("save"), { scope.launch { repo.setUsername(name) } }, enabled = name.isNotBlank())
                    }
                }
                items(GameRegistry.games) { g ->
                    val s = p.stats[g.id]
                    NexCard(Modifier.fillMaxWidth()) {
                        Text(g.emoji + " " + S.t(g.id), color = NexColors.Text, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(S.t("wins"), "${s?.wins ?: 0}", NexColors.Green, Modifier.weight(1f))
                            StatChip(S.t("losses"), "${s?.losses ?: 0}", NexColors.Red, Modifier.weight(1f))
                            StatChip(S.t("draws"), "${s?.draws ?: 0}", NexColors.Amber, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
