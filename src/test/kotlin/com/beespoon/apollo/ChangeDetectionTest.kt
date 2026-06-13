package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.reload.ChangeDetection
import com.beespoon.apollo.testdata.SimpleConfig
import org.junit.jupiter.api.io.TempDir
class ChangeDetectionTest {
    @Test fun modificationTimeIgnoresUnchangedFingerprint(@TempDir dir: File) {
        val f = File(dir, "config.json")
        val r = ref<SimpleConfig>(dir)
        r.poll()
        val mtime = f.lastModified()
        f.writeText(f.readText().replace("\"default\"", "\"modifZd\""))
        f.setLastModified(mtime)
        assertEquals("default", r.poll().name)
    }
    @Test fun contentDetectsChangeWithoutFingerprintMove(@TempDir dir: File) {
        val f = File(dir, "config.json")
        val r = ref<SimpleConfig>(dir, detection = ChangeDetection.CONTENT)
        r.poll()
        val mtime = f.lastModified()
        f.writeText(f.readText().replace("\"default\"", "\"modifZd\""))
        f.setLastModified(mtime)
        assertEquals("modifZd", r.poll().name)
    }
    @Test fun contentStaysQuietWhenValueUnchanged(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir, detection = ChangeDetection.CONTENT)
        var fires = 0
        r.listen { fires++ }
        r.poll()
        assertEquals(1, fires)
        r.poll()
        assertEquals(1, fires)
    }
}
