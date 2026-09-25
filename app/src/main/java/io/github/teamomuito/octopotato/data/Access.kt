package io.github.teamomuito.octopotato.data

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.Settings
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

    /** "All files access": lets the cleaner look through shared storage and use Android's clear-all-caches screen. */
    fun hasAllFiles(): Boolean = Environment.isExternalStorageManager()

    /** "Usage access": app sizes, cache sizes and when each app was last opened. */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            granted(context, android.Manifest.permission.PACKAGE_USAGE_STATS)
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun openAllFilesSettings(context: Context) {
        val forUs = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
        runCatching { context.startActivity(forUs) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }

    fun openUsageAccessSettings(context: Context) {
        val forUs = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        runCatching { context.startActivity(forUs) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
