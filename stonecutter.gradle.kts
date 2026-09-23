plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.15.5" apply false
    id("net.fabricmc.fabric-loom") version "1.15.5" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("com.diffplug.spotless") version "8.2.1" apply false
}

stonecutter active "1.21.11-fabric" /* [SC] DO NOT EDIT */

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')
    constants["fabric"] = loader == "fabric"
    constants["neoforge"] = loader == "neoforge"
    swaps["mod_version"] = "\"${property("mod.version")}\";"
}

tasks.register("buildRelease") {
    group = "build"
    val targets = provider {
        subprojects.filter { it.projectDir.parentFile == rootProject.file("versions") }
    }
    dependsOn(targets.map { projects -> projects.map { "${it.path}:buildAndCollect" } })
    doLast {
        val artifacts = targets.get().map { target ->
            val loader = target.name.substringAfterLast('-')
            val collect = target.tasks.named<Copy>("buildAndCollect").get()
            val artifact = collect.source.singleFile
            mapOf(
                "target" to target.name,
                "minecraft" to target.name.substringBeforeLast('-'),
                "loader" to loader,
                "version" to target.version.toString(),
                "path" to collect.destinationDir.resolve(artifact.name).relativeTo(rootDir).invariantSeparatorsPath
            )
        }
        check(artifacts.isNotEmpty()) { "No registered release targets" }
        val output = layout.buildDirectory.file("libs/release-artifacts.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(artifacts)) + "\n")
    }
}
