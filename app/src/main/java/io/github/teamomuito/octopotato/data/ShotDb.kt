package io.github.teamomuito.octopotato.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Shot(
    val id: Long,
    val uri: Uri,
    val name: String,
    val taken: Long,
    val found: Long,
    val read: Boolean,
    val kind: Kind,
    val keep: Boolean,
    val snippet: String? = null,
)

data class Progress(val read: Int, val total: Int) {
    val done: Boolean get() = read >= total
}

/**
 * Plain SQLite plus an FTS4 table for the words. Small enough that Room felt like overkill.
 * Row ids are MediaStore ids, so a screenshot keeps its row as long as it's on the phone.
 */
class ShotDb private constructor(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    private val _changes = MutableStateFlow(0L)

    /** Goes up every time something is written, so screens know to reload. */
    val changes: StateFlow<Long> = _changes

    private fun changed() = _changes.update { it + 1 }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE shots (
                id INTEGER PRIMARY KEY,
                uri TEXT NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                taken INTEGER NOT NULL,
                found INTEGER NOT NULL,
                status INTEGER NOT NULL DEFAULT 0,
                kind INTEGER NOT NULL DEFAULT 0,
                keep INTEGER NOT NULL DEFAULT 0,
                body TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX shots_taken ON shots (taken)")
        db.execSQL("CREATE INDEX shots_status ON shots (status)")
        // body is what the screenshot says. extra holds the kind words and file name:
        // searchable, but kept out of the snippets shown in results.
        db.execSQL("CREATE VIRTUAL TABLE shots_fts USING fts4 (body, extra, tokenize=unicode61)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // it's only an index, so starting over is always safe
        db.execSQL("DROP TABLE IF EXISTS shots")
        db.execSQL("DROP TABLE IF EXISTS shots_fts")
        onCreate(db)
    }

    fun knownIds(): Set<Long> = readableDatabase.rawQuery("SELECT id FROM shots", null).use { c ->
        HashSet<Long>(c.count).apply { while (c.moveToNext()) add(c.getLong(0)) }
    }

    fun insert(items: List<MediaItem>, now: Long) {
        if (items.isEmpty()) return
        writableDatabase.inTransaction {
            val row = ContentValues()
            for (m in items) {
                row.clear()
                row.put("id", m.id)
                row.put("uri", m.uri.toString())
                row.put("name", m.name)
                row.put("taken", m.taken)
                row.put("found", now)
                insertWithOnConflict("shots", null, row, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
        changed()
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.inTransaction {
            for (chunk in ids.chunked(400)) {
                val list = chunk.joinToString(",")
                execSQL("DELETE FROM shots WHERE id IN ($list)")
                execSQL("DELETE FROM shots_fts WHERE docid IN ($list)")
            }
        }
        changed()
    }

    data class Pending(val id: Long, val uri: Uri)

    /** Unread screenshots, newest first, since those are the ones people look for. */
    fun pending(limit: Int): List<Pending> =
        readableDatabase.rawQuery("SELECT id, uri FROM shots WHERE status = 0 ORDER BY taken DESC LIMIT $limit", null).use { c ->
            buildList { while (c.moveToNext()) add(Pending(c.getLong(0), Uri.parse(c.getString(1)))) }
        }

    fun pendingCount(): Int =
        DatabaseUtils.longForQuery(readableDatabase, "SELECT COUNT(*) FROM shots WHERE status = 0", null).toInt()

    fun saveReading(id: Long, text: String, kind: Kind) {
        writableDatabase.inTransaction {
            val row = ContentValues().apply {
                put("status", STATUS_READ)
                put("kind", kind.id)
                put("body", text)
            }
            if (update("shots", row, "id = ?", arrayOf(id.toString())) > 0) {
                val name = DatabaseUtils.stringForQuery(this, "SELECT name FROM shots WHERE id = ?", arrayOf(id.toString()))
                execSQL("DELETE FROM shots_fts WHERE docid = $id")
                execSQL("INSERT INTO shots_fts (docid, body, extra) VALUES (?, ?, ?)", arrayOf<Any>(id, text, extraWords(kind, name)))
            }
        }
        changed()
    }

    // File names often say which app the screenshot came from (Screenshot_20250101_Instagram.jpg),
    // so they're searchable too.
    private fun extraWords(kind: Kind, name: String): String =
        kind.searchWords + " " + name.substringBeforeLast('.').replace('_', ' ')

    fun markFailed(id: Long) {
        writableDatabase.execSQL("UPDATE shots SET status = $STATUS_FAILED WHERE id = $id")
        changed()
    }

    fun setKeep(id: Long, keep: Boolean) {
        writableDatabase.execSQL("UPDATE shots SET keep = ${if (keep) 1 else 0} WHERE id = $id")
        changed()
    }

    /** "Read everything again". Keeps the keep flags, drops everything else. */
    fun forgetReadings() {
        writableDatabase.inTransaction {
            execSQL("UPDATE shots SET status = 0, kind = 0, body = ''")
            execSQL("DELETE FROM shots_fts")
        }
        changed()
    }

    fun progress(): Progress =
        readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(status != 0), 0) FROM shots", null).use { c ->
            c.moveToFirst()
            Progress(read = c.getInt(1), total = c.getInt(0))
        }

    fun all(kind: Kind?): List<Shot> {
        val where = if (kind != null) "WHERE kind = ${kind.id}" else ""
        return shots("SELECT $COLS, NULL FROM shots $where ORDER BY taken DESC", null)
    }

    fun temporaries(): List<Shot> =
        shots("SELECT $COLS, NULL FROM shots WHERE kind != 0 AND keep = 0 ORDER BY taken", null)

    fun search(match: String, kind: Kind?): List<Shot> {
        val andKind = if (kind != null) "AND s.kind = ${kind.id}" else ""
        val sql = """
            SELECT $S_COLS, snippet(shots_fts, char(1), char(2), '…', 0, 14)
            FROM shots_fts JOIN shots s ON s.id = shots_fts.docid
            WHERE shots_fts MATCH ? $andKind
            ORDER BY s.taken DESC
            LIMIT 500
        """
        return try {
            shots(sql, arrayOf(match))
        } catch (e: SQLiteException) {
            emptyList()
        }
    }

    fun get(id: Long): Shot? = shots("SELECT $COLS, NULL FROM shots WHERE id = $id", null).firstOrNull()

    fun text(id: Long): String = readableDatabase.rawQuery("SELECT body FROM shots WHERE id = $id", null).use { c ->
        if (c.moveToFirst()) c.getString(0) else ""
    }

    private fun shots(sql: String, args: Array<String>?): List<Shot> = readableDatabase.rawQuery(sql, args).use { c ->
        buildList(c.count) { while (c.moveToNext()) add(c.toShot()) }
    }

    private fun Cursor.toShot() = Shot(
        id = getLong(0),
        uri = Uri.parse(getString(1)),
        name = getString(2),
        taken = getLong(3),
        found = getLong(4),
        read = getInt(5) != 0,
        kind = Kind.of(getInt(6)),
        keep = getInt(7) != 0,
        snippet = if (isNull(8)) null else getString(8),
    )

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val NAME = "shots.db"
        private const val VERSION = 1
        private const val STATUS_READ = 1
        private const val STATUS_FAILED = 2
        private const val COLS = "id, uri, name, taken, found, status, kind, keep"
        private const val S_COLS = "s.id, s.uri, s.name, s.taken, s.found, s.status, s.kind, s.keep"

        @Volatile private var instance: ShotDb? = null

        fun get(context: Context): ShotDb =
            instance ?: synchronized(this) {
                instance ?: ShotDb(context.applicationContext).also { instance = it }
            }
    }
}
