package io.github.teamomuito.octopotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.Freed
import io.github.teamomuito.octopotato.data.Library
import io.github.teamomuito.octopotato.data.MediaEntry
import io.github.teamomuito.octopotato.data.MonthMath
import io.github.teamomuito.octopotato.data.MonthSummary
import io.github.teamomuito.octopotato.data.MonthView
import io.github.teamomuito.octopotato.data.Prefs
import io.github.teamomuito.octopotato.data.SwipeStore
import io.github.teamomuito.octopotato.data.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

class SwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SwipeStore.get(app)

    /** Every photo and video, or null while it's still being read. */
    private val library = MutableStateFlow<List<MediaEntry>?>(null)

    val openMonth = MutableStateFlow<YearMonth?>(null)

    /** Looking at the "marked for deletion" pile instead of the cards. */
    val reviewing = MutableStateFlow(false)

    val canSeeVideos = MutableStateFlow(Access.canSeeVideos(app))
    val freed: StateFlow<Freed> = Prefs.freed
    val skipTrash: StateFlow<Boolean> = Prefs.skipTrash

    // what was swiped this session, newest last, for the undo button
    private val history = ArrayDeque<Long>()
    val canUndo = MutableStateFlow(false)

    val months: StateFlow<List<MonthSummary>?> = combine(library, store.verdicts) { lib, verdicts ->
        lib?.let { MonthMath.summarize(it, verdicts) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val month: StateFlow<MonthView?> = combine(library, store.verdicts, openMonth) { lib, verdicts, m ->
        if (lib == null || m == null) null else MonthMath.view(lib, verdicts, m)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    /** Re-reads the library. Cheap enough to do on every resume, and it catches photos deleted elsewhere. */
    fun refresh() {
        val app = getApplication<Application>()
        canSeeVideos.value = Access.canSeeVideos(app)
        viewModelScope.launch(Dispatchers.IO) {
            store.load()
            library.value = Library.load(app)
        }
    }

    fun open(month: YearMonth) {
        openMonth.value = month
        reviewing.value = false
        history.clear()
        canUndo.value = false
    }

    fun close() {
        openMonth.value = null
        reviewing.value = false
    }

    fun decide(entry: MediaEntry, keep: Boolean) {
        store.set(entry.id, if (keep) Verdict.KEEP else Verdict.DELETE)
        history.addLast(entry.id)
        canUndo.value = true
    }

    fun undo() {
        val id = history.removeLastOrNull() ?: return
        store.clear(listOf(id))
        canUndo.value = history.isNotEmpty()
    }

    /** Changed their mind in the review pile. */
    fun rescue(entry: MediaEntry) = store.set(entry.id, Verdict.KEEP)

    /** Android confirmed these are gone. */
    fun deleted(entries: List<MediaEntry>) {
        val ids = entries.mapTo(HashSet()) { it.id }
        library.update { lib -> lib?.filter { it.id !in ids } }
        store.clear(ids)
        history.removeAll { it in ids }
        canUndo.value = history.isNotEmpty()
        Prefs.addFreed(entries.sumOf { it.size }, entries.size)
    }
}
