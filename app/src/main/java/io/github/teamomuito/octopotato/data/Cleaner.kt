package io.github.teamomuito.octopotato.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import java.io.File
import java.nio.file.Files

/** Walks shared storage and deletes junk. Everything here needs "all files access". */
object Cleaner {
    private const val MAX_DEPTH = 24

    val root: File get() = Environment.getExternalStorageDirectory()

    /** Reads the whole of shared storage into memory. [progress] gets the running file count. */
    fun scan(progress: (Int) -> Unit): DirNode {
        var seen = 0

        fun walk(dir: File, relative: String, depth: Int): DirNode {
            val children = dir.listFiles() ?: return DirNode(dir.path, dir.name, relative, emptyList(), emptyList(), readable = false)
            val dirs = ArrayList<DirNode>()
            val files = ArrayList<FileNode>()
            var readable = true
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) {
                    // Android/data and Android/obb are off limits to every app since Android 11
                    if (depth == 0 && child.name.equals("Android", ignoreCase = true)) continue
                    val rel = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                    if (depth < MAX_DEPTH) dirs += walk(child, rel, depth + 1) else readable = false
                } else {
                    files += FileNode(child.path, child.name, child.length(), child.lastModified())
                    if (++seen % 400 == 0) progress(seen)
                }
            }
            return DirNode(dir.path, dir.name, relative, dirs, files, readable)
        }

        return walk(root, "", 0).also { progress(seen) }
    }

    /** Deletes what was picked and returns how many bytes that freed. Re-checks everything first. */
    fun delete(items: Collection<JunkItem>): Long {
        val base = root.canonicalPath + File.separator
        var freed = 0L
        for (item in items) {
            val file = File(item.path)
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: continue
            if (!canonical.startsWith(base)) continue
            val relative = canonical.removePrefix(base)
            if (relative.isEmpty() || JunkRules.isKept(relative) || relative.startsWith("Android/", ignoreCase = true)) continue
            val ok = when (item.kind) {
                // only removes folders; if a file showed up since the scan, it stays and so does its folder
                JunkKind.EMPTY -> deleteEmptyTree(file)
                JunkKind.CACHE, JunkKind.THUMBNAILS -> if (file.isDirectory) file.deleteRecursively() else file.delete()
                JunkKind.LARGE -> file.isFile && file.delete()
            }
            if (ok) freed += item.bytes
        }
        return freed
    }

    private fun deleteEmptyTree(dir: File): Boolean {
        if (!dir.isDirectory) return false
        dir.listFiles()?.filter { it.isDirectory }?.forEach { deleteEmptyTree(it) }
        return dir.delete()
    }
}

/** Sizes and last-used times of installed apps. Needs "usage access". */
object Apps {

    fun load(context: Context): List<AppUsage> {
        val pm = context.packageManager
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val lastUsed = runCatching { usage.queryAndAggregateUsageStats(now - 365 * Expiry.DAY_MS, now) }.getOrNull().orEmpty()
        val user = Process.myUserHandle()

        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(0)
        return installed
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
            .mapNotNull { info ->
                val size = runCatching { stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user) }.getOrNull()
                @Suppress("DEPRECATION")
                val firstInstall = runCatching { pm.getPackageInfo(info.packageName, 0).firstInstallTime }.getOrNull() ?: return@mapNotNull null
                AppUsage(
                    pkg = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    bytes = (size?.appBytes ?: 0) + (size?.dataBytes ?: 0),
                    cacheBytes = size?.cacheBytes ?: 0,
                    lastUsed = lastUsed[info.packageName]?.lastTimeUsed?.takeIf { it > 0 },
                    installed = firstInstall,
                )
            }
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
