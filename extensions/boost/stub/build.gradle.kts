plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    compileOnly(libs.bundles.boost)
    compileOnly(libs.okhttp.boost)
}

android {
    namespace = "app.morphe.extension"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        coreLibraryDesugaring(libs.desugar.jdk.libs)
    }
}
