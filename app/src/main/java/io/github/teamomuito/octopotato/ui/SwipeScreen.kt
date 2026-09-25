package io.github.teamomuito.octopotato.ui

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.MediaEntry
import io.github.teamomuito.octopotato.data.MonthSummary
import io.github.teamomuito.octopotato.data.MonthView
import io.github.teamomuito.octopotato.ui.theme.Pastel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun SwipeScreen(vm: SwipeViewModel, onSettings: () -> Unit) {
    val open by vm.openMonth.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler(enabled = open != null) { vm.close() }
    val delete = rememberDeleter(vm)

    AnimatedContent(
        targetState = open,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "swipe",
    ) { month ->
        if (month == null) MonthList(vm, onSettings) else MonthDeck(vm, month, delete)
    }
}

/**
 * Deleting other apps' photos always goes through a system request. With "media management"
 * access it's approved on the spot, otherwise Android asks once for the whole batch.
 */
@Composable
private fun rememberDeleter(vm: SwipeViewModel): (List<MediaEntry>) -> Unit {
    val context = LocalContext.current
    val skipTrash by vm.skipTrash.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val done = pending
        pending = emptyList()
        if (result.resultCode == Activity.RESULT_OK && done.isNotEmpty()) {
            vm.deleted(done)
            Toast.makeText(context, "poof! ${formatBytes(context, done.sumOf { it.size })} freed", Toast.LENGTH_SHORT).show()
        }
    }
    return { entries ->
        if (entries.isNotEmpty() && pending.isEmpty()) {
            pending = entries
            val uris = entries.map { Uri.parse(it.uri) }
            runCatching {
                val request = if (skipTrash) {
                    MediaStore.createDeleteRequest(context.contentResolver, uris)
                } else {
                    MediaStore.createTrashRequest(context.contentResolver, uris, true)
                }
                launcher.launch(IntentSenderRequest.Builder(request).build())
            }.onFailure { pending = emptyList() }
        }
    }
}

@Composable
private fun MonthList(vm: SwipeViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val months by vm.months.collectAsStateWithLifecycle()
    val freed by vm.freed.collectAsStateWithLifecycle()
    val videos by vm.canSeeVideos.collectAsStateWithLifecycle()
    val askVideos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            Potato(boxSize = 60.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("swipe cleanup", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "right to keep, left to say bye. one month at a time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "settings")
            }
        }

        FreedBanner(
            freed = if (freed.bytes > 0) formatBytes(context, freed.bytes) else null,
            items = freed.items,
        )

        if (!videos) {
            NoteCard(
                title = "videos too?",
                body = "they're usually the big ones. let potato see them and they show up here.",
                action = "allow",
                onAction = { askVideos.launch(Access.videoRequest()) },
            )
        }

        val list = months
        when {
            list == null -> EmptyState("counting your photos…")
            list.isEmpty() -> EmptyState("no photos or videos yet.\nnothing to clean, nice.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.month.toString() }) { month ->
                    MonthRow(month) { vm.open(month.month) }
                }
            }
        }
    }
}

@Composable
private fun FreedBanner(freed: String?, items: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (freed == null) {
                Text("nothing freed yet", style = MaterialTheme.typography.titleLarge)
                Text("pick a month and start swiping.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("$freed freed", style = MaterialTheme.typography.headlineMedium)
                Text("${plural(items, "photo or video", "photos and videos")} said bye so far", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MonthRow(month: MonthSummary, onClick: () -> Unit) {
    val context = LocalContext.current
    val counts = buildList {
        if (month.photos > 0) add(plural(month.photos, "photo"))
        if (month.videos > 0) add(plural(month.videos, "video"))
        add(formatBytes(context, month.bytes))
    }.joinToString(" · ")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(monthLabel(month.month), style = MaterialTheme.typography.titleMedium)
                Text(counts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (month.reviewed > 0 && !month.done) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { month.reviewed / month.total.toFloat() },
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            when {
                month.toDelete > 0 -> Pill("${month.toDelete} to delete", Pastel.butter, Pastel.butterInk)
                month.done -> Pill("done", Pastel.mint, Pastel.mintInk)
                month.reviewed > 0 -> Pill("${month.reviewed * 100 / month.total}%", Pastel.lavender, Pastel.lavenderInk)
            }
        }
    }
}

@Composable
private fun MonthDeck(vm: SwipeViewModel, month: YearMonth, delete: (List<MediaEntry>) -> Unit) {
    val context = LocalContext.current
    val view by vm.month.collectAsStateWithLifecycle()
    val reviewing by vm.reviewing.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    BackHandler(enabled = reviewing) { vm.reviewing.value = false }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // hold on to the last view so the fade-out after "back" doesn't flash a loading state
        var shown by remember { mutableStateOf<MonthView?>(null) }
        view?.takeIf { it.summary.month == month }?.let { shown = it }
        val current = shown
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp)) {
            IconButton(onClick = { vm.close() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back to months")
            }
            Column(Modifier.weight(1f)) {
                Text(monthLabel(month), style = MaterialTheme.typography.titleLarge)
                if (current != null) {
                    Text(
                        if (current.deck.isEmpty()) "all swiped" else "${current.deck.size} to go",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (current != null && current.marked.isNotEmpty()) {
                MarkedChip(current.summary.toDeleteBytes, current.marked.size) { vm.reviewing.value = true }
            }
        }

        when {
            current == null -> EmptyState("getting the photos…")
            reviewing || current.deck.isEmpty() -> ReviewPile(
                view = current,
                finished = current.deck.isEmpty(),
                onRescue = vm::rescue,
                onDelete = { delete(current.marked) },
                onLeave = { if (current.deck.isEmpty()) vm.close() else vm.reviewing.value = false },
            )
            else -> CardStack(
                deck = current.deck,
                canUndo = canUndo,
                onDecide = vm::decide,
                onUndo = vm::undo,
                onOpen = { openInGallery(context, Uri.parse(it.uri), it.isVideo) },
            )
        }
    }
}

/** How many bytes are lined up to go. The number rolls up as you swipe left. */
@Composable
private fun MarkedChip(bytes: Long, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val shown by animateFloatAsState(bytes.toFloat(), tween(450), label = "marked")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Rounded.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$count · ${formatBytes(context, shown.toLong())}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Where the top card is. Kept outside the card so the one underneath can react to it. */
private class CardMotion {
    val x = Animatable(0f)
    val y = Animatable(0f)
    var leaving = false

    /** 0 while resting, 1 once it's far enough to count as a swipe. */
    fun progress(width: Float) = (abs(x.value) / (width * THRESHOLD)).coerceIn(0f, 1f)

    suspend fun flyOut(keep: Boolean, width: Float) = coroutineScope {
        launch { y.animateTo(y.value + 80f, tween(230)) }
        x.animateTo(if (keep) width * 1.6f else -width * 1.6f, tween(230))
    }

    suspend fun settle() = coroutineScope {
        launch { y.animateTo(0f, spring(dampingRatio = 0.55f)) }
        x.animateTo(0f, spring(dampingRatio = 0.55f))
    }

    companion object {
        const val THRESHOLD = 0.3f
    }
}

@Composable
private fun CardStack(
    deck: List<MediaEntry>,
    canUndo: Boolean,
    onDecide: (MediaEntry, Boolean) -> Unit,
    onUndo: () -> Unit,
    onOpen: (MediaEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val top = deck.first()
    val motion = remember(top.id) { CardMotion() }
    var width by remember { mutableFloatStateOf(1f) }

    fun decide(keep: Boolean) {
        if (motion.leaving) return
        motion.leaving = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            motion.flyOut(keep, width)
            onDecide(top, keep)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .onSizeChanged { width = it.width.toFloat() },
        ) {
            deck.getOrNull(1)?.let { next ->
                key(next.id) {
                    MediaCard(
                        next,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val grow = 0.93f + 0.07f * motion.progress(width)
                                scaleX = grow
                                scaleY = grow
                            },
                    )
                }
            }
            key(top.id) {
                MediaCard(
                    top,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = motion.x.value
                            translationY = motion.y.value
                            rotationZ = motion.x.value / 45f
                        }
                        .pointerInput(top.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val limit = width * CardMotion.THRESHOLD
                                    when {
                                        motion.x.value > limit -> decide(keep = true)
                                        motion.x.value < -limit -> decide(keep = false)
                                        else -> scope.launch { motion.settle() }
                                    }
                                },
                                onDragCancel = { scope.launch { motion.settle() } },
                            ) { change, drag ->
                                change.consume()
                                scope.launch {
                                    motion.x.snapTo(motion.x.value + drag.x)
                                    motion.y.snapTo(motion.y.value + drag.y)
                                }
                            }
                        },
                    keepStamp = { (motion.x.value / (width * CardMotion.THRESHOLD)).coerceIn(0f, 1f) },
                    byeStamp = { (-motion.x.value / (width * CardMotion.THRESHOLD)).coerceIn(0f, 1f) },
                    onClick = { onOpen(top) },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 16.dp),
        ) {
            RoundButton(Icons.Rounded.Close, "delete", Color(0xFFFFD6E7), Color(0xFFB0265F), 68.dp) { decide(keep = false) }
            RoundButton(Icons.Rounded.Refresh, "undo", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, 48.dp, enabled = canUndo, onClick = onUndo)
            RoundButton(Icons.Rounded.Favorite, "keep", Pastel.mint, Pastel.mintInk, 68.dp) { decide(keep = true) }
        }
    }
}

@Composable
private fun MediaCard(
    entry: MediaEntry,
    modifier: Modifier,
    keepStamp: () -> Float = { 0f },
    byeStamp: () -> Float = { 0f },
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val uri = remember(entry.uri) { Uri.parse(entry.uri) }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Thumbnail(uri, Modifier.fillMaxSize(), big = true, alignment = Alignment.Center)

        if (entry.isVideo) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(12.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 18.dp),
        ) {
            Text(
                DateUtils.formatDateTime(context, entry.taken, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR).lowercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            val details = buildList {
                if (entry.isVideo && entry.durationMs > 0) add(duration(entry.durationMs))
                add(formatBytes(context, entry.size))
            }.joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }

        Stamp(
            "keep",
            Pastel.mintInk,
            Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .graphicsLayer {
                    alpha = keepStamp()
                    rotationZ = -14f
                },
        )
        Stamp(
            "bye",
            Color(0xFFB0265F),
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .graphicsLayer {
                    alpha = byeStamp()
                    rotationZ = 14f
                },
        )
    }
}

@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = color,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .border(BorderStroke(3.dp, color), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    container: Color,
    content: Color,
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
        modifier = Modifier.size(size),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.42f))
    }
}

/** The "marked for deletion" pile. Tap one to rescue it, or send them all off in one go. */
@Composable
private fun ReviewPile(
    view: MonthView,
    finished: Boolean,
    onRescue: (MediaEntry) -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val s = view.summary
    val leave = if (finished) "back to months" else "keep swiping"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Potato(boxSize = 88.dp)
        Text(
            when {
                !finished -> "your bye pile"
                view.marked.isEmpty() -> "${monthLabel(s.month)}, all done!"
                else -> "${monthLabel(s.month)}, all swiped!"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            if (view.marked.isEmpty()) {
                "you kept ${plural(s.kept, "thing")}. potato approves of your taste."
            } else {
                "kept ${s.kept}, saying bye to ${view.marked.size} (${formatBytes(context, s.toDeleteBytes)})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        if (view.marked.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            Text("tap one to keep it after all", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(view.marked, key = { it.id }) { entry ->
                    Box(
                        Modifier
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRescue(entry) },
                    ) {
                        Thumbnail(Uri.parse(entry.uri), Modifier.fillMaxSize(), alignment = Alignment.Center)
                        if (entry.isVideo) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "video",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("delete ${view.marked.size} · ${formatBytes(context, s.toDeleteBytes)}", style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(onClick = onLeave, modifier = Modifier.padding(vertical = 4.dp)) { Text(leave) }
    }
}

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

fun monthLabel(month: YearMonth): String = month.format(monthFormat).lowercase()

private fun duration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
