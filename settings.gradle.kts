rootProject.name = "patcheddit"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN"))
            }
        }
        // Obtain baksmali/smali from source builds - https://github.com/iBotPeaches/smali
        // Remove when official smali releases come out again.
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "2.0.0"
}

settings {
    extensions {
        defaultNamespace = "app.morphe.extension"

        // Must resolve to an absolute path (not relative),
        // otherwise the extensions in subfolders will fail to find the proguard config.
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}

include(":patches:stub")

// Use the published library unless a local composite build is explicitly requested.
if (providers.gradleProperty("useLocalMorphePatchesLibrary").orNull == "true") {
    val libDir = file("../morphe-patches-library")
    require(libDir.isDirectory) { "Local morphe-patches-library checkout was not found." }
    includeBuild(libDir) {
        dependencySubstitution {
            substitute(module("app.morphe:morphe-patches-library")).using(project(":patch-library"))
            substitute(module("app.morphe:morphe-extensions-library")).using(project(":extension-library"))
        }
    }
}
