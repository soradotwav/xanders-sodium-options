plugins {
    id("net.neoforged.moddev")
}

version = "${property("mod.version")}+${sc.current.version}-neoforge"
group = "dev.isxander"

base {
    archivesName = property("mod.name") as String
}

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases") {
        content {
            includeGroup("dev.isxander")
            includeGroup("org.quiltmc.parsers")
        }
    }
    maven("https://maven.gegy.dev") {
        content {
            includeGroupByRegex("dev\\.lambdaurora.*")
            includeGroupByRegex("dev\\.yumi.*")
            includeGroup("io.github.queerbric")
        }
    }
    maven("https://maven.caffeinemc.net/releases") {
        content { includeGroup("net.caffeinemc") }
    }
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") { name = "Modrinth" }
        }
        filter { includeGroup("maven.modrinth") }
    }
}

neoForge {
    version = property("deps.neoforge") as String
    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/26.1-neoforge")
        }
    }
    mods {
        register("xanders_sodium_options") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val withCompatMods = providers.gradleProperty("xso.compatRuntime").map { it.toBoolean() }.getOrElse(true)

dependencies {
    implementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")
    jarJar("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")
    implementation("net.caffeinemc:sodium-neoforge-mod:${property("deps.sodium")}")
    runtimeOnly("net.caffeinemc:sodium-neoforge:${property("deps.sodium")}")

    compileOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
    compileOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")
    compileOnly("maven.modrinth:iris:${property("deps.iris")}")

    // Universal jars include NeoForge metadata. Avoid their Fabric-specific runtime variants.
    compileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}")
    compileOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}")
    // Use -Pxso.compatRuntime=false to test with only required dependencies.
    if (withCompatMods) {
        runtimeOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
        runtimeOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")
        runtimeOnly("maven.modrinth:iris:${property("deps.iris")}")
        runtimeOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}@jar")
        runtimeOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:${property("deps.lambdynamiclights")}@jar")
        runtimeOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}@jar")
        runtimeOnly("dev.yumi.mc.core:yumi-mc-foundation:${property("deps.yumi")}@jar")
        runtimeOnly("maven.modrinth:cloth-config:${property("runtime.cloth")}")
    }
}

tasks.processResources {
    exclude("fabric.mod.json")
    val props = mapOf(
        "mod_version" to project.version,
        "target_minecraft" to project.property("mod.target"),
        "target_sodium" to project.property("target.sodium"),
        "target_neoforge" to "[${project.property("deps.neoforge")},)",
        "target_minecraft_range" to "[${project.property("mod.target")}]",
        "target_yacl" to "[3.9.7,)",
        "target_java" to 25
    )
    inputs.properties(props)
    inputs.property("mixin_compatibility", "JAVA_25")

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
    filesMatching("xanders-sodium-options.mixins.json") {
        filter { line ->
            line.replace("    \"yacl.TextScaledButtonWidgetMixin\",", "")
                .replace("JAVA_21", "JAVA_25")
        }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
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
