package io.github.teamomuito.octopotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.AppUsage
import io.github.teamomuito.octopotato.data.Apps
import io.github.teamomuito.octopotato.data.Cleaner
import io.github.teamomuito.octopotato.data.JunkItem
import io.github.teamomuito.octopotato.data.JunkKind
import io.github.teamomuito.octopotato.data.JunkReport
import io.github.teamomuito.octopotato.data.JunkRules
import io.github.teamomuito.octopotato.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val files: Int) : ScanState
    data class Done(val report: JunkReport) : ScanState
}

class CleanViewModel(app: Application) : AndroidViewModel(app) {

    val hasAllFiles = MutableStateFlow(Access.hasAllFiles())
    val hasUsage = MutableStateFlow(Access.hasUsageAccess(app))
    val scan = MutableStateFlow<ScanState>(ScanState.Idle)

    /** Installed apps with their sizes, or null until loaded (or without usage access). */
    val apps = MutableStateFlow<List<AppUsage>?>(null)

    /** Paths ticked for deletion. */
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val working = MutableStateFlow(false)
    val cleaned: StateFlow<Long> = Prefs.cleaned

    private var scanJob: Job? = null

    fun refresh() {
        val app = getApplication<Application>()
        hasAllFiles.value = Access.hasAllFiles()
        hasUsage.value = Access.hasUsageAccess(app)
        if (hasAllFiles.value && scan.value == ScanState.Idle) scan()
        if (hasUsage.value && apps.value == null) loadApps()
    }

    fun scan() {
        if (!Access.hasAllFiles()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            scan.value = ScanState.Scanning(0)
            val root = Cleaner.scan { seen -> scan.value = ScanState.Scanning(seen) }
            val report = JunkRules.find(root, System.currentTimeMillis())
            // the safe stuff starts ticked; large files are personal, so nothing there is ticked for you
            selected.value = report.items.filter { it.kind != JunkKind.LARGE }.mapTo(HashSet()) { it.path }
            scan.value = ScanState.Done(report)
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            apps.value = if (Access.hasUsageAccess(getApplication())) Apps.load(getApplication()) else null
        }
    }

    fun toggle(item: JunkItem) = selected.update { if (item.path in it) it - item.path else it + item.path }

    fun setAll(items: List<JunkItem>, on: Boolean) = selected.update { current ->
        val paths = items.map { it.path }
        if (on) current + paths else current - paths.toSet()
    }

    /** Deletes everything ticked, adds it to the running total, then looks again. Returns bytes freed. */
    suspend fun clean(): Long {
        val report = (scan.value as? ScanState.Done)?.report ?: return 0
        val picked = report.items.filter { it.path in selected.value }
        if (picked.isEmpty()) return 0
        working.value = true
        val freed = withContext(Dispatchers.IO) { Cleaner.delete(picked) }
        Prefs.addCleaned(freed)
        working.value = false
        scan()
        return freed
    }

    /** Android's clear-all-caches screen is done; whatever the cache total dropped by was freed. */
    fun afterCacheClear(before: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = Apps.load(getApplication())
            apps.value = now
            Prefs.addCleaned(before - now.sumOf { it.cacheBytes })
        }
    }

    fun afterUninstall(pkg: String) {
        if (!Apps.isInstalled(getApplication(), pkg)) {
            apps.update { list -> list?.filter { it.pkg != pkg } }
        }
    }
}
