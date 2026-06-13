package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.JsonObject
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.io.TempDir
class BackfillTest {
    private val listProps get() = discover(ListDirectoryConfigV2::class)
    private val mapProps get() = discover(MapDirectoryConfigV2::class)
    private val nestedProps get() = discover(NestedDirectoryConfigV2::class)
    @Test fun addsNonNullableSkipsNullablePreservesRest(@TempDir dir: File) {
        File(dir, "config.json").writeText("""{"name":"custom","count":7,"enabled":false,"my-custom":"keep"}""")
        val c = ref<SimpleConfigV2>(dir).poll()
        assertEquals("custom", c.name)
        assertEquals("added", c.newField)
        assertEquals(99, c.newInt)
        val d = File(dir, "config.json").read()
        assertTrue("new-field" in d)
        assertTrue("new-int" in d)
        assertFalse("new-nullable" in d)
        assertEquals("\"keep\"", d["my-custom"].toString())
        assertEquals("7", d["count"].toString())
    }
    @Test fun noopWhenAllPresent(@TempDir dir: File) {
        File(dir, "config.json").writeText("""{"name":"x","count":1,"enabled":true,"new-field":"a","new-int":50}""")
        val before = File(dir, "config.json").lastModified()
        ref<SimpleConfigV2>(dir).poll()
        assertEquals(before, File(dir, "config.json").lastModified())
    }
    @Test fun runsOnlyOnFirstPoll(@TempDir dir: File) {
        File(dir, "config.json").writeText("""{"name":"x","count":1,"enabled":true}""")
        val r = ref<SimpleConfigV2>(dir)
        r.poll()
        assertTrue("new-field" in File(dir, "config.json").read())
        val without = File(dir, "config.json").read().toMutableMap().apply { remove("new-field") }
        File(dir, "config.json").writeText(J.encodeToString(JsonObject.serializer(), JsonObject(without)))
        File(dir, "config.json").bumpMTime()
        r.poll()
        assertFalse("new-field" in File(dir, "config.json").read())
    }
    @Test fun backfillsListElementsNotLeakingDirField(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/r1.json").writeText("""{"id":"gem","amount":5,"user-note":"keep"}""")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        ref<ListDirectoryConfigV2>(dir, directoryProps = listProps).poll()
        val element = File(dir, "rewards/r1.json").read()
        assertTrue("rarity" in element)
        assertFalse("description" in element)
        assertTrue("user-note" in element)
        val main = File(dir, "config.json").read()
        assertFalse("rewards" in main)
        assertTrue("subtitle" in main)
    }
    @Test fun backfillsMapElements(@TempDir dir: File) {
        File(dir, "shops").mkdirs()
        File(dir, "shops/armor.json").writeText("""{"price":200,"currency":"gold"}""")
        File(dir, "config.json").writeText("""{"version":1}""")
        ref<MapDirectoryConfigV2>(dir, directoryProps = mapProps).poll()
        val element = File(dir, "shops/armor.json").read()
        assertTrue("discount" in element)
        assertFalse("note" in element)
    }
    @Test fun backfillsNestedEntryAndLeaf(@TempDir dir: File) {
        File(dir, "shops/main").mkdirs()
        File(dir, "shops/main/main.json").writeText("""{"shop-name":"armory"}""")
        File(dir, "shops/main/items").mkdirs()
        File(dir, "shops/main/items/i1.json").writeText("""{"item-name":"bow","cost":30}""")
        File(dir, "config.json").writeText("""{"label":"test"}""")
        ref<NestedDirectoryConfigV2>(dir, directoryProps = nestedProps).poll()
        assertTrue("tier" in File(dir, "shops/main/main.json").read())
        assertFalse("items" in File(dir, "shops/main/main.json").read())
        assertTrue("stock" in File(dir, "shops/main/items/i1.json").read())
    }
    @Test fun toleratesMalformedElementDuringBackfill(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/good.json").writeText("""{"id":"gem","amount":5}""")
        File(dir, "rewards/broken.json").writeText("""{"id":"x","amount":1}""")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        val r = ref<ListDirectoryConfigV2>(dir, directoryProps = listProps)
        r.poll()
        File(dir, "rewards/broken.json").writeText("not json")
        val fresh = ref<ListDirectoryConfigV2>(dir, directoryProps = listProps)
        runCatching { fresh.poll() }
        assertTrue("rarity" in File(dir, "rewards/good.json").read())
    }
}
