package com.beespoon.apollo
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.*
import com.beespoon.apollo.api.ConfigException
import com.beespoon.apollo.testdata.*
import com.beespoon.apollo.util.*
import org.junit.jupiter.api.io.TempDir
class DirectoryConfigTest {
    private val listProps get() = discover(ListDirectoryConfig::class)
    private val mapProps get() = discover(MapDirectoryConfig::class)
    private val nestedProps get() = discover(NestedDirectoryConfig::class)
    private val deepProps get() = discover(DeeplyNestedConfig::class)
    @Test fun listSplitsAndExcludesFromMain(@TempDir dir: File) {
        ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        assertEquals(1, File(dir, "rewards").filesWithExtension("json").size)
        val main = File(dir, "config.json").read()
        assertTrue("rewards" !in main)
        assertTrue("title" in main)
    }
    @Test fun listReadsBack(@TempDir dir: File) {
        val c = ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        assertEquals("rewards-page", c.title)
        assertEquals("coin", c.rewards.single().id)
    }
    @Test fun listPreservesInsertionOrder(@TempDir dir: File) {
        val props = discover(OrderedListConfig::class)
        val ids = ref<OrderedListConfig>(dir, directoryProps = props).poll().rewards.map { it.id }
        assertEquals(listOf("alpha", "bravo", "charlie"), ids)
        assertEquals(ids, ref<OrderedListConfig>(dir, directoryProps = props).poll().rewards.map { it.id })
    }
    @Test fun mapWritesKeyedFilesAndReadsBack(@TempDir dir: File) {
        val c = ref<MapDirectoryConfig>(dir, directoryProps = mapProps).poll()
        assertTrue(File(dir, "shops/default-shop.json").exists())
        assertEquals(100, c.shops["default-shop"]?.price)
    }
    @Test fun nestedWritesSubdirAndReadsBack(@TempDir dir: File) {
        val c = ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll()
        assertTrue(File(dir, "shops/main/main.json").exists())
        assertEquals(1, File(dir, "shops/main/items").filesWithExtension("json").size)
        assertEquals("armory", c.shops["main"]?.shopName)
        assertEquals("sword", c.shops["main"]?.items?.single()?.itemName)
    }
    @Test fun deeplyNestedWritesAndReadsBack(@TempDir dir: File) {
        val c = ref<DeeplyNestedConfig>(dir, directoryProps = deepProps).poll()
        assertTrue(File(dir, "level1").subdirectories().size == 1)
        val l1 = c.level1.single()
        assertEquals("top-child", l1.name)
        val c1 = l1.containers.getValue("c1")
        assertEquals("mid", c1.tag)
        assertEquals("leaf", c1.items.single().value)
    }
    @Test fun addingElementFileReread(@TempDir dir: File) {
        val r = ref<ListDirectoryConfig>(dir, directoryProps = listProps)
        assertEquals(1, r.poll().rewards.size)
        File(dir, "rewards/extra.json").writeText(J.encodeToString(Reward.serializer(), Reward("gem", 99)))
        assertEquals(2, r.poll().rewards.size)
    }
    @Test fun removingElementFilesReread(@TempDir dir: File) {
        val r = ref<ListDirectoryConfig>(dir, directoryProps = listProps)
        assertEquals(1, r.poll().rewards.size)
        File(dir, "rewards").listFiles()?.forEach { it.delete() }
        assertEquals(0, r.poll().rewards.size)
    }
    @Test fun modifyingElementFileReread(@TempDir dir: File) {
        val r = ref<ListDirectoryConfig>(dir, directoryProps = listProps)
        assertEquals("coin", r.poll().rewards[0].id)
        val f = File(dir, "rewards").listFiles()!!.first()
        f.writeText(J.encodeToString(Reward.serializer(), Reward("diamond", 500)))
        f.bumpMTime()
        assertEquals("diamond", r.poll().rewards[0].id)
    }
    @Test fun deletedDirYieldsEmpty(@TempDir dir: File) {
        val r = ref<ListDirectoryConfig>(dir, directoryProps = listProps)
        r.poll()
        File(dir, "rewards").deleteRecursively()
        File(dir, "config.json").bumpMTime()
        assertTrue(r.poll().rewards.isEmpty())
    }
    @Test fun existingEmptyDirNotSeeded(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        assertEquals(0, ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll().rewards.size)
    }
    @Test fun existingElementsRespected(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/custom.json").writeText(J.encodeToString(Reward.serializer(), Reward("pre", 1)))
        assertEquals("pre", ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll().rewards[0].id)
    }
    @Test fun recreatesMainKeepingElements(@TempDir dir: File) {
        ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        File(dir, "rewards/extra.json").writeText(J.encodeToString(Reward.serializer(), Reward("extra", 77)))
        File(dir, "config.json").delete()
        val c = ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        assertTrue(File(dir, "config.json").exists())
        assertEquals(2, c.rewards.size)
    }
    @Test fun reseedsWhenEverythingDeleted(@TempDir dir: File) {
        ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        File(dir, "rewards").deleteRecursively()
        File(dir, "config.json").delete()
        val c = ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll()
        assertEquals("coin", c.rewards.single().id)
    }
    @Test fun mapExistingRespectedAndRecreatesMain(@TempDir dir: File) {
        File(dir, "shops").mkdirs()
        File(dir, "shops/custom-shop.json").writeText(J.encodeToString(ShopEntry.serializer(), ShopEntry(price = 999, currency = "gems")))
        assertEquals(999, ref<MapDirectoryConfig>(dir, directoryProps = mapProps).poll().shops["custom-shop"]?.price)
        File(dir, "config.json").delete()
        assertTrue(File(dir, "shops/custom-shop.json").exists())
        ref<MapDirectoryConfig>(dir, directoryProps = mapProps).poll()
        assertTrue(File(dir, "config.json").exists())
    }
    @Test fun nestedExistingRespected(@TempDir dir: File) {
        File(dir, "shops/custom").mkdirs()
        File(dir, "shops/custom/custom.json").writeText("""{"shop-name": "my-shop"}""")
        File(dir, "shops/custom/items").mkdirs()
        File(dir, "shops/custom/items/item1.json").writeText(J.encodeToString(ShopItem.serializer(), ShopItem("axe", 75)))
        val c = ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll()
        assertEquals("my-shop", c.shops["custom"]?.shopName)
        assertEquals("axe", c.shops["custom"]?.items?.get(0)?.itemName)
    }
    @Test fun parseErrorInElementFile(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/bad.json").writeText("not json")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        assertFailsWith<ConfigException.ParseError> { ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll() }
    }
    @Test fun deserialisationErrorNamesElementFile(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/bad.json").writeText("""{"id":123,"amount":"nan"}""")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        val e = assertFailsWith<ConfigException.DeserialisationError> { ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll() }
        assertEquals("bad.json", e.file.name)
        assertEquals(typeOf<Reward>(), e.type)
    }
    @Test fun deserialisationErrorNamesNestedEntryFile(@TempDir dir: File) {
        File(dir, "shops/main").mkdirs()
        File(dir, "shops/main/main.json").writeText("""{"shop-name":{"oops":true}}""")
        File(dir, "shops/main/items").mkdirs()
        File(dir, "shops/main/items/i1.json").writeText("""{"item-name":"bow","cost":30}""")
        File(dir, "config.json").writeText("""{"label":"test"}""")
        val e = assertFailsWith<ConfigException.DeserialisationError> { ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll() }
        assertEquals("main.json", e.file.name)
        assertEquals(typeOf<NestedShop>(), e.type)
    }
    @Test fun deserialisationErrorNamesNestedLeafFile(@TempDir dir: File) {
        File(dir, "shops/main").mkdirs()
        File(dir, "shops/main/main.json").writeText("""{"shop-name":"armory"}""")
        File(dir, "shops/main/items").mkdirs()
        File(dir, "shops/main/items/bad.json").writeText("""{"item-name":"x","cost":"nan"}""")
        File(dir, "config.json").writeText("""{"label":"test"}""")
        val e = assertFailsWith<ConfigException.DeserialisationError> { ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll() }
        assertEquals("bad.json", e.file.name)
        assertEquals(typeOf<ShopItem>(), e.type)
    }
    @Test fun scalarDecodeFailureNamesMainFile(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/ok.json").writeText("""{"id":"gem","amount":5}""")
        File(dir, "config.json").writeText("""{"title":{"oops":true}}""")
        val e = assertFailsWith<ConfigException.DeserialisationError> { ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll() }
        assertEquals("config.json", e.file.name)
        assertEquals(typeOf<ListDirectoryConfig>(), e.type)
    }
    @Test fun parseErrorInNestedElementFile(@TempDir dir: File) {
        File(dir, "shops/bad").mkdirs()
        File(dir, "shops/bad/bad.json").writeText("broken")
        File(dir, "config.json").writeText("""{"label":"test"}""")
        assertFailsWith<ConfigException.ParseError> { ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll() }
    }
    @Test fun toleratesUnknownKeysInElementFile(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        File(dir, "rewards/r.json").writeText("""{"id":"gem","amount":5,"extra":true}""")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        assertEquals("gem", ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll().rewards[0].id)
    }
    @Test fun elementFilesSortedNaturally(@TempDir dir: File) {
        File(dir, "rewards").mkdirs()
        for ((n, id) in listOf("10" to "ten", "2" to "two", "1" to "one")) File(dir, "rewards/$n.json").writeText("""{"id":"$id","amount":1}""")
        File(dir, "config.json").writeText("""{"title":"test"}""")
        assertEquals(listOf("one", "two", "ten"), ref<ListDirectoryConfig>(dir, directoryProps = listProps).poll().rewards.map { it.id })
    }
    @Test fun nestedSubdirsSortedNaturally(@TempDir dir: File) {
        for (n in listOf("10-armory", "2-potions", "1-weapons")) {
            File(dir, "shops/$n").mkdirs()
            File(dir, "shops/$n/$n.json").writeText("""{"shop-name":"$n"}""")
            File(dir, "shops/$n/items").mkdirs()
            File(dir, "shops/$n/items/a.json").writeText("""{"item-name":"item-$n","cost":1}""")
        }
        File(dir, "config.json").writeText("""{"label":"test"}""")
        assertEquals(listOf("1-weapons", "2-potions", "10-armory"), ref<NestedDirectoryConfig>(dir, directoryProps = nestedProps).poll().shops.keys.toList())
    }
}
