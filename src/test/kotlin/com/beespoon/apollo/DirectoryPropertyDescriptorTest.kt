package com.beespoon.apollo
import kotlin.test.*
import com.beespoon.apollo.reference.DirectoryPropertyDescriptor
import com.beespoon.apollo.testdata.*
class DirectoryPropertyDescriptorTest {
    @Test fun collectionMode() {
        val d = discover(ListDirectoryConfig::class)
        assertEquals(1, d.size)
        assertEquals(DirectoryPropertyDescriptor.Mode.COLLECTION, d[0].mode)
        assertEquals("rewards", d[0].propertyName)
        assertEquals(Reward::class, d[0].elementType)
    }
    @Test fun mapMode() {
        val d = discover(MapDirectoryConfig::class)
        assertEquals(DirectoryPropertyDescriptor.Mode.MAP, d[0].mode)
        assertEquals(ShopEntry::class, d[0].elementType)
    }
    @Test fun skipsUnannotated() { assertTrue(discover(PlainConfig::class).isEmpty()) }
    @Test fun customName() {
        val d = discover(CustomAnnotationConfig::class)[0]
        assertEquals("my-rewards", d.dirName)
    }
    @Test fun serialNameOverridesKebab() { assertEquals("custom-rewards", discover(SerialNameConfig::class)[0].serialName) }
    @Test fun kebabCaseDefault() { assertEquals("rewards", discover(ListDirectoryConfig::class)[0].serialName) }
    @Test fun nullStrategyKeepsPropertyName() {
        val d = DirectoryPropertyDescriptor.discover(CamelDirectoryConfig::class, namingStrategy = null)
        assertEquals("rewardItems", d[0].serialName)
    }
    @Test fun errorOnNonCollection() { assertFailsWith<IllegalStateException> { discover(InvalidConfig::class) } }
    @Test fun errorOnUnresolvableElementType() { assertFailsWith<IllegalStateException> { discover(StarProjectedConfig::class) } }
    @Test fun kebabCasesCamelPropertyName() { assertEquals("reward-items", discover(CamelDirectoryConfig::class)[0].serialName) }
    @Test fun cycleDetected() { assertFailsWith<IllegalStateException> { discover(CyclicA::class) } }
    @Test fun deeplyNested() {
        val l1 = discover(DeeplyNestedConfig::class)[0]
        assertEquals(Level1Container::class, l1.elementType)
        val l2 = l1.children[0]
        assertEquals(Level2Container::class, l2.elementType)
        val l3 = l2.children[0]
        assertEquals(Level3Item::class, l3.elementType)
        assertTrue(l3.children.isEmpty())
    }
    @Test fun mixedKeepsOnlyAnnotated() {
        val d = discover(MixedConfig::class)
        assertEquals(1, d.size); assertEquals("rewards", d[0].propertyName)
    }
}
