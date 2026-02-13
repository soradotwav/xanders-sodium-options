plugins {
    id("net.neoforged.moddev")
}

version = "${property("mod.version")}+${sc.current.version}-neoforge"
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
        register("xanders_sodium_options") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    attributesSchema {
        attribute(mappingsAttribute)
    }

    // Required Dependencies
    implementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")
    jarJar("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")

    // Sodium (Required)
    implementation("net.caffeinemc:sodium-neoforge-mod:${property("deps.sodium")}")
    runtimeOnly("net.caffeinemc:sodium-neoforge:${property("deps.sodium")}")

    // Optional Compat: Sodium Extra
    compileOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
    runtimeOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")

    // Optional Compat: More Culling
    compileOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")
    runtimeOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")

    // Optional Compat: Iris
    compileOnly("maven.modrinth:iris:${property("deps.iris")}")
    //runtimeOnly("maven.modrinth:iris:${property("deps.iris")}")

    // Optional Compat: LambDynamicLights
    compileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}") {
        attributes { attribute(mappingsAttribute, "mojmap") }
    }
    runtimeOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}") {
        attributes { attribute(mappingsAttribute, "mojmap") }
    }

    // Optional Compat: SpruceUI (Required by LDL)
    compileOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}") {
        attributes { attribute(mappingsAttribute, "mojmap") }
    }
    runtimeOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}") {
        attributes { attribute(mappingsAttribute, "mojmap") }
    }

    // Internal libraries (YACL/MixinSquared)
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
    exclude("fabric.mod.json")

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
    into(rootProject.layout.buildDirectory.dir("libs/neoforge"))
    dependsOn("build")
}
