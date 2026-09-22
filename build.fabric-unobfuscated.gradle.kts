plugins {
    id("net.fabricmc.fabric-loom")
}

version = "${property("mod.version")}+${sc.current.version}-fabric"
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
    maven("https://maven.terraformersmc.com") {
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.gegy.dev") {
        content {
            includeGroupByRegex("dev\\.lambdaurora.*")
            includeGroupByRegex("dev\\.yumi.*")
            includeGroup("io.github.queerbric")
        }
    }
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
    // Unobfuscated Minecraft uses ordinary dependency configurations.
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    runtimeOnly("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric")}")

    // Required Dependencies
    implementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
    }
    include("dev.isxander:yet-another-config-lib:${property("deps.yacl")}")

    // Sodium (Required)
    implementation("maven.modrinth:sodium:${property("deps.sodium")}")
    // ModMenu entrypoint and development menu.
    compileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")
    runtimeOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

    // Optional Compat: Sodium Extra
    compileOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")
    runtimeOnly("maven.modrinth:sodium-extra:${property("deps.sodium-extra")}")

    // Optional Compat: More Culling
    compileOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")
    runtimeOnly("maven.modrinth:moreculling:${property("deps.moreculling")}")

    // Optional Compat: Iris
    compileOnly("maven.modrinth:iris:${property("deps.iris")}")
    runtimeOnly("maven.modrinth:iris:${property("deps.iris")}")

    // FancyMenu has no 26.1 release; its @Pseudo mixin does not need a compile dependency.

    // Optional Compat: LambDynamicLights
    compileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}")
    runtimeOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${property("deps.lambdynamiclights")}")

    // Optional Compat: SpruceUI (Required by LDL)
    compileOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}")
    runtimeOnly("dev.lambdaurora:spruceui:${property("deps.spruceui")}")

    runtimeOnly("maven.modrinth:cloth-config:${property("runtime.cloth")}")

}

tasks.processResources {
    exclude("META-INF/neoforge.mods.toml")

    val fixMacTabScroll = sc.current.version == "26.3"
    inputs.property("fix_mac_tab_scroll", fixMacTabScroll)

    // YACL 3.9 uses TooltipButtonWidget for reset controls; the old scaling patch is obsolete.
    filesMatching("xanders-sodium-options.mixins.json") {
        filter { line ->
            val withScrollMixin = if (fixMacTabScroll) {
                line.replace("\"yacl.YACLScreenMixin\",", "\"yacl.YACLScreenMixin\", \"yacl.ScrollableNavigationBarMixin\",")
            } else line
            withScrollMixin.replace("    \"yacl.TextScaledButtonWidgetMixin\",", "")
                .replace("JAVA_21", "JAVA_25")
        }
    }

    val props = mapOf(
            "mod_version" to project.version,
            "target_minecraft" to project.property("mod.target"),
            "target_sodium" to project.property("target.sodium"),
            "target_fabricloader" to project.property("deps.fabric_loader"),
            "target_java" to 25,
            "target_yacl" to project.property("target.yacl")
    )

    inputs.properties(props)
    inputs.property("mixin_compatibility", "JAVA_25")

    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    val version = JavaVersion.VERSION_25
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
    into(rootProject.layout.buildDirectory.dir("libs/fabric"))
    dependsOn("build")
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "../../run/${sc.current.version}-fabric"
    }
}
