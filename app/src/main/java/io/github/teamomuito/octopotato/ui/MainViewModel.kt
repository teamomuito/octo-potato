package io.github.teamomuito.octopotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.Expiry
import io.github.teamomuito.octopotato.data.Kind
import io.github.teamomuito.octopotato.data.Media
import io.github.teamomuito.octopotato.data.Prefs
import io.github.teamomuito.octopotato.data.Progress
import io.github.teamomuito.octopotato.data.SearchQuery
import io.github.teamomuito.octopotato.data.Shot
import io.github.teamomuito.octopotato.data.ShotDb
import io.github.teamomuito.octopotato.data.TidyPlan
import io.github.teamomuito.octopotato.data.TidySettings
import io.github.teamomuito.octopotato.work.Jobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

enum class Filter(val label: String, val kind: Kind?) {
    ALL("all", null),
    QR("qr codes", Kind.QR),
    BOARDING("boarding passes", Kind.BOARDING),
    CODE("login codes", Kind.CODE),
    SOON("leaving soon", null),
}

data class Results(val searching: Boolean, val shots: List<Shot>)

data class Detail(val shot: Shot, val text: String)

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ShotDb.get(app)

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(Filter.ALL)
    val access = MutableStateFlow(Access.level(app))
    val canTidySilently = MutableStateFlow(Access.canTidySilently(app))
    val canNotify = MutableStateFlow(Access.canNotify(app))
    val tidy: StateFlow<TidySettings> = Prefs.tidy

    /** Set when someone taps the tidy-up notification. */
    val tidyAsked = MutableStateFlow(false)

    // while indexing, the database changes several times a second; screens don't need all of that
    private val dbTicks = db.changes.throttleLatest(400)

    val progress: StateFlow<Progress?> = dbTicks
        .map { db.progress() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val results: StateFlow<Results> = combine(
        query.debounce { if (it.isBlank()) 0L else 150L },
        filter,
        dbTicks,
        tidy,
    ) { q, f, _, t -> load(q, f, t) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Results(false, emptyList()))

    val due: StateFlow<List<Shot>> = combine(dbTicks, tidy) { _, t -> TidyPlan.due(db, t) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** Called on every resume: permissions may have changed, new screenshots may be waiting. */
    fun refresh() {
        val app = getApplication<Application>()
        access.value = Access.level(app)
        canTidySilently.value = Access.canTidySilently(app)
        canNotify.value = Access.canNotify(app)
        if (access.value != Access.Level.NONE) {
            viewModelScope.launch(Dispatchers.IO) { Media.sync(app, db) }
            Jobs.indexNow(app)
        }
    }

    private fun load(q: String, f: Filter, t: TidySettings): Results {
        val match = SearchQuery.toMatch(q)
        val shots = when {
            match != null -> db.search(match, f.kind)
            f == Filter.SOON -> db.temporaries()
            else -> db.all(f.kind)
        }
        if (f != Filter.SOON) return Results(match != null, shots)
        val leaving = shots
            .filter { t.covers(it.kind) && !it.keep }
            .sortedBy { Expiry.at(it.taken, it.found, t.days) }
        return Results(match != null, leaving)
    }

    fun detail(id: Long): Flow<Detail?> = dbTicks
        .map { db.get(id)?.let { Detail(it, db.text(id)) } }
        .flowOn(Dispatchers.IO)

    fun setKeep(id: Long, keep: Boolean) = io { db.setKeep(id, keep) }

    /** The system confirmed these went to the trash. */
    fun forget(ids: List<Long>) = io { db.remove(ids) }

    fun readAgain() = io {
        db.forgetReadings()
        Jobs.indexNow(getApplication<Application>(), queue = true)
    }

    fun updateTidy(change: (TidySettings) -> TidySettings) = Prefs.updateTidy(change)

    val skipTrash: StateFlow<Boolean> = Prefs.skipTrash

    fun setSkipTrash(on: Boolean) = Prefs.setSkipTrash(on)

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}

/** Emits right away, then at most once per [periodMs], always ending on the latest value. */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMs)
}
