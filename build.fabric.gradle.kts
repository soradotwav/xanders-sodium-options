plugins {
    id("net.fabricmc.fabric-loom-remap")
}

version = "${property("mod.version")}+${sc.current.version}-fabric"
group = "dev.isxander"

base {
    archivesName = property("mod.name") as String
}

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.flashyreese.me/releases")
    maven("https://maven.bawnorton.com/releases")
    maven("https://maven.fallenbreath.me/releases")
    maven("https://maven.flashyreese.me/snapshots")
    maven("https://maven.shedaniel.me")
    maven("https://maven.terraformersmc.com")
    maven("https://jitpack.io")
    maven("https://maven.gegy.dev")
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") { name = "Modrinth" }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    // Base Fabric setup (Keep these!)
    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric")}")

    // Required Dependencies
    modImplementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
    }
    include("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")

    // Sodium (Required)
    modImplementation("maven.modrinth:sodium:${property("deps.sodium")}")
    // (ModMenu is standard for Fabric configs)
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")
    modRuntimeOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

    // Optional Compat: Sodium Extra
    modCompileOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
    modRuntimeOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")

    // Optional Compat: More Culling
    modCompileOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")
    modRuntimeOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")

    // Optional Compat: Iris
    modCompileOnly("maven.modrinth:iris:${property("deps.iris")}")
    modRuntimeOnly("maven.modrinth:iris:${property("deps.iris")}")

    // Optional Compat: LambDynamicLights
    modCompileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}")
    modRuntimeOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}")

    // Optional Compat: SpruceUI (Required by LDL)
    modCompileOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}")
    modRuntimeOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}")

    // Internal libraries (YACL/MixinSquared)
    include(modImplementation("net.caffeinemc:CaffeineConfig:1.3.0+1.17")!!)
    include(modImplementation("me.fallenbreath:conditional-mixin-fabric:+")!!)

    modRuntimeOnly("maven.modrinth:cloth-config:${property("runtime.cloth")}")

    include(implementation(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.2.0")!!)!!)
}

tasks.processResources {
    exclude("META-INF/neoforge.mods.toml")

    val props = mapOf(
            "mod_version" to project.version,
            "target_minecraft" to project.property("mod.target"),
            "target_sodium" to project.property("target.sodium"),
            "target_fabricloader" to project.property("deps.fabric_loader")
    )

    inputs.properties(props)

    filesMatching("fabric.mod.json") {
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
    from(tasks.named("remapJar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/fabric"))
    dependsOn("build")
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "../../run"
    }
}
