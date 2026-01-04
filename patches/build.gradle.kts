group = "app.morphe"

patches {
    about {
        name = "RVX Ported"
        description = "RVX Patches for Morphe"
        source = "git@github.com:wchill/patcheddit.git"
        author = "wchill & inotia00"
        contact = "https://github.com/wchill"
        website = "https://github.com/wchill/patcheddit"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Used by JsonGenerator.
    implementation(libs.gson)
}

tasks {
    jar {
        exclude("app/morphe/generator")
    }
    register<JavaExec>("generatePatchesFiles") {
        description = "Generate patches files"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.generator.MainKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesFiles")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
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