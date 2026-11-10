package com.nexplay.game.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.S
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val p by repo.profile.collectAsState()
    var server by rememberSaveable(p.serverUrl) { mutableStateOf(p.serverUrl) }
    var confirm by remember { mutableStateOf(false) }
    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            NexTopBar(S.t("settings"), onBack = onBack)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NexCard(Modifier.fillMaxWidth()) {
                    Text(S.t("language"), color = NexColors.Text, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        for ((code, label) in listOf("tg" to "Тоҷикӣ", "ru" to "Русский", "en" to "English")) {
                            FilterChip(selected = p.lang == code, onClick = { scope.launch { repo.setLang(code) } }, label = { Text(label) })
                        }
                    }
                }
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.t("sound"), color = NexColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = p.sound, onCheckedChange = { scope.launch { repo.setSound(it) } })
                    }
                }
                NexCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        server, { server = it }, label = { Text(S.t("server")) },
                        supportingText = { Text(S.t("server_hint")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    GlowButton(S.t("save"), { scope.launch { repo.setServer(server) } })
                }
                NexCard(Modifier.fillMaxWidth()) {
                    OutlineButton(S.t("reset"), { confirm = true }, Modifier.fillMaxWidth())
                    Text("NEXPLAY 1.0.0 · com.nexplay.game", color = NexColors.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                containerColor = NexColors.Surface,
                title = { Text(S.t("reset"), color = NexColors.Text) },
                text = { Text(S.t("confirm_reset"), color = NexColors.TextDim) },
                confirmButton = { TextButton(onClick = { scope.launch { repo.resetLocal() }; confirm = false }) { Text(S.t("yes"), color = NexColors.Red) } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(S.t("no"), color = NexColors.TextDim) } }
            )
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val p by repo.profile.collectAsState()
    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t("notifications"), onBack = onBack) {
                if (p.notifs.isNotEmpty()) TextButton(onClick = { scope.launch { repo.clearNotifs() } }) { Text(S.t("clear"), color = NexColors.Cyan) }
            }
            if (p.notifs.isEmpty()) EmptyBox(S.t("no_notifs"), "🔔")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(p.notifs) { n -> NexCard(Modifier.fillMaxWidth()) { Text(n, color = NexColors.Text) } }
            }
        }
    }
}
