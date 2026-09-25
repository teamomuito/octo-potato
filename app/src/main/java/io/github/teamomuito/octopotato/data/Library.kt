package io.github.teamomuito.octopotato.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Every photo and video on the phone, for swipe cleanup. Things already in the trash are left out by MediaStore. */
object Library {

    fun load(context: Context, zone: ZoneId = ZoneId.systemDefault()): List<MediaEntry> {
        val out = ArrayList<MediaEntry>()
        read(context, MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), video = false, zone, out)
        read(context, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), video = true, zone, out)
        return out
    }

    private fun read(context: Context, collection: Uri, video: Boolean, zone: ZoneId, out: MutableList<MediaEntry>) {
        val columns = mutableListOf(BaseColumns._ID, MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED, MediaColumns.SIZE)
        if (video) columns += MediaColumns.DURATION
        context.contentResolver.query(collection, columns.toTypedArray(), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(BaseColumns._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaColumns.SIZE)
            val durationCol = if (video) c.getColumnIndexOrThrow(MediaColumns.DURATION) else -1
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                out += MediaEntry(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    isVideo = video,
                    taken = taken,
                    size = c.getLong(sizeCol),
                    durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0,
                    month = YearMonth.from(Instant.ofEpochMilli(taken).atZone(zone)),
                )
            }
        }
    }
}
