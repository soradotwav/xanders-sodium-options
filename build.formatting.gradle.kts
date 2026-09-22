plugins {
    id("com.diffplug.spotless")
}

layout.buildDirectory = rootProject.layout.buildDirectory.dir("formatting")

repositories {
    mavenCentral()
}

spotless {
    java {
        target("main/java/**/*.java")
        // Preserve Stonecutter directives, inactive branches, and conditional imports.
        eclipse("4.38").configFile(rootProject.file("gradle/eclipse-formatter.properties"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
