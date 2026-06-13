package com.beespoon.apollo.testdata
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import com.beespoon.apollo.annotation.*
@Serializable data class SimpleConfig(val name: String = "default", val count: Int = 42, val enabled: Boolean = true)
@Serializable data class PlainConfig(val foo: String = "bar", val num: Int = 1)
@Serializable data class PlainDirConfig(val title: String = "top", val rewards: List<Reward> = listOf(Reward()))
@Serializable data class Reward(val id: String = "coin", val amount: Int = 10)
@Serializable data class ListDirectoryConfig(val title: String = "rewards-page", @DirectoryConfig val rewards: List<Reward> = listOf(Reward()))
@Serializable data class ShopEntry(val price: Int = 100, val currency: String = "coins")
@Serializable data class MapDirectoryConfig(val version: Int = 1, @DirectoryConfig val shops: Map<String, ShopEntry> = mapOf("default-shop" to ShopEntry()))
@Serializable data class ShopItem(val itemName: String = "sword", val cost: Int = 50)
@Serializable data class NestedShop(val shopName: String = "armory", @DirectoryConfig val items: List<ShopItem> = listOf(ShopItem()))
@Serializable data class NestedDirectoryConfig(val label: String = "nested", @DirectoryConfig val shops: Map<String, NestedShop> = mapOf("main" to NestedShop()))
@Serializable data class MixedConfig(val scalar: String = "hello", @DirectoryConfig val rewards: List<Reward> = emptyList(), val anotherScalar: Int = 99)
@Serializable data class Level3Item(val value: String = "leaf")
@Serializable data class Level2Container(val tag: String = "mid", @DirectoryConfig val items: List<Level3Item> = listOf(Level3Item()))
@Serializable data class Level1Container(val name: String = "top-child", @DirectoryConfig val containers: Map<String, Level2Container> = mapOf("c1" to Level2Container()))
@Serializable data class DeeplyNestedConfig(val root: String = "root", @DirectoryConfig val level1: List<Level1Container> = listOf(Level1Container()))
@Serializable data class OrderedListConfig(@DirectoryConfig val rewards: List<Reward> = listOf(Reward("alpha", 1), Reward("bravo", 2), Reward("charlie", 3)))
@Serializable data class SimpleConfigV2(val name: String = "default", val count: Int = 42, val enabled: Boolean = true, val newField: String = "added", val newNullable: String? = null, val newInt: Int = 99)
@Serializable data class RewardV2(val id: String = "coin", val amount: Int = 10, val rarity: String = "common", val description: String? = null)
@Serializable data class ListDirectoryConfigV2(val title: String = "rewards-page", val subtitle: String = "new-subtitle", @DirectoryConfig val rewards: List<RewardV2> = listOf(RewardV2()))
@Serializable data class ShopEntryV2(val price: Int = 100, val currency: String = "coins", val discount: Int = 0, val note: String? = null)
@Serializable data class MapDirectoryConfigV2(val version: Int = 1, val region: String = "global", @DirectoryConfig val shops: Map<String, ShopEntryV2> = mapOf("default-shop" to ShopEntryV2()))
@Serializable data class ShopItemV2(val itemName: String = "sword", val cost: Int = 50, val stock: Int = -1, val enchantment: String? = null)
@Serializable data class NestedShopV2(val shopName: String = "armory", val tier: Int = 1, val note: String? = null, @DirectoryConfig val items: List<ShopItemV2> = listOf(ShopItemV2()))
@Serializable data class NestedDirectoryConfigV2(val label: String = "nested", @DirectoryConfig val shops: Map<String, NestedShopV2> = mapOf("main" to NestedShopV2()))
@Serializable data class CustomAnnotationConfig(@DirectoryConfig(name = "my-rewards") val rewards: List<Reward> = emptyList())
@Serializable data class SerialNameConfig(@SerialName("custom-rewards") @DirectoryConfig val rewardItems: List<Reward> = emptyList())
data class InvalidConfig(@DirectoryConfig val notACollection: String = "oops")
data class StarProjectedConfig(@DirectoryConfig val items: List<*> = emptyList<Any>())
@Serializable data class CamelDirectoryConfig(@DirectoryConfig val rewardItems: List<Reward> = emptyList())
@Serializable data class CyclicA(@DirectoryConfig val bs: List<CyclicB> = emptyList())
@Serializable data class CyclicB(@DirectoryConfig val as_: List<CyclicA> = emptyList())
@Serializable @FileConfig data class ScannableConfig(val tag: String = "scanned")
@FileConfig data class BrokenScannableConfig(@DirectoryConfig val notACollection: String = "oops")
@Serializable data class Unregistered(val value: Int = 0)
@Serializable data class Standalone(val value: Int = 0)
@Polymorphic interface Shape
@Serializable @SerialName("circle") data class Circle(val radius: Int = 1) : Shape
@Serializable @SerialName("square") data class Square(val side: Int = 2) : Shape
class Triangle : Shape
@Serializable @Polymorphic abstract class Animal
@Serializable @SerialName("dog") data class Dog(val petName: String = "rex") : Animal()
@Serializable data class DrawingConfig(val shape: Shape = Circle(), val pet: Animal = Dog())
class Colour(val rgb: Int)
object ColourSerializer : KSerializer<Colour> {
    override val descriptor = PrimitiveSerialDescriptor("Colour", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Colour) = encoder.encodeInt(value.rgb)
    override fun deserialize(decoder: Decoder) = Colour(decoder.decodeInt())
}
@Serializable data class ThemeConfig(@Contextual val colour: Colour = Colour(255))
