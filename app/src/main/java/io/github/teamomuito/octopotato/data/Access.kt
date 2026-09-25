package io.github.teamomuito.octopotato.data

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object Access {
    enum class Level { NONE, PARTIAL, FULL }

    fun level(context: Context): Level = when {
        Build.VERSION.SDK_INT >= 33 && granted(context, READ_MEDIA_IMAGES) -> Level.FULL
        Build.VERSION.SDK_INT >= 34 && granted(context, READ_MEDIA_VISUAL_USER_SELECTED) -> Level.PARTIAL
        Build.VERSION.SDK_INT < 33 && granted(context, READ_EXTERNAL_STORAGE) -> Level.FULL
        else -> Level.NONE
    }

    /** What to ask for. Videos are for swipe cleanup, notifications for tidy-up reminders. */
    fun toRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, POST_NOTIFICATIONS)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, POST_NOTIFICATIONS)
        else -> arrayOf(READ_EXTERNAL_STORAGE)
    }

    /** For people who installed before videos were a thing. */
    fun videoRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(READ_MEDIA_VIDEO)
        else -> arrayOf(READ_EXTERNAL_STORAGE)
    }

    fun canSeeVideos(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= 34 && granted(context, READ_MEDIA_VISUAL_USER_SELECTED))
        else -> granted(context, READ_EXTERNAL_STORAGE)
    }

    fun mediaPermission(): String = if (Build.VERSION.SDK_INT >= 33) READ_MEDIA_IMAGES else READ_EXTERNAL_STORAGE

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 || granted(context, POST_NOTIFICATIONS)

    /** "Media management" special access: trash requests go through without a popup. */
    fun canTidySilently(context: Context): Boolean = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
