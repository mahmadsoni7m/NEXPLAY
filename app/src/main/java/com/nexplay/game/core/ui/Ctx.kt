package com.nexplay.game.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nexplay.game.NexPlayApp
import com.nexplay.game.data.Repository

@Composable
fun rememberRepo(): Repository = (LocalContext.current.applicationContext as NexPlayApp).repo
