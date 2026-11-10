package com.nexplay.game.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.nav.GameRegistry
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.NetStatus
import com.nexplay.game.data.S
import kotlinx.coroutines.launch

@Composable
fun OnlineScreen(onBack: () -> Unit, onEnterGame: (game: String) -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val profile by repo.profile.collectAsState()
    val status by repo.online.status.collectAsState()
    val room by repo.online.room.collectAsState()
    val queueing by repo.online.queueing.collectAsState()
    val error by repo.online.error.collectAsState()
    val reconnecting by repo.online.reconnecting.collectAsState()

    var server by rememberSaveable(profile.serverUrl) { mutableStateOf(profile.serverUrl) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var game by rememberSaveable { mutableStateOf("checkers") }
    var authError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var navigatedCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(room?.code, room?.phase) {
        val r = room
        if (r != null && r.phase != "waiting" && navigatedCode != r.code) {
            navigatedCode = r.code
            onEnterGame(r.game)
        }
    }
    val loggedIn = profile.token.isNotEmpty()
    val connected = status == NetStatus.ONLINE

    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            NexTopBar(S.t("play_online"), onBack = onBack)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = when (status) { NetStatus.ONLINE -> NexColors.Green; NetStatus.CONNECTING -> NexColors.Amber; else -> NexColors.Red }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                reconnecting -> S.t("reconnecting")
                                status == NetStatus.ONLINE -> S.t("status_online")
                                status == NetStatus.CONNECTING -> S.t("status_connecting")
                                else -> S.t("status_offline")
                            },
                            color = NexColors.Text, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = server, onValueChange = { server = it },
                        label = { Text(S.t("server")) },
                        supportingText = { Text(S.t("server_hint")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!loggedIn) {
                    NexCard(Modifier.fillMaxWidth()) {
                        Text(S.t("auth_title"), color = NexColors.Text, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(user, { user = it }, label = { Text(S.t("username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            pass, { pass = it }, label = { Text(S.t("password")) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                        )
                        authError?.let { Text(it, color = NexColors.Red, modifier = Modifier.padding(top = 6.dp)) }
                        Spacer(Modifier.height(10.dp))
                        if (busy) LoadingBox()
                        else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowButton(S.t("login"), {
                                scope.launch {
                                    busy = true; authError = null
                                    repo.setServer(server)
                                    try { repo.login(false, user, pass); pass = "" } catch (e: Exception) { authError = e.message }
                                    busy = false
                                }
                            }, Modifier.weight(1f))
                            OutlineButton(S.t("register"), {
                                scope.launch {
                                    busy = true; authError = null
                                    repo.setServer(server)
                                    try { repo.login(true, user, pass); pass = "" } catch (e: Exception) { authError = e.message }
                                    busy = false
                                }
                            }, Modifier.weight(1f))
                        }
                    }
                } else {
                    NexCard(Modifier.fillMaxWidth()) {
                        Text(S.t("signed_as") + " " + profile.onlineUser, color = NexColors.Text, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!connected && status != NetStatus.CONNECTING) {
                                GlowButton(S.t("connect"), {
                                    scope.launch { repo.setServer(server); repo.online.clearError(); repo.online.connect() }
                                }, Modifier.weight(1f))
                            } else {
                                OutlineButton(S.t("disconnect"), { repo.online.disconnect() }, Modifier.weight(1f))
                            }
                            OutlineButton(S.t("logout"), { scope.launch { repo.logout() } }, Modifier.weight(1f))
                        }
                    }
                }

                error?.let { ErrorBox(it, onRetry = { repo.online.clearError() }) }

                if (loggedIn && connected) {
                    val r = room
                    if (r != null && r.phase == "waiting") {
                        NexCard(Modifier.fillMaxWidth()) {
                            Text(S.t("waiting_opp"), color = NexColors.TextDim)
                            Text(r.code, color = NexColors.Cyan, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlineButton(S.t("cancel"), { repo.online.leaveRoom() })
                        }
                    } else if (r != null) {
                        NexCard(Modifier.fillMaxWidth()) {
                            Text("${S.t(r.game)} · ${r.oppName}", color = NexColors.Text, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            GlowButton(S.t("play"), { onEnterGame(r.game) })
                        }
                    } else {
                        NexCard(Modifier.fillMaxWidth()) {
                            Text(S.t("online_games_note"), color = NexColors.TextDim, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (g in GameRegistry.games.filter { it.onlineSupported }) {
                                    FilterChip(selected = game == g.id, onClick = { game = g.id }, label = { Text(S.t(g.id)) })
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (queueing) {
                                LoadingBox()
                                OutlineButton(S.t("cancel"), { repo.online.cancelQueue() }, Modifier.fillMaxWidth())
                            } else {
                                GlowButton(S.t("matchmaking"), { repo.online.queue(game) }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlineButton(S.t("create_room"), { repo.online.createRoom(game) }, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        code, { code = it.uppercase().take(6) }, label = { Text(S.t("room_code")) },
                                        singleLine = true, modifier = Modifier.weight(1f)
                                    )
                                    GlowButton(S.t("join_room"), { repo.online.joinRoom(code) }, enabled = code.length >= 4)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
