package io.github.teamomuito.octopotato.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.octopotato.data.Expiry
import io.github.teamomuito.octopotato.data.Kind
import io.github.teamomuito.octopotato.data.Shot
import io.github.teamomuito.octopotato.data.TidySettings

@Composable
fun DetailScreen(id: Long, vm: MainViewModel, onBack: () -> Unit, onTrash: (Shot) -> Unit) {
    val detail by remember(id) { vm.detail(id) }.collectAsStateWithLifecycle(initialValue = null)
    val tidy by vm.tidy.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
                }
                Text(
                    detail?.let { whenTaken(it.shot.taken) } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            detail?.let { DetailBody(it, tidy, vm, onTrash) }
        }
    }
}

@Composable
private fun DetailBody(detail: Detail, tidy: TidySettings, vm: MainViewModel, onTrash: (Shot) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shot = detail.shot

    FullImage(
        shot.uri,
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { openInGallery(context, shot.uri) },
    )

    if (shot.kind.temporary) {
        Spacer(Modifier.height(12.dp))
        TemporaryCard(shot, tidy, onKeep = { vm.setKeep(shot.id, it) })
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FilledTonalButton(onClick = { share(context, shot.uri) }) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("share")
        }
        OutlinedButton(onClick = { onTrash(shot) }) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("trash")
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
        Text("what potato read", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (detail.text.isNotBlank()) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(detail.text))
                Toast.makeText(context, "copied", Toast.LENGTH_SHORT).show()
            }) { Text("copy all") }
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        val text = when {
            detail.text.isNotBlank() -> detail.text
            !shot.read -> "potato hasn't gotten to this one yet. give it a minute."
            else -> "no words in this one. just vibes."
        }
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun TemporaryCard(shot: Shot, tidy: TidySettings, onKeep: (Boolean) -> Unit) {
    val what = when (shot.kind) {
        Kind.QR -> "a qr code"
        Kind.BOARDING -> "a boarding pass"
        else -> "a login code"
    }
    val status = when {
        shot.keep -> "you're keeping it, potato won't touch it."
        !tidy.covers(shot.kind) -> "auto tidy is off for these, so it stays."
        else -> {
            val left = Expiry.daysLeft(Expiry.at(shot.taken, shot.found, tidy.days), System.currentTimeMillis())
            if (left <= 0) "its time is up, it goes in the next tidy." else "${Expiry.label(left)}, then off to the trash."
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("looks like $what", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = shot.keep, onCheckedChange = onKeep)
                Text("keep", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
