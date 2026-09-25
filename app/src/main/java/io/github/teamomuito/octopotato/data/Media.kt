package io.github.teamomuito.octopotato.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

data class MediaItem(val id: Long, val uri: Uri, val name: String, val taken: Long)

object Media {
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Every screenshot on the phone, or null if MediaStore didn't answer. */
    fun screenshots(context: Context): List<MediaItem>? {
        val projection = arrayOf(BaseColumns._ID, MediaColumns.DISPLAY_NAME, MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED)
        // Most phones keep them in a Screenshots folder, a few only name the files that way.
        val selection = "${MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaColumns.BUCKET_DISPLAY_NAME} LIKE ? OR ${MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%screenshot%", "%screenshot%", "screenshot%")
        val cursor = context.contentResolver.query(collection, projection, selection, args, null) ?: return null
        return cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(BaseColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            buildList(c.count) {
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                    add(MediaItem(id, ContentUris.withAppendedId(collection, id), c.getString(nameCol) ?: "", taken))
                }
            }
        }
    }

    /** Adds new screenshots to the database and drops the ones that are gone from the phone. */
    fun sync(context: Context, db: ShotDb) {
        if (Access.level(context) == Access.Level.NONE) return
        val onPhone = screenshots(context) ?: return
        val known = db.knownIds()
        db.insert(onPhone.filter { it.id !in known }, System.currentTimeMillis())
        val stillThere = onPhone.mapTo(HashSet()) { it.id }
        db.remove(known.filter { it !in stillThere })
    }
}
