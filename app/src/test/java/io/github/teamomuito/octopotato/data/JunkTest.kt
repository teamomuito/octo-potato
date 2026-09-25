package io.github.teamomuito.octopotato.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkTest {
    private val day = Expiry.DAY_MS
    private val now = 1000 * day
    private val mb = 1024L * 1024

    private fun file(rel: String, size: Long = 10, age: Int = 1) =
        FileNode("/sd/$rel", rel.substringAfterLast('/'), size, now - age * day)

    private fun dir(rel: String, vararg children: Any, readable: Boolean = true) = DirNode(
        path = if (rel.isEmpty()) "/sd" else "/sd/$rel",
        name = rel.substringAfterLast('/'),
        relative = rel,
        dirs = children.filterIsInstance<DirNode>(),
        files = children.filterIsInstance<FileNode>(),
        readable = readable,
    )

    private fun find(root: DirNode) = JunkRules.find(root, now)
    private fun names(report: JunkReport, kind: JunkKind) = report.of(kind).map { it.path.removePrefix("/sd/") }

    @Test fun `thumbnails folders are found with their size`() {
        val root = dir("", dir("DCIM", dir("DCIM/.thumbnails", file("DCIM/.thumbnails/a.jpg", 300), file("DCIM/.thumbnails/b.jpg", 200))))
        val t = find(root).of(JunkKind.THUMBNAILS).single()
        assertEquals("/sd/DCIM/.thumbnails", t.path)
        assertEquals(500L, t.bytes)
        assertEquals(2, t.files)
    }

    @Test fun `cache folders and tmp or log files count as leftover cache`() {
        val root = dir(
            "",
            dir("SomeApp", dir("SomeApp/cache", file("SomeApp/cache/x.bin", 70)), file("SomeApp/debug.log", 5), file("SomeApp/notes.txt")),
            file("upload.tmp", 9),
        )
        assertEquals(listOf("SomeApp/cache", "SomeApp/debug.log", "upload.tmp"), names(find(root), JunkKind.CACHE).sorted())
    }

    @Test fun `big files inside a cache folder are counted once, as cache`() {
        val root = dir("", dir("App", dir("App/.cache", file("App/.cache/huge.bin", 500 * mb, age = 400))))
        val r = find(root)
        assertEquals(1, r.of(JunkKind.CACHE).size)
        assertTrue(r.of(JunkKind.LARGE).isEmpty())
    }

    @Test fun `only the topmost empty folder is reported`() {
        val root = dir("", dir("OldApp", dir("OldApp/sub", dir("OldApp/sub/deeper"))))
        assertEquals(listOf("OldApp"), names(find(root), JunkKind.EMPTY))
    }

    @Test fun `standard folders stay even when empty, but empty folders inside them go`() {
        val root = dir("", dir("DCIM", dir("DCIM/Camera"), dir("DCIM/GoneApp")), dir("Download"))
        assertEquals(listOf("DCIM/GoneApp"), names(find(root), JunkKind.EMPTY))
    }

    @Test fun `hidden folders are never called empty`() {
        val root = dir("", dir(".config", dir(".config/app")), dir("Stuff", dir("Stuff/.marker")))
        assertTrue(find(root).of(JunkKind.EMPTY).isEmpty())
    }

    @Test fun `inside hidden folders only caches and thumbnails are picked up`() {
        val root = dir(
            "",
            dir(
                ".SomeApp",
                dir(".SomeApp/maps", file(".SomeApp/maps/europe.map", 800 * mb, age = 400)),
                dir(".SomeApp/cache", file(".SomeApp/cache/tile", 40)),
                file(".SomeApp/crash.log", 3),
            ),
        )
        val r = find(root)
        assertEquals(listOf(".SomeApp/cache", ".SomeApp/crash.log"), names(r, JunkKind.CACHE).sorted())
        assertTrue(r.of(JunkKind.LARGE).isEmpty())
        assertTrue(r.of(JunkKind.EMPTY).isEmpty())
    }

    @Test fun `documents only ever shows big old files`() {
        val root = dir(
            "",
            dir(
                "Documents",
                file("Documents/notes.log", 3),
                dir("Documents/cache", file("Documents/cache/mine.txt", 4)),
                dir("Documents/Taxes"),
                file("Documents/backup.zip", 700 * mb, age = 500),
            ),
        )
        val r = find(root)
        assertTrue(r.of(JunkKind.CACHE).isEmpty())
        assertTrue(r.of(JunkKind.EMPTY).isEmpty())
        assertEquals(listOf("backup.zip"), r.of(JunkKind.LARGE).map { it.name })
    }

    @Test fun `folders that couldn't be read are never called empty`() {
        val root = dir("", dir("Locked", readable = false), dir("Parent", dir("Parent/locked", readable = false)))
        assertTrue(find(root).of(JunkKind.EMPTY).isEmpty())
    }

    @Test fun `a folder with only a nomedia file is not empty`() {
        val root = dir("", dir("App", file("App/.nomedia", 0)))
        assertTrue(find(root).of(JunkKind.EMPTY).isEmpty())
    }

    @Test fun `large files have to be big and old`() {
        val root = dir(
            "",
            dir(
                "Download",
                file("Download/old-movie.mkv", 900 * mb, age = 200),
                file("Download/new-movie.mkv", 900 * mb, age = 5),
                file("Download/old-small.pdf", 2 * mb, age = 400),
            ),
        )
        val large = find(root).of(JunkKind.LARGE).single()
        assertEquals("old-movie.mkv", large.name)
        assertEquals("Download", large.where)
    }

    @Test fun `scanned file count covers everything`() {
        val root = dir("", dir("A", file("A/1"), file("A/2")), file("3"))
        assertEquals(3, find(root).scannedFiles)
    }
}

class AppRulesTest {
    private val day = Expiry.DAY_MS
    private val now = 1000 * day

    private fun app(pkg: String, bytes: Long, lastUsedDaysAgo: Int?, installedDaysAgo: Int, cache: Long = 0) =
        AppUsage(pkg, pkg, bytes, cache, lastUsedDaysAgo?.let { now - it * day }, now - installedDaysAgo * day)

    @Test fun `unused means installed a while ago and not opened in a month, biggest first`() {
        val apps = listOf(
            app("used", 500, lastUsedDaysAgo = 2, installedDaysAgo = 300),
            app("forgotten", 100, lastUsedDaysAgo = 90, installedDaysAgo = 300),
            app("never-opened", 900, lastUsedDaysAgo = null, installedDaysAgo = 120),
            app("just-installed", 800, lastUsedDaysAgo = null, installedDaysAgo = 3),
        )
        assertEquals(listOf("never-opened", "forgotten"), AppRules.unused(apps, now).map { it.pkg })
    }

    @Test fun `apps with cache, biggest cache first`() {
        val apps = listOf(app("a", 1, 1, 1, cache = 10), app("b", 1, 1, 1, cache = 0), app("c", 1, 1, 1, cache = 99))
        assertEquals(listOf("c", "a"), AppRules.byCache(apps).map { it.pkg })
    }
}
