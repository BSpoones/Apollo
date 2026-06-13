package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.NestedDirectoryConfig
import com.beespoon.apollo.util.*
import org.junit.jupiter.api.io.TempDir
class FileUtilTest {
    @Test fun snapshotReflectsContent(@TempDir dir: File) {
        val f = File(dir, "test.json").apply { writeText("hello") }
        val s1 = FileSnapshot(f.lastModified(), f.length())
        assertEquals(5L, s1.length)
        f.writeText("hello world")
        f.bumpMTime()
        assertNotEquals(s1, FileSnapshot(f.lastModified(), f.length()))
    }
    @Test fun filesFilteredByExtensionAndSorted(@TempDir dir: File) {
        File(dir, "10.json").writeText("{}")
        File(dir, "2.json").writeText("{}")
        File(dir, "1.json").writeText("{}")
        File(dir, "note.txt").writeText("")
        assertEquals(listOf("1.json", "2.json", "10.json"), dir.filesWithExtension("json").map { it.name })
    }
    @Test fun subdirectoriesExcludeFiles(@TempDir dir: File) {
        File(dir, "sub1").mkdirs()
        File(dir, "sub2").mkdirs()
        File(dir, "file.txt").writeText("")
        assertEquals(2, dir.subdirectories().size)
    }
    @Test fun emptyListingsForMissingDir(@TempDir dir: File) {
        val missing = File(dir, "missing")
        assertTrue(missing.filesWithExtension("json").isEmpty())
        assertTrue(missing.subdirectories().isEmpty())
    }
    @Test fun atomicWriteReplacesContent(@TempDir dir: File) {
        val f = File(dir, "data.json")
        f.writeTextAtomically("first")
        f.writeTextAtomically("second")
        assertEquals("second", f.readText())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
    @Test fun touchTreeIgnoresMissingMainFile(@TempDir dir: File) { touchTree(File(dir, "absent.json"), emptyList()) }
    @Test fun touchTreeLeavesPlainFileReadable(@TempDir dir: File) {
        val f = File(dir, "config.json").apply { writeText("""{"title":"keep"}""") }
        touchTree(f, emptyList())
        assertTrue(f.exists())
        assertEquals("""{"title":"keep"}""", f.readText())
    }
    @Test fun touchTreeWalksDirectoryConfigTreeHarmlessly(@TempDir dir: File) {
        val props = discover(NestedDirectoryConfig::class)
        val r = ref<NestedDirectoryConfig>(dir, directoryProps = props)
        val before = r.poll()
        val main = File(dir, "config.json")
        val entry = File(dir, "shops/main/main.json")
        val leaf = File(dir, "shops/main/items").filesWithExtension("json").single()
        touchTree(main, props)
        assertTrue(main.exists() && entry.exists() && leaf.exists())
        assertEquals(before.shops, ref<NestedDirectoryConfig>(dir, directoryProps = props).poll().shops)
    }
}
