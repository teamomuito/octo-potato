package io.github.teamomuito.octopotato.ui

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.Shot
import kotlinx.coroutines.launch

@Composable
fun PotatoApp(vm: MainViewModel) {
    val access by vm.access.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    if (access == Access.Level.NONE) {
        WelcomeScreen(onAnswered = vm::refresh)
    } else {
        Screens(vm)
    }
}

@Composable
private fun Screens(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // Moving other apps' files to the trash always goes through a system request.
    // With "media management" access it's approved on the spot, otherwise Android asks first.
    var trashing by remember { mutableStateOf<List<Long>>(emptyList()) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val ids = trashing
        trashing = emptyList()
        if (result.resultCode == Activity.RESULT_OK && ids.isNotEmpty()) {
            vm.forget(ids)
            if (openId?.let { it in ids } == true) openId = null
            val message = if (ids.size == 1) "moved to the trash" else "moved ${ids.size} screenshots to the trash"
            scope.launch { snackbar.showSnackbar(message) }
        }
    }
    val trash: (List<Shot>) -> Unit = { shots ->
        if (shots.isNotEmpty() && trashing.isEmpty()) {
            trashing = shots.map { it.id }
            runCatching {
                val pending = MediaStore.createTrashRequest(context.contentResolver, shots.map { it.uri }, true)
                trashLauncher.launch(IntentSenderRequest.Builder(pending).build())
            }.onFailure { trashing = emptyList() }
        }
    }

    val due by vm.due.collectAsStateWithLifecycle()
    val tidyAsked by vm.tidyAsked.collectAsStateWithLifecycle()
    val silent by vm.canTidySilently.collectAsStateWithLifecycle()
    var tidiedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(due, tidyAsked, silent) {
        if (due.isEmpty()) return@LaunchedEffect
        if (tidyAsked || (silent && !tidiedOnOpen)) {
            vm.tidyAsked.value = false
            tidiedOnOpen = true
            trash(due)
        }
    }

    val swipe: SwipeViewModel = viewModel()
    val swiping by swipe.openMonth.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_SCREENSHOTS) }

    BackHandler(enabled = tab == TAB_SWIPE && !settingsOpen && openId == null) { tab = TAB_SCREENSHOTS }
    BackHandler(enabled = settingsOpen && openId == null) { settingsOpen = false }
    BackHandler(enabled = openId != null) { openId = null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (tab == TAB_SCREENSHOTS) {
                    HomeScreen(
                        vm = vm,
                        due = due,
                        onOpen = { openId = it.id },
                        onSettings = { settingsOpen = true },
                        onTidy = { trash(due) },
                    )
                } else {
                    SwipeScreen(swipe, onSettings = { settingsOpen = true })
                }
            }
            // the cards get the whole screen while you're swiping through a month
            if (tab == TAB_SCREENSHOTS || swiping == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    NavigationBarItem(
                        selected = tab == TAB_SCREENSHOTS,
                        onClick = { tab = TAB_SCREENSHOTS },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text("screenshots") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_SWIPE,
                        onClick = { tab = TAB_SWIPE },
                        icon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                        label = { Text("swipe") },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut(),
        ) {
            SettingsScreen(vm, onBack = { settingsOpen = false })
        }
        AnimatedContent(
            targetState = openId,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut()) },
            modifier = Modifier.fillMaxSize(),
            label = "detail",
        ) { id ->
            if (id != null) {
                DetailScreen(id, vm, onBack = { openId = null }, onTrash = { trash(listOf(it)) })
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        )
    }
}

private const val TAB_SCREENSHOTS = 0
private const val TAB_SWIPE = 1
