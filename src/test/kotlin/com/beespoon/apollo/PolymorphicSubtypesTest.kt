package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class PolymorphicSubtypesTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun explicitSubtypesRoundTripWithoutScan(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; polymorphicSubtypes<Shape>(Circle::class, Square::class); polymorphicSubtypes<Animal>(Dog::class); register<DrawingConfig>() }
        assertEquals(Circle(), apollo.config<DrawingConfig>().value.shape)
        assertEquals(Dog(), apollo.config<DrawingConfig>().value.pet)
        assertTrue(File(dir, "drawing.json").readText().contains("\"circle\""))
    }
    @Test fun explicitDuplicateOfScannedSubtypeRegistersOnce(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; classLoader = Shape::class.java.classLoader; packages("com.beespoon.apollo.testdata"); polymorphicSubtypes<Shape>(Circle::class); register<DrawingConfig>() }
        assertEquals(Circle(), apollo.config<DrawingConfig>().value.shape)
    }
    @Test fun nonConcreteExplicitSubtypesAreIgnored(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; polymorphicSubtypes<Shape>(Shape::class, Circle::class); polymorphicSubtypes<Animal>(Animal::class, Dog::class); register<DrawingConfig>() }
        assertEquals(Circle(), apollo.config<DrawingConfig>().value.shape)
        assertEquals(Dog(), apollo.config<DrawingConfig>().value.pet)
    }
}
