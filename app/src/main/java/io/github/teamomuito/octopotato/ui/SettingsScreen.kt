package io.github.teamomuito.octopotato.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.octopotato.BuildConfig

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val tidy by vm.tidy.collectAsStateWithLifecycle()
    val silent by vm.canTidySilently.collectAsStateWithLifecycle()
    val canNotify by vm.canNotify.collectAsStateWithLifecycle()
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    var rereadStarted by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
                }
                Text("settings", style = MaterialTheme.typography.headlineSmall)
            }

            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("tidy up temporary screenshots", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "qr codes, boarding passes and login codes go to the trash after a while. " +
                                "anything you mark as keep stays put.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = tidy.enabled, onCheckedChange = { on -> vm.updateTidy { it.copy(enabled = on) } })
                }
                if (tidy.enabled) {
                    Spacer(Modifier.height(14.dp))
                    Text("after", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        for ((days, label) in listOf(1 to "1 day", 3 to "3 days", 7 to "1 week", 14 to "2 weeks")) {
                            FilterChip(
                                selected = tidy.days == days,
                                onClick = { vm.updateTidy { it.copy(days = days) } },
                                label = { Text(label) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    CheckRow("qr codes", tidy.qr) { on -> vm.updateTidy { it.copy(qr = on) } }
                    CheckRow("boarding passes", tidy.boarding) { on -> vm.updateTidy { it.copy(boarding = on) } }
                    CheckRow("login and verification codes", tidy.codes) { on -> vm.updateTidy { it.copy(codes = on) } }
                }
            }

            if (Build.VERSION.SDK_INT >= 31) {
                Section {
                    Text("tidy without asking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (silent) {
                            "on. when you open the app, potato moves anything that's due to the trash by itself."
                        } else {
                            "android normally asks before an app moves files to the trash. " +
                                "give potato \"media management\" access and it can do it without the popup."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!silent) {
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { openMediaManagementSettings(context) }) { Text("allow") }
                    }
                }
            }

            if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                Section {
                    Text("reminders", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "a little notification when there's stuff ready to tidy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("turn on") }
                }
            }

            Section {
                Text("read everything again", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (rereadStarted) "on it. this takes a bit if you have lots of screenshots."
                    else "handy if search seems off. your keep choices are remembered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !rereadStarted,
                    onClick = {
                        rereadStarted = true
                        vm.readAgain()
                    },
                ) { Text("start over") }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, start = 32.dp, end = 32.dp),
            ) {
                Potato(boxSize = 96.dp)
                Spacer(Modifier.height(8.dp))
                Text("octo potato ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "your screenshots and the words in them stay on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
