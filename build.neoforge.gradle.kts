plugins {
    id("net.neoforged.moddev")
}

version = "${property("mod.version")}+${sc.current.version}"
group = "dev.isxander"

base {
    archivesName = property("mod.name") as String
}

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.bawnorton.com/releases")
    maven("https://maven.neoforged.net/releases/")
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") { name = "Modrinth" }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

neoForge {
    version = property("deps.neoforge") as String

    runs {
        register("client") {
            client()
        }
    }

    mods {
        register("xanders-sodium-options") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // TODO: Add NeoForge-compatible dependencies
    // implementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")
    // implementation("maven.modrinth:sodium:${property("deps.sodium")}")
}

tasks.processResources {
    val props = mapOf(
        "mod_version" to project.version,
        "target_minecraft" to project.property("mod.target"),
        "target_sodium" to project.property("target.sodium")
    )

    inputs.properties(props)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

java {
    val version = JavaVersion.VERSION_21
    sourceCompatibility = version
    targetCompatibility = version
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named("jar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile })
    into(rootProject.layout.buildDirectory.file("libs"))
    dependsOn("build")
}
