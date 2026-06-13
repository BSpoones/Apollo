package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import kotlinx.serialization.modules.*
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class PolymorphicTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun discoversAndRoundTripsSubtypes(@TempDir dir: File) {
        Apollo.build { basePath = dir; classLoader = Shape::class.java.classLoader; packages("com.beespoon.apollo") }.register<DrawingConfig>()
        val cfg: DrawingConfig by config<DrawingConfig>()
        assertIs<Circle>(cfg.shape); assertIs<Dog>(cfg.pet)
        assertTrue(File(dir, "drawing.json").readText().contains("\"circle\""))
        File(dir, "drawing.json").writeText("""{"shape":{"type":"square","side":9},"pet":{"type":"dog","pet-name":"rex"}}""")
        File(dir, "drawing.json").bumpMTime()
        config<DrawingConfig>().poll()
        val reloaded: DrawingConfig by config<DrawingConfig>()
        assertIs<Square>(reloaded.shape)
        assertEquals(9, (reloaded.shape as Square).side)
    }
    @Test fun customSerializerModuleApplied(@TempDir dir: File) {
        Apollo.build { basePath = dir; serializers(SerializersModule { contextual(Colour::class, ColourSerializer) }) }.register<ThemeConfig>()
        val cfg: ThemeConfig by config<ThemeConfig>()
        assertEquals(255, cfg.colour.rgb)
        assertEquals("255", File(dir, "theme.json").read()["colour"].toString())
    }
}
