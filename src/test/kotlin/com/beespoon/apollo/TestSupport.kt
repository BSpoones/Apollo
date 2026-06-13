package com.beespoon.apollo
import java.io.File
import kotlin.reflect.*
import kotlin.test.assertTrue
import kotlinx.serialization.json.*
import com.beespoon.apollo.format.*
import com.beespoon.apollo.reference.*
import com.beespoon.apollo.reload.ChangeDetection
import org.slf4j.*
internal val J = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; namingStrategy = JsonNamingStrategy.KebabCase }
internal val REG = FormatRegistry().also { it.register("json", JsonFormatCodec(J)) }
internal val LOG: Logger = LoggerFactory.getLogger("ApolloTest")
internal inline fun <reified T : Any> ref(dir: File, fileName: String = "config.json", directoryProps: List<DirectoryPropertyDescriptor> = emptyList(), detection: ChangeDetection = ChangeDetection.MODIFICATION_TIME) = FileConfigReference<T>(file = File(dir, fileName), type = typeOf<T>(), directoryProps = directoryProps, json = J, codec = REG["json"], logger = LOG, detection = detection)
internal fun discover(clazz: KClass<*>) = DirectoryPropertyDescriptor.discover(clazz, JsonNamingStrategy.KebabCase)
internal fun File.read() = J.parseToJsonElement(readText()).jsonObject
internal fun File.bumpMTime() { setLastModified(lastModified() + 1000) }
internal fun awaitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(5) }
    assertTrue(condition(), "condition not met within ${timeoutMs}ms")
}
