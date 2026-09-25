package io.github.teamomuito.octopotato.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.DateUtils
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.AppRules
import io.github.teamomuito.octopotato.data.AppUsage
import io.github.teamomuito.octopotato.data.Expiry
import io.github.teamomuito.octopotato.data.JunkItem
import io.github.teamomuito.octopotato.data.JunkKind
import io.github.teamomuito.octopotato.ui.theme.GlassCard
import io.github.teamomuito.octopotato.ui.theme.LocalGlass
import io.github.teamomuito.octopotato.ui.theme.bottomSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanScreen(vm: CleanViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allFiles by vm.hasAllFiles.collectAsStateWithLifecycle()
    val usage by vm.hasUsage.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val cleaned by vm.cleaned.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var confirming by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Android's own "clear all app caches" screen. It only opens for apps with all files access.
    var cacheBefore by remember { mutableLongStateOf(0L) }
    val clearCaches = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.afterCacheClear(cacheBefore)
    }
    var uninstalling by remember { mutableStateOf<String?>(null) }
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        uninstalling?.let(vm::afterUninstall)
        uninstalling = null
    }

    val report = (scan as? ScanState.Done)?.report
    val picked = report?.items?.filter { it.path in selected }.orEmpty()
    val pickedBytes = picked.sumOf { it.bytes }
    val appList = apps
    val cached = appList?.let { AppRules.byCache(it) }.orEmpty()
    val unused = appList?.let { AppRules.unused(it, System.currentTimeMillis()) }.orEmpty()
    fun toggleOpen(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            Potato(boxSize = 60.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("deep clean", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (cleaned > 0) "${formatBytes(context, cleaned)} cleaned so far" else "caches, thumbnails and forgotten stuff",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (allFiles) {
                IconButton(onClick = vm::scan, enabled = scan !is ScanState.Scanning && !working) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "scan again")
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "settings")
            }
        }

        val showButton = picked.isNotEmpty() || working
        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 16.dp + bottomSpace() + if (showButton) 76.dp else 0.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!allFiles) {
                    item {
                        Ask(
                            title = "let potato look around",
                            body = "to find thumbnails, empty folders, leftover cache and big old files, " +
                                "potato needs \"all files access\". everything stays on your phone.",
                        ) { Access.openAllFilesSettings(context) }
                    }
                }
                if (!usage) {
                    item {
                        Ask(
                            title = "see which apps hog space",
                            body = "\"usage access\" lets potato see app and cache sizes, and which apps you haven't opened in ages.",
                        ) { Access.openUsageAccessSettings(context) }
                    }
                }

                if (usage) {
                    item(key = "caches") {
                        AppCacheCard(
                            apps = cached,
                            loading = appList == null,
                            canClear = allFiles,
                            open = "caches" in expanded,
                            onOpen = { toggleOpen("caches") },
                            onClearAll = {
                                cacheBefore = cached.sumOf { it.cacheBytes }
                                val clear = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
                                runCatching { clearCaches.launch(clear) }.onFailure {
                                    Toast.makeText(context, "android didn't let potato do that, opening storage settings", Toast.LENGTH_LONG).show()
                                    runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                                }
                            },
                            onApp = { openAppInfo(context, it.pkg) },
                        )
                    }
                }

                when (val s = scan) {
                    is ScanState.Scanning -> item(key = "scanning") { Scanning(s.files) }
                    is ScanState.Done -> {
                        for (kind in listOf(JunkKind.CACHE, JunkKind.THUMBNAILS, JunkKind.EMPTY, JunkKind.LARGE)) {
                            val items = s.report.of(kind)
                            item(key = kind.name) {
                                JunkCard(
                                    kind = kind,
                                    items = items,
                                    selected = selected,
                                    open = kind.name in expanded,
                                    onOpen = { toggleOpen(kind.name) },
                                    onAll = { on -> vm.setAll(items, on) },
                                    onToggle = vm::toggle,
                                )
                            }
                        }
                    }
                    ScanState.Idle -> Unit
                }

                if (usage && appList != null) {
                    item(key = "unused") {
                        UnusedAppsCard(
                            apps = unused,
                            open = "unused" in expanded,
                            onOpen = { toggleOpen("unused") },
                            onUninstall = { app ->
                                uninstalling = app.pkg
                                runCatching { uninstall.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.pkg, null))) }
                                    .onFailure { uninstalling = null }
                            },
                        )
                    }
                }
            }

            // floats just above the tab bar, over the list
            if (showButton) {
                Button(
                    onClick = { confirming = true },
                    enabled = !working,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomSpace() + 4.dp, start = 24.dp, end = 24.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(50)),
                ) {
                    if (working) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("cleaning…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("clean ${plural(picked.size, "thing")} · ${formatBytes(context, pickedBytes)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("clean up?") },
            text = {
                Text(
                    "this deletes ${plural(picked.size, "thing")} (${formatBytes(context, pickedBytes)}) for good. " +
                        "there's no trash for these, so have a quick look at the lists first if you're not sure.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        val freed = vm.clean()
                        Toast.makeText(context, "poof! ${formatBytes(context, freed)} cleaned", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("clean") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("not yet") } },
        )
    }
}

@Composable
private fun Ask(title: String, body: String, onAllow: () -> Unit) {
    GlassCard(
        tint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (LocalGlass.current.dark) 0.45f else 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAllow) { Text("allow") }
        }
    }
}

@Composable
private fun Scanning(files: Int) {
    Section {
        Text("looking around…", style = MaterialTheme.typography.titleMedium)
        Text(
            "${"%,d".format(files)} files checked so far",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SectionHeader(title: String, body: String, size: String, open: Boolean, onOpen: () -> Unit, leading: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(size, style = MaterialTheme.typography.labelLarge)
        Icon(
            if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (open) "hide" else "show",
        )
    }
}

private fun kindText(kind: JunkKind): Pair<String, String> = when (kind) {
    JunkKind.CACHE -> "leftover cache" to
        "scratch folders and temp files apps left in your storage. apps make new ones when they need them."
    JunkKind.THUMBNAILS -> "thumbnails" to
        "little preview copies your gallery made. they come back on their own if they're needed."
    JunkKind.EMPTY -> "empty folders" to
        "folders with nothing in them, usually left behind by apps you removed."
    JunkKind.LARGE -> "big files you haven't touched" to
        "over 50 MB and unchanged for 3 months. nothing here is ticked for you, pick what can go."
}

@Composable
private fun JunkCard(
    kind: JunkKind,
    items: List<JunkItem>,
    selected: Set<String>,
    open: Boolean,
    onOpen: () -> Unit,
    onAll: (Boolean) -> Unit,
    onToggle: (JunkItem) -> Unit,
) {
    val context = LocalContext.current
    val (title, body) = kindText(kind)
    val ticked = items.count { it.path in selected }
    val state = when {
        items.isEmpty() || ticked == 0 -> ToggleableState.Off
        ticked == items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val size = if (items.isEmpty()) "none" else formatBytes(context, items.sumOf { it.bytes })

    Section {
        SectionHeader(
            title = if (items.isEmpty()) title else "$title · ${items.size}",
            body = body,
            size = size,
            open = open,
            onOpen = onOpen,
            leading = {
                TriStateCheckbox(
                    state = state,
                    enabled = items.isNotEmpty(),
                    onClick = { onAll(state != ToggleableState.On) },
                )
            },
        )
        if (open) {
            if (items.isEmpty()) {
                Text("nothing here, all clean.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            }
            val shown = items.sortedByDescending { it.bytes }.take(SHOW_MAX)
            for (item in shown) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(item) },
                ) {
                    Checkbox(checked = item.path in selected, onCheckedChange = { onToggle(item) })
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            detail(context, item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(context, item.bytes), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (items.size > SHOW_MAX) {
                Text(
                    "and ${items.size - SHOW_MAX} more, all ticked the same way as this box",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                )
            }
        }
    }
}

private const val SHOW_MAX = 150

private fun detail(context: Context, item: JunkItem): String {
    val where = item.where.ifEmpty { "main storage" }
    return when {
        item.kind == JunkKind.LARGE -> "$where · changed ${ago(item.modified)}"
        item.isDir && item.files > 0 -> "$where · ${plural(item.files, "file")}"
        else -> where
    }
}

private fun ago(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString().lowercase()

@Composable
private fun AppCacheCard(
    apps: List<AppUsage>,
    loading: Boolean,
    canClear: Boolean,
    open: Boolean,
    onOpen: () -> Unit,
    onClearAll: () -> Unit,
    onApp: (AppUsage) -> Unit,
) {
    val context = LocalContext.current
    val total = apps.sumOf { it.cacheBytes }
    Section {
        SectionHeader(
            title = "app caches",
            body = "hidden cache inside your apps, plus what they keep in their own storage folders. android clears all of it in one go.",
            size = if (loading) "…" else formatBytes(context, total),
            open = open,
            onOpen = onOpen,
        )
        Spacer(Modifier.height(10.dp))
        if (canClear) {
            FilledTonalButton(onClick = onClearAll, enabled = !loading && total > 0) { Text("clear all app caches") }
        } else {
            Text(
                "clearing every app's cache at once needs \"all files access\" too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(
                "tap an app to clear just that one from its settings page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (app in apps.take(40)) {
                AppRow(app, detail = formatBytes(context, app.cacheBytes), onClick = { onApp(app) })
            }
        }
    }
}

@Composable
private fun UnusedAppsCard(apps: List<AppUsage>, open: Boolean, onOpen: () -> Unit, onUninstall: (AppUsage) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    Section {
        SectionHeader(
            title = if (apps.isEmpty()) "apps you don't use" else "apps you don't use · ${apps.size}",
            body = "not opened in over a month. android asks before anything is uninstalled.",
            size = if (apps.isEmpty()) "none" else formatBytes(context, apps.sumOf { it.bytes }),
            open = open,
            onOpen = onOpen,
        )
        val shown = if (open) apps else apps.take(3)
        for (app in shown) {
            val last = app.lastUsed
            val used = when {
                last != null -> "opened ${ago(last)}"
                now - app.installed < 365 * Expiry.DAY_MS -> "never opened"
                else -> "not opened in a year"
            }
            AppRow(app, detail = "${formatBytes(context, app.bytes)} · $used") {
                TextButton(onClick = { onUninstall(app) }) { Text("uninstall") }
            }
        }
        if (!open && apps.size > 3) {
            TextButton(onClick = onOpen) { Text("show all ${apps.size}") }
        }
    }
}

@Composable
private fun AppRow(app: AppUsage, detail: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        AppIcon(app.pkg, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

private val icons = LruCache<String, ImageBitmap>(120)

@Composable
private fun AppIcon(pkg: String, modifier: Modifier) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(icons.get(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()?.also { icons.put(pkg, it) }
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private fun openAppInfo(context: Context, pkg: String) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))) }
}
