package com.nexplay.game.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexplay.game.data.Rewards
import com.nexplay.game.data.S
import kotlin.math.sin

val AVATARS = listOf("🦊", "🐯", "🦁", "🐺", "🦅", "🐉", "🤖", "👾")

private val PanelShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
private val ButtonShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

/** Animated "arena" background: drifting neon particles and soft glows. */
@Composable
fun NexBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val tr = rememberInfiniteTransition(label = "bg")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "bgT")
    val pulse by tr.animateFloat(0.55f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "bgP")
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141A44), NexColors.Bg, Color(0xFF060813))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(NexColors.Violet.copy(alpha = 0.30f * pulse), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.08f), size.width * 0.9f),
                size.width * 0.9f, Offset(size.width * 0.15f, size.height * 0.08f)
            )
            drawCircle(
                Brush.radialGradient(listOf(NexColors.Cyan.copy(alpha = 0.18f * pulse), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.85f), size.width * 0.8f),
                size.width * 0.8f, Offset(size.width * 0.95f, size.height * 0.85f)
            )
            for (i in 0 until 26) {
                val sx = ((i * 53) % 100) / 100f
                val speed = 0.35f + ((i * 29) % 10) / 14f
                val y = size.height - (((t * speed) + ((i * 37) % 100) / 100f) % 1f) * size.height
                val x = sx * size.width + sin(t * 6.2832f * 2f + i) * 14f
                val r = 1.2f + (i % 4) * 0.9f
                val c = if (i % 3 == 0) NexColors.Cyan else if (i % 3 == 1) NexColors.Violet else NexColors.Pink
                drawCircle(c.copy(alpha = 0.35f), r * 2.2f, Offset(x, y))
                drawCircle(c.copy(alpha = 0.9f), r, Offset(x, y))
            }
        }
        content()
    }
}

@Composable
fun BrandText(text: String, size: Int = 28, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(brush = NexColors.Brand, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
    )
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color> = listOf(NexColors.Violet, Color(0xFF536DFE))
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "btnScale")
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(if (enabled) 12.dp else 0.dp, ButtonShape, ambientColor = colors.first(), spotColor = colors.first())
            .clip(ButtonShape)
            .background(Brush.horizontalGradient(colors))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.18f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), ButtonShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Brush.horizontalGradient(listOf(NexColors.Violet, NexColors.Cyan)), ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = NexColors.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun NexCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier
        .clip(PanelShape)
        .background(Brush.verticalGradient(listOf(Color(0xE61E2650), Color(0xE6121838))))
        .border(1.dp, Brush.linearGradient(listOf(NexColors.Violet.copy(alpha = 0.6f), NexColors.Cyan.copy(alpha = 0.25f))), PanelShape)
    val m = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(m.padding(16.dp), content = content)
}

@Composable
fun GameCard(emoji: String, title: String, subtitle: String, colors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "cardScale")
    Box(
        modifier
            .heightIn(min = 132.dp)
            .scale(scale)
            .shadow(14.dp, shape, ambientColor = colors.first(), spotColor = colors.first())
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f))))
            .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" }
    ) {
        Text(emoji, fontSize = 104.sp, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 18.dp, y = (-6).dp).alpha(0.30f))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.clip(CutCornerShape(6.dp)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("▶ " + S.t("play").uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Avatar(index: Int, size: Int = 56) {
    val tr = rememberInfiniteTransition(label = "av")
    val a by tr.animateFloat(0.4f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "avA")
    Box(
        Modifier
            .size(size.dp)
            .shadow((size / 5).dp, CircleShape, ambientColor = NexColors.Cyan.copy(alpha = a), spotColor = NexColors.Violet)
            .clip(CircleShape)
            .background(Brush.sweepGradient(listOf(NexColors.Violet, NexColors.Cyan, NexColors.Pink, NexColors.Violet)))
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(NexColors.SurfaceHi)
            .semantics { contentDescription = "Avatar" },
        contentAlignment = Alignment.Center
    ) {
        Text(AVATARS[index.coerceIn(0, AVATARS.lastIndex)], fontSize = (size * 0.5f).sp)
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color = NexColors.Cyan, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, color.copy(alpha = 0.35f), CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label.uppercase(), color = NexColors.TextDim, fontSize = 9.sp, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

/** Small HUD pill, e.g. coins or streak. */
@Composable
fun HudPill(icon: String, value: String, color: Color = NexColors.Amber, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(Color(0x33000000))
            .border(1.dp, color.copy(alpha = 0.6f), CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(5.dp))
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).background(NexColors.Brand))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = NexColors.Text, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
fun NexTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.t("back"), tint = NexColors.Text)
                }
            } else Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), Modifier.weight(1f), color = NexColors.Text, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 2.sp, maxLines = 1)
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, NexColors.Violet, NexColors.Cyan, Color.Transparent))))
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NexColors.Cyan)
            Spacer(Modifier.height(8.dp))
            Text(S.t("loading"), color = NexColors.TextDim)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Warning, contentDescription = S.t("error"), tint = NexColors.Amber, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(6.dp))
        Text(message, color = NexColors.TextDim, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            OutlineButton(S.t("retry"), onRetry)
        }
    }
}

@Composable
fun EmptyBox(message: String, emoji: String = "🎮", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 36.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = NexColors.TextDim, textAlign = TextAlign.Center)
    }
}

/** Falling neon confetti for victory screens. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "cf").animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "cfT")
    val cols = listOf(NexColors.Cyan, NexColors.Violet, NexColors.Pink, NexColors.Amber, NexColors.Green)
    Canvas(modifier.fillMaxSize()) {
        for (i in 0 until 70) {
            val speed = 0.6f + ((i * 13) % 10) / 18f
            val y = (((t * speed) + ((i * 17) % 100) / 100f) % 1f) * size.height
            val x = (((i * 37) % 100) / 100f) * size.width + sin(t * 12.566f + i) * 22f
            drawRect(cols[i % cols.size].copy(alpha = 0.9f), Offset(x, y), Size(9f, 15f))
        }
    }
}

/** Full-screen victory / defeat overlay with rewards. [outcome]: "win" | "loss" | "draw" | null (no rewards, e.g. local game). */
@Composable
fun ResultDialog(title: String, subtitle: String? = null, onNew: (() -> Unit)?, onExit: () -> Unit, outcome: String? = null) {
    val win = outcome == "win" || (outcome == null && title.contains("!"))
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xE6070A18)), contentAlignment = Alignment.Center) {
            if (win) Confetti()
            Column(
                Modifier.padding(28.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (win) "🏆" else if (outcome == "draw") "🤝" else "💀", fontSize = 72.sp)
                Text(
                    title.uppercase(),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        brush = if (win) Brush.linearGradient(listOf(NexColors.Gold, NexColors.Amber, NexColors.Pink)) else Brush.linearGradient(listOf(NexColors.TextDim, NexColors.Text)),
                        fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp
                    )
                )
                if (subtitle != null) Text(subtitle, color = NexColors.TextDim, textAlign = TextAlign.Center)
                if (outcome != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudPill("⭐", "+${Rewards.xp(outcome)} XP", NexColors.Cyan)
                        HudPill("🪙", "+${Rewards.coins(outcome)}", NexColors.Amber)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (onNew != null) GlowButton(S.t("new_game"), onNew, Modifier.fillMaxWidth(0.8f), colors = listOf(NexColors.Violet, NexColors.Cyan))
                OutlineButton(S.t("back"), onExit, Modifier.fillMaxWidth(0.8f))
            }
        }
    }
}

@Composable
fun RefreshIcon() = Icon(Icons.Filled.Refresh, contentDescription = S.t("retry"), tint = NexColors.Text)

/** Pieces "pop" into place with a springy scale. Wrap in key(piece) so it replays on changes. */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    val s = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 450f)) }
    Box(Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }) { content() }
}

/** Game-style bottom navigation: (route, emoji, label). */
@Composable
fun GameNavBar(items: List<Triple<String, String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, NexColors.Violet, NexColors.Cyan, Color.Transparent))))
        Row(
            Modifier.fillMaxWidth().background(Color(0xF00A0E24)).navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for ((route, emoji, label) in items) {
                val on = route == selected
                val sc by animateFloatAsState(if (on) 1.25f else 1f, spring(dampingRatio = 0.5f), label = "nav")
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab) { onSelect(route) }.padding(vertical = 4.dp).semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(emoji, fontSize = 22.sp, modifier = Modifier.scale(sc).alpha(if (on) 1f else 0.55f))
                    Text(label.uppercase(), color = if (on) NexColors.Cyan else NexColors.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Box(Modifier.padding(top = 3.dp).width(if (on) 22.dp else 0.dp).height(3.dp).background(NexColors.Brand))
                }
            }
        }
    }
}

/** 8x8 board made of clickable cells. [content] draws the piece; the Dp is the cell size. */
@Composable
fun SquareBoard(
    flip: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    light: Color = Color(0xFF3B4170),
    dark: Color = Color(0xFF1B2043),
    highlight: (Int) -> Color? = { null },
    description: (Int) -> String = { "" },
    content: @Composable BoxScope.(Int, Dp) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val glow by rememberInfiniteTransition(label = "bd").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "bdG")
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = maxWidth / 8
        val shape = CutCornerShape(14.dp)
        Column(
            Modifier
                .fillMaxSize()
                .shadow(18.dp, shape, ambientColor = NexColors.Violet.copy(alpha = glow), spotColor = NexColors.Cyan.copy(alpha = glow))
                .clip(shape)
                .border(2.dp, Brush.linearGradient(listOf(NexColors.Violet.copy(alpha = glow), NexColors.Cyan.copy(alpha = glow))), shape)
        ) {
            for (dr in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (dc in 0..7) {
                        val r = if (flip) 7 - dr else dr
                        val c = if (flip) 7 - dc else dc
                        val sq = r * 8 + c
                        val base = if ((r + c) % 2 == 0) light else dark
                        val hl = highlight(sq)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(base)
                                .background(hl ?: Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTap(sq)
                                }
                                .semantics { contentDescription = description(sq) },
                            contentAlignment = Alignment.Center
                        ) { content(sq, cell) }
                    }
                }
            }
        }
    }
}
