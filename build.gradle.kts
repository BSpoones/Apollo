import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "com.beespoon"

version = findProperty("version")?.toString()?.takeUnless { it == "unspecified" } ?: "1.0"

repositories {
    mavenCentral()
}

dependencies {

    implementation(kotlin("reflect"))

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("io.github.classgraph:classgraph:4.8.174")

    implementation("org.slf4j:slf4j-api:2.0.16")

    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")
    testImplementation("dev.zacsweers.kctfork:ksp:0.8.0")
    testImplementation("org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable:2.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)

        freeCompilerArgs.addAll("-Xjsr305=strict", "-jvm-default=no-compatibility")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy("com.beespoon.apollo.util.CoverageIgnore")
            }
        }
        total {
            verify {
                rule {
                    minBound(100)
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signing {
        useGpgCmd()
    }

    coordinates("com.beespoon", "apollo", project.version.toString())

    pom {
        name.set("Apollo")
        description.set("Minimal hot-reload configuration management for the JVM")
        url.set("https://github.com/BSpoones/Apollo")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("beespoon")
                name.set("BeeSpoon")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/BSpoones/Apollo.git")
            developerConnection.set("scm:git:ssh://github.com/BSpoones/Apollo.git")
            url.set("https://github.com/BSpoones/Apollo")
        }
    }
}
