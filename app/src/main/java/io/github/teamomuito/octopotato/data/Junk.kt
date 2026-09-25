package io.github.teamomuito.octopotato.data

/** A file found while scanning shared storage. */
data class FileNode(val path: String, val name: String, val size: Long, val modified: Long)

/**
 * A folder and everything under it. [complete] is false when part of it couldn't be read
 * (no permission, too deep), and then it's never treated as empty. Neither is anything
 * holding a hidden folder, since apps use those as markers.
 */
class DirNode(
    val path: String,
    val name: String,
    val relative: String,
    val dirs: List<DirNode>,
    val files: List<FileNode>,
    val readable: Boolean = true,
) {
    val bytes: Long by lazy { files.sumOf { it.size } + dirs.sumOf { it.bytes } }
    val fileCount: Int by lazy { files.size + dirs.sumOf { it.fileCount } }
    val complete: Boolean by lazy { readable && dirs.all { it.complete } }
    val hasHidden: Boolean by lazy { dirs.any { it.name.startsWith(".") || it.hasHidden } }
    val isEmpty: Boolean get() = complete && fileCount == 0 && !hasHidden
}

enum class JunkKind { CACHE, THUMBNAILS, EMPTY, LARGE }

data class JunkItem(
    val kind: JunkKind,
    val path: String,
    val name: String,
    /** The folder it's in, relative to the storage root, for showing people. */
    val where: String,
    val bytes: Long,
    val files: Int,
    val isDir: Boolean,
    val modified: Long,
)

data class JunkReport(val items: List<JunkItem>, val scannedFiles: Int) {
    fun of(kind: JunkKind): List<JunkItem> = items.filter { it.kind == kind }
}

/**
 * Decides what counts as junk. Deliberately careful: standard folders are never removed,
 * hidden folders are never called empty (apps use them as markers), and anything that
 * couldn't be fully read is left alone.
 */
object JunkRules {
    const val LARGE_BYTES = 50L * 1024 * 1024
    const val OLD_DAYS = 90

    /** Folders apps use as scratch space on shared storage. */
    val CACHE_DIRS = setOf(".cache", "cache", ".tmp", "tmp", ".temp")
    val CACHE_FILE_ENDINGS = listOf(".tmp", ".log")

    /** Folders Android and apps expect to be there, even when empty. Lowercase, relative to the root. */
    val KEEP = setOf(
        "android", "dcim", "dcim/camera", "dcim/screenshots", "pictures", "pictures/screenshots",
        "movies", "music", "download", "documents", "podcasts", "ringtones", "alarms",
        "notifications", "audiobooks", "recordings",
    )

    fun find(root: DirNode, now: Long): JunkReport {
        val out = ArrayList<JunkItem>()
        visit(root, now, out)
        return JunkReport(out, root.fileCount)
    }

    fun isKept(relative: String): Boolean = relative.lowercase() in KEEP

    /**
     * Inside hidden folders ([hidden]) only thumbnails and caches are picked up; the rest belongs to some app.
     * Inside Documents ([personal]) only big files are shown, since everything there is someone's own stuff.
     */
    private fun visit(dir: DirNode, now: Long, out: MutableList<JunkItem>, hidden: Boolean = false, personal: Boolean = false) {
        for (d in dir.dirs) {
            val lower = d.name.lowercase()
            val mine = personal || d.relative.equals("documents", ignoreCase = true)
            when {
                mine -> visit(d, now, out, hidden, personal = true)
                lower == ".thumbnails" -> if (d.fileCount > 0) out += item(JunkKind.THUMBNAILS, d, dir)
                lower in CACHE_DIRS && d.fileCount > 0 -> out += item(JunkKind.CACHE, d, dir)
                hidden || d.name.startsWith(".") -> visit(d, now, out, hidden = true)
                d.isEmpty && !isKept(d.relative) -> out += item(JunkKind.EMPTY, d, dir)
                else -> visit(d, now, out)
            }
        }
        for (f in dir.files) {
            val lower = f.name.lowercase()
            val old = f.size >= LARGE_BYTES && f.modified < now - OLD_DAYS * Expiry.DAY_MS
            when {
                personal -> if (old && !hidden) out += item(JunkKind.LARGE, f, dir)
                CACHE_FILE_ENDINGS.any { lower.endsWith(it) } -> out += item(JunkKind.CACHE, f, dir)
                hidden -> Unit
                old -> out += item(JunkKind.LARGE, f, dir)
            }
        }
    }

    private fun item(kind: JunkKind, d: DirNode, parent: DirNode) =
        JunkItem(kind, d.path, d.name, parent.relative, d.bytes, d.fileCount, isDir = true, modified = 0)

    private fun item(kind: JunkKind, f: FileNode, parent: DirNode) =
        JunkItem(kind, f.path, f.name, parent.relative, f.size, 1, isDir = false, modified = f.modified)
}

/** An installed app, as far as cleaning is concerned. */
data class AppUsage(
    val pkg: String,
    val label: String,
    /** App plus its data, cache included. */
    val bytes: Long,
    val cacheBytes: Long,
    val lastUsed: Long?,
    val installed: Long,
)

object AppRules {
    const val UNUSED_DAYS = 30

    /** Apps that have been around a while and haven't been opened in a month. Biggest first. */
    fun unused(apps: List<AppUsage>, now: Long): List<AppUsage> {
        val cutoff = now - UNUSED_DAYS * Expiry.DAY_MS
        return apps.filter { it.installed < cutoff && (it.lastUsed == null || it.lastUsed < cutoff) }
            .sortedByDescending { it.bytes }
    }

    fun byCache(apps: List<AppUsage>): List<AppUsage> = apps.filter { it.cacheBytes > 0 }.sortedByDescending { it.cacheBytes }
}
