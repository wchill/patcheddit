plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:boostforreddit:stub"))
    implementation(libs.annotation)
    compileOnly(libs.okhttp)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    compilerOptions {
        // OkHttp/Okio are compiled with a newer Kotlin version than the one used by
        // the Morphe plugin. This flag allows KSP to process them without errors.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

