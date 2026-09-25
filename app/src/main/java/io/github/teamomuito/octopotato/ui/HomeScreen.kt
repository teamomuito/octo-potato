package io.github.teamomuito.octopotato.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.Expiry
import io.github.teamomuito.octopotato.data.Progress
import io.github.teamomuito.octopotato.data.Shot
import io.github.teamomuito.octopotato.data.TidySettings
import io.github.teamomuito.octopotato.ui.theme.Pastel

@Composable
fun HomeScreen(
    vm: MainViewModel,
    due: List<Shot>,
    onOpen: (Shot) -> Unit,
    onSettings: () -> Unit,
    onTidy: () -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val tidy by vm.tidy.collectAsStateWithLifecycle()
    val askAgain = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Header(progress, onSettings)
        SearchBox(query) { vm.query.value = it }
        FilterRow(filter, showSoon = tidy.enabled) { vm.filter.value = it }

        if (access == Access.Level.PARTIAL) {
            NoteCard(
                title = "potato can only see the photos you picked",
                body = "let it see all of them and every screenshot becomes searchable.",
                action = "allow all",
                onAction = { askAgain.launch(Access.toRequest()) },
            )
        }
        if (due.isNotEmpty()) {
            NoteCard(
                title = "${plural(due.size, "screenshot")} ready to tidy",
                body = "temporary ones older than ${Expiry.spanLabel(tidy.days)}. they go to your trash first, just in case.",
                action = "tidy up",
                onAction = onTidy,
            )
        }

        Box(Modifier.weight(1f)) {
            val p = progress
            when {
                p == null -> Unit
                p.total == 0 -> EmptyState("no screenshots yet.\ngo take one, potato will wait.")
                results.shots.isEmpty() && query.isNotBlank() -> EmptyState("nothing for “${query.trim()}”.\npotato checked twice.")
                results.shots.isEmpty() -> EmptyState(emptyFilterText(filter))
                results.searching -> ResultList(results.shots, onOpen)
                else -> ShotGrid(results.shots, tidy, onOpen)
            }
        }
    }
}

@Composable
private fun Header(progress: Progress?, onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp),
    ) {
        Potato(boxSize = 60.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("octo potato", style = MaterialTheme.typography.headlineSmall)
            Text(
                statusLine(progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "settings")
        }
    }
    if (progress != null && progress.total > 0 && !progress.done) {
        LinearProgressIndicator(
            progress = { progress.read / progress.total.toFloat() },
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(50)),
        )
    }
}

private fun statusLine(p: Progress?): String = when {
    p == null -> "waking up…"
    p.total == 0 -> "nothing to read yet"
    !p.done -> "reading… ${p.read} of ${p.total}"
    else -> "${plural(p.total, "screenshot")} read"
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("search your screenshots") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "clear") } }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilterRow(selected: Filter, showSoon: Boolean, onPick: (Filter) -> Unit) {
    val filters = Filter.entries.filter { it != Filter.SOON || showSoon }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onPick(f) },
                label = { Text(f.label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}


@Composable
private fun ShotGrid(shots: List<Shot>, tidy: TidySettings, onOpen: (Shot) -> Unit) {
    val now = System.currentTimeMillis()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shots, key = { it.id }) { shot ->
            Box(
                Modifier
                    .aspectRatio(0.6f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(shot) },
            ) {
                Thumbnail(shot.uri, Modifier.fillMaxSize())
                KindPill(shot.kind, Modifier.align(Alignment.TopStart).padding(6.dp))
                val leaving = leavingLabel(shot, tidy, now)
                if (leaving != null) {
                    Pill(leaving, Pastel.butter, Pastel.butterInk, Modifier.align(Alignment.BottomCenter).padding(6.dp))
                }
            }
        }
    }
}

private fun leavingLabel(shot: Shot, tidy: TidySettings, now: Long): String? {
    if (!tidy.covers(shot.kind)) return null
    if (shot.keep) return "kept"
    return Expiry.label(Expiry.daysLeft(Expiry.at(shot.taken, shot.found, tidy.days), now))
}

@Composable
private fun ResultList(shots: List<Shot>, onOpen: (Shot) -> Unit) {
    val hit = hitStyle(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shots, key = { it.id }) { shot ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onOpen(shot) }
                    .padding(10.dp),
            ) {
                Thumbnail(
                    shot.uri,
                    Modifier
                        .size(width = 64.dp, height = 108.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            whenTaken(shot.taken),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        KindPill(shot.kind)
                    }
                    Spacer(Modifier.height(4.dp))
                    // a blank snippet means the hit was in the file name or kind, not in the words
                    Text(
                        highlighted(shot.snippet?.takeIf { it.isNotBlank() } ?: "found by its file name", hit),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun emptyFilterText(filter: Filter): String = when (filter) {
    Filter.QR -> "no qr codes spotted."
    Filter.BOARDING -> "no boarding passes.\ntime to book a trip?"
    Filter.CODE -> "no login codes lying around."
    Filter.SOON -> "nothing is leaving soon.\nall calm down here."
    Filter.ALL -> "no screenshots yet."
}

