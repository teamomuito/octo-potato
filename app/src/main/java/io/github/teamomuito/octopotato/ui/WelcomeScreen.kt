package io.github.teamomuito.octopotato.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.ui.theme.LiquidBackground

@Composable
fun WelcomeScreen(onAnswered: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        onAnswered()
    }
    // After "don't allow" twice, Android stops showing the dialog, so point to settings instead.
    val activity = context as? Activity
    val blocked = asked && activity != null && !activity.shouldShowRequestPermissionRationale(Access.mediaPermission())

    Box(Modifier.fillMaxSize()) {
        LiquidBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Potato(boxSize = 190.dp)
            Spacer(Modifier.height(20.dp))
            Text("hi, i'm potato", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "i read the words in your screenshots so you can search them later. " +
                    "it all happens right here on your phone, your screenshots never leave it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { if (blocked) openAppSettings(context) else ask.launch(Access.toRequest()) },
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(if (blocked) "open settings" else "okay, have a look", style = MaterialTheme.typography.titleMedium)
            }
            if (blocked) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "potato needs access to your photos to see the screenshots. it's under permissions → photos and videos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "i also tidy away old qr codes, boarding passes and login codes after a week. you can change that whenever.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
