package com.nexplay.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nexplay.game.core.nav.NexNav
import com.nexplay.game.core.ui.NexTheme
import com.nexplay.game.feature.home.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexTheme {
                var splash by rememberSaveable { mutableStateOf(true) }
                Crossfade(targetState = splash, label = "splash") { showSplash ->
                    if (showSplash) SplashScreen { splash = false } else NexNav()
                }
            }
        }
    }
}
