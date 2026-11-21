import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.util.zip.ZipInputStream

group = "app.morphe"

val httpLibraryResources by configurations.creating

patches {
    about {
        name = "Patcheddit"
        description = "Morphe Patches for Reddit"
        source = "git@github.com:wchill/patcheddit.git"
        author = "wchill"
        contact = "w@chill.dev"
        website = "https://github.com/wchill/patcheddit"
        license = "Additional conditions under GPL section 7 apply: attribution and project name restrictions. See LICENSE file."
    }
}

dependencies {
    httpLibraryResources(libs.okhttp.transport) {
        isTransitive = false
    }
    // Used by JsonGenerator.
    implementation(libs.gson)

    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library) {
        // The library still requests the older smali hash, which Gradle sorts above the patcher's.
        exclude(group = "com.github.MorpheApp.smali", module = "smali")
    }

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    testImplementation(kotlin("test-junit"))
}

val prepareHttpLibraryResources by tasks.registering(Sync::class) {
    from({ httpLibraryResources.map { zipTree(it) } }) {
        include("okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz")
        include("okhttp3/internal/publicsuffix/NOTICE")
        into("http")
    }
    into(layout.buildDirectory.dir("generated/http-resources"))
    doLast {
        check(destinationDir.resolve("http/okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz").isFile) {
            "OkHttp public suffix database was not found"
        }
    }
}

val bundledExtensionLibraries = provider {
    rootProject.subprojects.filter { it.path.startsWith(":extensions:") && !it.path.endsWith(":stub") }
        .flatMap { project ->
            project.configurations.findByName("releaseRuntimeClasspath")
                ?.incoming?.artifactView {
                    componentFilter { it is ModuleComponentIdentifier }
                }?.artifacts?.artifacts.orEmpty()
        }
        .map { (it.id.componentIdentifier as ModuleComponentIdentifier) to it.file }
        .distinctBy { it.first.displayName }
        .sortedBy { it.first.displayName }
}

val prepareAboutLicenses by tasks.registering {
    val licenseDirectory = file("src/main/resources/settings/licenses")
    val outputDirectory = layout.buildDirectory.dir("generated/about-resources")
    val output = outputDirectory.map { it.file("settings/raw/patcheddit_licenses.json") }
    inputs.dir(licenseDirectory)
    inputs.files(rootProject.files("LICENSE", "NOTICE"),
        file("src/main/resources/branding-license/LICENSE_MORPHE.TXT"))
    inputs.files(bundledExtensionLibraries.map { libraries -> libraries.map { it.second } })
    outputs.dir(outputDirectory)
    doLast {
        fun document(name: String) = licenseDirectory.resolve(name).readText()
        val gpl = rootProject.file("LICENSE").readText()
        val apache = document("Apache-2.0.txt")
        val notices = mutableListOf(
            mapOf("name" to "Patcheddit", "text" to rootProject.file("NOTICE").readText() + "\n\n" + gpl),
            mapOf("name" to "Morphe and ReVanced-derived code", "text" to
                document("Morphe-patches-NOTICE.txt") + "\n\n" + document("Morphe-library-NOTICE.txt") +
                "\n\n" + file("src/main/resources/branding-license/LICENSE_MORPHE.TXT").readText() + "\n\n" + gpl),
            mapOf("name" to "Morphe branding", "text" to
                "Copyright 2025 Morphe. Morphe branding is not licensed under open-source terms.\n" +
                "https://github.com/MorpheApp/morphe-branding\n\n" +
                "Patcheddit is a separate project, not the official Morphe patches."),
            mapOf("name" to "Google Material Design icons", "text" to
                "https://github.com/google/material-design-icons\n\n$apache"),
        )
        fun embeddedNotices(input: java.io.InputStream, prefix: String = ""): List<String> =
            ZipInputStream(input).use { zip ->
                buildList {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (entry.name == "classes.jar") {
                            addAll(embeddedNotices(zip.readBytes().inputStream(), "$prefix/classes.jar"))
                        } else if (entry.name.substringAfterLast('/').matches(
                                Regex("(?i)(licen[cs]e|notice|copying)([._-].*)?"))) {
                            add("$prefix/${entry.name}\n\n${zip.readBytes().toString(Charsets.UTF_8)}")
                        }
                    }
                }
            }
        for ((id, artifact) in bundledExtensionLibraries.get()) {
            if (id.group == "app.morphe") continue
            check(id.group.startsWith("androidx.") ||
                id.group in setOf("org.jetbrains", "org.jetbrains.kotlin", "org.jetbrains.kotlinx",
                    "com.google.code.gson", "com.google.errorprone", "com.squareup.okhttp3",
                    "com.squareup.okio", "com.fasterxml.jackson.core", "org.jspecify")) {
                "Review the license for the new bundled dependency $id"
            }
            val embedded = artifact.inputStream().use { embeddedNotices(it) }.joinToString("\n\n")
            notices += mapOf("name" to id.displayName, "text" to "${id.displayName}\n\n$embedded\n\n$apache")
        }
        notices += mapOf("name" to "Public Suffix List", "text" to
            "OkHttp's PublicSuffixDatabase.gz is compiled from https://publicsuffix.org/list/public_suffix_list.dat\n" +
            "It is subject to the Mozilla Public License, version 2.0.\n\n" + document("MPL-2.0.txt"))
        notices += mapOf("name" to "OpenJDK desugaring libraries", "text" to
            document("Desugar-LICENSE.txt") + "\n\n" + document("Desugar-ADDITIONAL_LICENSE_INFO.txt"))
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(notices))
        }
    }
}

sourceSets.main {
    resources.srcDir(prepareHttpLibraryResources)
    resources.srcDir(prepareAboutLicenses)
}

val compileBoostFingerprintDump by tasks.registering(JavaCompile::class) {
    source(rootProject.file("scripts/BoostDexFingerprintDump.java"))
    classpath = configurations["runtimeClasspath"]
    destinationDirectory.set(layout.buildDirectory.dir("tools/boost-fingerprints"))
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("dumpBoostDexFingerprints") {
    description = "Exports stock APK bytecode for scripts/update_boost_mapping.py"
    dependsOn(compileBoostFingerprintDump)
    classpath = files(compileBoostFingerprintDump.flatMap { it.destinationDirectory }) +
        configurations["runtimeClasspath"]
    mainClass.set("BoostDexFingerprintDump")
    maxHeapSize = "512m"
    doFirst {
        args(
            providers.gradleProperty("boostFingerprintApk").get(),
            providers.gradleProperty("boostFingerprintOutput").get(),
            providers.gradleProperty("boostFingerprintMetadata").get(),
        )
    }
}

tasks {
    test {
        useJUnit()
        val boostApk = providers.environmentVariable("BOOST_APK").orNull
        inputs.property("boostApk", boostApk.orEmpty())
        if (boostApk != null) {
            val apk = rootProject.file(boostApk)
            inputs.file(apk).withPropertyName("boostApkFixture")
            environment("BOOST_APK", apk.absolutePath)
        }
        for (name in listOf("RIF_FREE_APK", "RIF_PLATINUM_APK")) {
            val path = providers.environmentVariable(name).orNull
            inputs.property(name, path.orEmpty())
            if (path != null) {
                val apk = rootProject.file(path)
                inputs.file(apk).withPropertyName(name)
                environment(name, apk.absolutePath)
            }
        }
    }

    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/wchill/patcheddit")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
