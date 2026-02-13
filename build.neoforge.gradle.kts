plugins {
    id("net.neoforged.moddev")
}

version = "${property("mod.version")}+${sc.current.version}"
group = "dev.isxander"

base {
    archivesName = property("mod.name") as String
}

val mappingsAttribute: Attribute<String> = Attribute.of("net.minecraft.mappings", String::class.java)

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.bawnorton.com/releases")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.gegy.dev")
    maven("https://maven.shedaniel.me")
    maven("https://jitpack.io")
    maven("https://maven.caffeinemc.net/releases")
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
    attributesSchema {
        attribute(mappingsAttribute)
    }

    implementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")
    jarJar("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")

    implementation("net.caffeinemc:sodium-neoforge-mod:${property("deps.sodium")}")

    implementation("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
    implementation("maven.modrinth:moreculling:${property("deps.moreculling")}")

    compileOnly("maven.modrinth:iris:${property("deps.iris")}")

    implementation("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}") {
        attributes {
            attribute(mappingsAttribute, "mojmap")
        }
    }

    implementation("dev.lambdaurora:spruceui:${property("deps.spruceui")}") {
        attributes {
            attribute(mappingsAttribute, "mojmap")
        }
    }
    jarJar("dev.lambdaurora:spruceui:[8.0.0,9.0.0)") {
        attributes {
            attribute(mappingsAttribute, "mojmap")
        }
    }
    jarJar("dev.yumi.mc.core:yumi-mc-foundation:1.0.0-alpha.15+1.21.1") {
        attributes {
            attribute(mappingsAttribute, "mojmap")
        }
    }

    runtimeOnly("maven.modrinth:cloth-config:${property("runtime.cloth")}")

    implementation(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.2.0")!!)
    jarJar("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.2.0")
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
