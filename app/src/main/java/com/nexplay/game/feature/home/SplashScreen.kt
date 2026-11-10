package com.nexplay.game.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.core.ui.BrandText
import com.nexplay.game.core.ui.NexBackground
import com.nexplay.game.core.ui.NexColors
import com.nexplay.game.data.S
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.2f) }
    val fade = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
            launch { fade.animateTo(1f, tween(900)) }
            launch { bar.animateTo(1f, tween(1500, easing = LinearEasing)) }
        }
        onDone()
    }
    NexBackground {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🎮", fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
            Spacer(Modifier.height(10.dp))
            BrandText("NEXPLAY", 46, Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
            Spacer(Modifier.height(8.dp))
            Text(S.t("tagline").uppercase(), color = NexColors.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.alpha(fade.value))
            Spacer(Modifier.height(48.dp))
            Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(NexColors.SurfaceHi)) {
                Box(Modifier.fillMaxWidth(bar.value).fillMaxHeight().background(NexColors.Brand))
            }
            Spacer(Modifier.height(8.dp))
            Text(S.t("tap_loading"), color = NexColors.TextDim, fontSize = 11.sp, modifier = Modifier.alpha(fade.value))
        }
    }
}
