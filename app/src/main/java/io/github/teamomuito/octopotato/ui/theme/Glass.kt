package io.github.teamomuito.octopotato.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The liquid glass look. Panels are translucent with a bright rim and a soft sheen on top,
 * floating over a blurry wash of color. Only the tab bar does real backdrop blur (it's the
 * one thing content scrolls under); everything else fakes it, which keeps scrolling smooth.
 */
@Immutable
data class Glass(
    val fill: Color,
    val rim: Brush,
    val sheen: Brush,
    val blur: Color,
    val dark: Boolean,
)

val LightGlass = Glass(
    fill = Color.White.copy(alpha = 0.52f),
    rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.75f))),
    sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.38f), 0.5f to Color.Transparent),
    blur = Color.White.copy(alpha = 0.3f),
    dark = false,
)

val DarkGlass = Glass(
    fill = Color.White.copy(alpha = 0.07f),
    rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.18f))),
    sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.09f), 0.5f to Color.Transparent),
    blur = Color.White.copy(alpha = 0.06f),
    dark = true,
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

/** How much room the floating tab bar takes at the bottom, so lists can scroll clear of it. */
val LocalBarSpace = compositionLocalOf { 0.dp }

@Composable
fun bottomSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalBarSpace.current

fun Modifier.glass(shape: Shape, glass: Glass, tint: Color? = null): Modifier =
    clip(shape)
        .background(tint ?: glass.fill)
        .background(glass.sheen)
        .border(1.dp, glass.rim, shape)

/** Clickable that squishes a little while pressed. */
@Composable
private fun Modifier.applySquish(enabled: Boolean, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "squish")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Squishy(modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.applySquish(enabled, onClick), contentAlignment = Alignment.Center) { content() }
}

/** A glass panel. Pass [onClick] and it squishes when pressed. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val base = if (onClick != null) modifier.applySquish(true, onClick) else modifier
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(base.glass(shape, glass, tint), content = content)
    }
}

private data class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

private val lightBlobs = listOf(
    Blob(Color(0xFFFFB8D6), 0.05f, 0.04f, 1.0f),
    Blob(Color(0xFFD9CCFF), 1.05f, 0.34f, 0.9f),
    Blob(Color(0xFFC6F2E3), -0.05f, 0.7f, 0.85f),
    Blob(Color(0xFFFFDCC8), 0.95f, 1.0f, 0.85f),
)

private val darkBlobs = listOf(
    Blob(Color(0xFF6A2A4C), 0.05f, 0.04f, 1.0f),
    Blob(Color(0xFF3B2B70), 1.05f, 0.34f, 0.9f),
    Blob(Color(0xFF1C4A42), -0.05f, 0.7f, 0.85f),
    Blob(Color(0xFF5B2350), 0.95f, 1.0f, 0.85f),
)

/** Soft color blobs everything floats on. Static on purpose: nothing to redraw while scrolling. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val blobs = if (LocalGlass.current.dark) darkBlobs else lightBlobs
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        for (b in blobs) {
            val center = Offset(size.width * b.x, size.height * b.y)
            val radius = size.width * b.radius
            drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

/**
 * The floating tab bar: real frosted glass over whatever scrolls behind it, with a pill
 * that slides to the selected tab.
 */
@Composable
fun GlassTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, haze: HazeState, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(32.dp)
    val style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tints = listOf(HazeTint(glass.blur)),
        blurRadius = 22.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
    )
    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(shape)
            .hazeEffect(haze, style)
            .background(glass.sheen)
            .border(1.dp, glass.rim, shape),
    ) {
        val slot = maxWidth / tabs.size
        val x by animateDpAsState(slot * selected, spring(dampingRatio = 0.7f, stiffness = 420f), label = "tab")
        Box(
            Modifier
                .offset(x = x)
                .width(slot)
                .fillMaxHeight()
                .padding(6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (glass.dark) 0.55f else 0.8f)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Squishy(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onSelect(i) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
                    }
                }
            }
        }
    }
}

/** Height of the bar plus its margins, for [LocalBarSpace]. */
val BAR_HEIGHT = 64.dp
val BAR_SPACE = BAR_HEIGHT + 24.dp

/** A round glass button, for the swipe card controls. */
@Composable
fun GlassCircle(
    icon: ImageVector,
    description: String,
    tint: Color,
    ink: Color,
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Squishy(
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .glass(CircleShape, glass, tint.copy(alpha = if (glass.dark) 0.35f else 0.75f)),
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(icon, contentDescription = description, tint = ink, modifier = Modifier.size(size * 0.42f))
    }
}
