package io.github.teamomuito.octopotato.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Remembers every swipe, so kept photos never come back and a half-done month picks up
 * where it was left. The whole thing lives in memory too, so a swipe shows up instantly
 * and the disk write happens in the background.
 */
class SwipeStore private constructor(context: Context) : SQLiteOpenHelper(context, NAME, null, 1) {

    private val writes = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val _verdicts = MutableStateFlow<Map<Long, Verdict>>(emptyMap())
    val verdicts: StateFlow<Map<Long, Verdict>> = _verdicts

    @Volatile private var loaded = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE verdicts (id INTEGER PRIMARY KEY, verdict INTEGER NOT NULL, at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Reads what's on disk. Call it off the main thread; after the first time it does nothing. */
    fun load() {
        if (loaded) return
        val fromDisk = HashMap<Long, Verdict>()
        readableDatabase.rawQuery("SELECT id, verdict FROM verdicts", null).use { c ->
            while (c.moveToNext()) Verdict.of(c.getInt(1))?.let { fromDisk[c.getLong(0)] = it }
        }
        // anything swiped while this was loading wins
        _verdicts.update { fromDisk + it }
        loaded = true
    }

    fun set(id: Long, verdict: Verdict) {
        _verdicts.update { it + (id to verdict) }
        writes.launch {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO verdicts (id, verdict, at) VALUES (?, ?, ?)",
                arrayOf<Any>(id, verdict.id, System.currentTimeMillis()),
            )
        }
    }

    fun clear(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val gone = ids.toSet()
        _verdicts.update { it - gone }
        writes.launch {
            for (chunk in gone.chunked(400)) {
                writableDatabase.execSQL("DELETE FROM verdicts WHERE id IN (${chunk.joinToString(",")})")
            }
        }
    }

    companion object {
        private const val NAME = "swipes.db"

        @Volatile private var instance: SwipeStore? = null

        fun get(context: Context): SwipeStore =
            instance ?: synchronized(this) {
                instance ?: SwipeStore(context.applicationContext).also { instance = it }
            }
    }
}
