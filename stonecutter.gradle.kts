plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.15-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.85" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
}

stonecutter active "1.21.11-fabric" /* [SC] DO NOT EDIT */

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')
    constants["fabric"] = loader == "fabric"
    constants["neoforge"] = loader == "neoforge"
    swaps["mod_version"] = "\"${property("mod.version")}\";"
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target(fileTree("src") {
                include("**/*.java")
            })
            palantirJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
