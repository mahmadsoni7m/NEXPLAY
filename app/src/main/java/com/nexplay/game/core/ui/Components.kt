package com.nexplay.game.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.game.data.S

val AVATARS = listOf("🦊", "🐯", "🦁", "🐺", "🦅", "🐉", "🤖", "👾")

@Composable
fun NexBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF12183A), NexColors.Bg, Color(0xFF080B16)))),
        content = content
    )
}

@Composable
fun BrandText(text: String, size: Int = 28, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(brush = NexColors.Brand, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
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
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "btnScale")
    Box(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(14.dp), ambientColor = colors.first(), spotColor = colors.first())
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, NexColors.Violet.copy(alpha = 0.7f)), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = NexColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun NexCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier
        .clip(RoundedCornerShape(18.dp))
        .background(NexColors.Surface.copy(alpha = 0.92f))
        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
    val m = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(m.padding(16.dp), content = content)
}

@Composable
fun GameCard(emoji: String, title: String, subtitle: String, colors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = colors.first(), spotColor = colors.first())
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" }
            .padding(16.dp)
    ) {
        Column {
            Text(emoji, fontSize = 38.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
fun Avatar(index: Int, size: Int = 56) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(NexColors.Violet, NexColors.Cyan)))
            .padding(2.dp)
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, color = NexColors.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = NexColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
fun NexTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.t("back"), tint = NexColors.Text)
            }
        } else Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), color = NexColors.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        actions()
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

@Composable
fun ResultDialog(title: String, subtitle: String? = null, onNew: (() -> Unit)?, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = NexColors.Surface,
        title = { Text(title, color = NexColors.Text, fontWeight = FontWeight.Black) },
        text = { if (subtitle != null) Text(subtitle, color = NexColors.TextDim) },
        confirmButton = { if (onNew != null) TextButton(onClick = onNew) { Text(S.t("new_game"), color = NexColors.Cyan) } },
        dismissButton = { TextButton(onClick = onExit) { Text(S.t("back"), color = NexColors.TextDim) } }
    )
}

@Composable
fun RefreshIcon() = Icon(Icons.Filled.Refresh, contentDescription = S.t("retry"), tint = NexColors.Text)

/** 8x8 board made of clickable cells. [content] draws the piece; [cell] is the cell size. */
@Composable
fun SquareBoard(
    flip: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    light: Color = Color(0xFF3B4170),
    dark: Color = Color(0xFF1B2043),
    highlight: (Int) -> Color? = { null },
    description: (Int) -> String = { "" },
    content: @Composable BoxScope.(Int, androidx.compose.ui.unit.Dp) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = maxWidth / 8
        Column(
            Modifier
                .fillMaxSize()
                .shadow(14.dp, RoundedCornerShape(12.dp), ambientColor = NexColors.Violet, spotColor = NexColors.Violet)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, NexColors.Violet.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
                                .clickable { onTap(sq) }
                                .semantics { contentDescription = description(sq) },
                            contentAlignment = Alignment.Center
                        ) { content(sq, cell) }
                    }
                }
            }
        }
    }
}
