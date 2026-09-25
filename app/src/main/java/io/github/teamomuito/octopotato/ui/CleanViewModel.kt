package io.github.teamomuito.octopotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.AppRules
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

private const val GUIDED_MIN_BYTES = 5L * 1024 * 1024

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

    /** After Android's clear-all-caches screen: whatever the cache total dropped by was freed. */
    suspend fun recountCaches(before: Long): Long = withContext(Dispatchers.IO) {
        val now = Apps.load(getApplication())
        apps.value = now
        (before - now.sumOf { it.cacheBytes }).coerceAtLeast(0).also { Prefs.addCleaned(it) }
    }

    /**
     * One by one: for phones without Android's clear-all screen (Samsung, for one). Each app's
     * settings page gets opened in turn, biggest cache first; the person taps clear cache there.
     */
    data class Guided(val queue: List<AppUsage>, val done: Int = 0, val freed: Long = 0) {
        val next: AppUsage? get() = queue.firstOrNull()
    }

    val guided = MutableStateFlow<Guided?>(null)

    fun startGuided() {
        val withCache = AppRules.byCache(apps.value.orEmpty()).filter { it.cacheBytes > 0 }
        // skip the crumbs, unless crumbs are all there is
        val worth = withCache.filter { it.cacheBytes >= GUIDED_MIN_BYTES }.ifEmpty { withCache }
        guided.value = if (worth.isEmpty()) null else Guided(worth)
    }

    fun skipGuided() = guided.update { g -> g?.let { if (it.queue.size <= 1) null else it.copy(queue = it.queue.drop(1)) } }

    fun stopGuided() {
        guided.value = null
        loadApps()
    }

    /** Back from an app's settings page: count what went, move on to the next one. */
    fun afterGuidedStep() {
        val g = guided.value ?: return
        val app = g.next ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val after = Apps.cacheOf(getApplication(), app.pkg) ?: 0
            val freed = (app.cacheBytes - after).coerceAtLeast(0)
            Prefs.addCleaned(freed)
            apps.update { list -> list?.map { if (it.pkg == app.pkg) it.copy(cacheBytes = after) else it } }
            guided.update { current ->
                current?.let { it.copy(queue = it.queue.drop(1), done = it.done + if (freed > 0) 1 else 0, freed = it.freed + freed) }
            }
        }
    }

    fun afterUninstall(pkg: String) {
        if (!Apps.isInstalled(getApplication(), pkg)) {
            apps.update { list -> list?.filter { it.pkg != pkg } }
        }
    }
}
