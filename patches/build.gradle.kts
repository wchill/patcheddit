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

    implementation(libs.morphe.patches.library)

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

sourceSets.main {
    resources.srcDir(prepareHttpLibraryResources)
}

tasks {
    test {
        useJUnit()
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