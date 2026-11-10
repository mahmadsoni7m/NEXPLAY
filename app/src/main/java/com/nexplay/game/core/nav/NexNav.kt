package com.nexplay.game.core.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexplay.game.core.ui.*
import com.nexplay.game.data.S
import com.nexplay.game.feature.home.HomeScreen
import com.nexplay.game.feature.leaderboard.LeaderboardScreen
import com.nexplay.game.feature.online.OnlineScreen
import com.nexplay.game.feature.profile.ProfileScreen
import com.nexplay.game.feature.settings.NotificationsScreen
import com.nexplay.game.feature.settings.SettingsScreen

private val TABS = listOf("home", "games", "ranking", "profile")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val showBar = route in TABS
    val slideIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition =
        { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } }
    val slideOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition =
        { fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { -it / 12 } }

    fun openTab(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = NexColors.Bg,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = NexColors.Surface) {
                val icons = listOf(Icons.Filled.Home, Icons.Filled.PlayArrow, Icons.Filled.Star, Icons.Filled.Person)
                TABS.forEachIndexed { i, r ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { openTab(r) },
                        icon = { Icon(icons[i], contentDescription = S.t(r)) },
                        label = { Text(S.t(r)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NexColors.Cyan, selectedTextColor = NexColors.Cyan,
                            indicatorColor = NexColors.SurfaceHi, unselectedIconColor = NexColors.TextDim, unselectedTextColor = NexColors.TextDim
                        )
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = slideIn, exitTransition = slideOut, popEnterTransition = slideIn, popExitTransition = slideOut
        ) {
            composable("home") {
                HomeScreen(
                    onGame = { nav.navigate("setup/$it") },
                    onSettings = { nav.navigate("settings") },
                    onNotifs = { nav.navigate("notifs") },
                    onLeaderboard = { openTab("ranking") }
                )
            }
            composable("games") { GamesList { nav.navigate("setup/$it") } }
            composable("ranking") { LeaderboardScreen() }
            composable("profile") { ProfileScreen() }
            composable("settings") { SettingsScreen { nav.popBackStack() } }
            composable("notifs") { NotificationsScreen { nav.popBackStack() } }
            composable("online") {
                OnlineScreen(onBack = { nav.popBackStack() }, onEnterGame = { g -> nav.navigate("play/$g/online/0") })
            }
            composable("setup/{game}", arguments = listOf(navArgument("game") { type = NavType.StringType })) { e ->
                val id = e.arguments?.getString("game") ?: "checkers"
                SetupScreen(
                    id,
                    onBack = { nav.popBackStack() },
                    onStart = { mode, level -> nav.navigate("play/$id/$mode/$level") },
                    onOnline = { nav.navigate("online") }
                )
            }
            composable(
                "play/{game}/{mode}/{level}",
                arguments = listOf(
                    navArgument("game") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("level") { type = NavType.IntType }
                )
            ) { e ->
                val g = GameRegistry.find(e.arguments?.getString("game") ?: "")
                val mode = e.arguments?.getString("mode") ?: "ai"
                val level = e.arguments?.getInt("level") ?: 1
                if (g != null) g.screen(mode, level) { nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun GamesList(onGame: (String) -> Unit) {
    NexBackground {
        Column(Modifier.fillMaxSize()) {
            NexTopBar(S.t("games"))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(GameRegistry.games) { g ->
                    GameCard(g.emoji, S.t(g.id), if (g.onlineSupported) S.t("play_online") else S.t("play_ai"), g.colors, Modifier.fillMaxWidth()) { onGame(g.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(id: String, onBack: () -> Unit, onStart: (String, Int) -> Unit, onOnline: () -> Unit) {
    val info = GameRegistry.find(id)
    var sheet by remember { mutableStateOf(false) }
    NexBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            NexTopBar(S.t(id), onBack = onBack)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameCard(info?.emoji ?: "🎮", S.t(id), S.t("tagline"), info?.colors ?: NexColors.CardCheckers, Modifier.fillMaxWidth()) {}
                GlowButton(S.t("play_ai"), { sheet = true }, Modifier.fillMaxWidth())
                GlowButton(S.t("play_local"), { onStart("local", 1) }, Modifier.fillMaxWidth(), colors = listOf(NexColors.Cyan, NexColors.Violet))
                if (info?.onlineSupported == true) {
                    GlowButton(S.t("play_online"), onOnline, Modifier.fillMaxWidth(), colors = listOf(NexColors.Pink, NexColors.Violet))
                }
            }
        }
        if (sheet) {
            ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = NexColors.Surface) {
                Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(S.t("difficulty"), color = NexColors.Text)
                    val levels = listOf(S.t("easy"), S.t("medium"), S.t("hard"), if (id == "checkers") S.t("expert_soni") else S.t("expert"))
                    levels.forEachIndexed { i, l ->
                        OutlineButton(l, { sheet = false; onStart("ai", i) }, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
