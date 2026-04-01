plugins {
    alias(libs.plugins.android.library) 
}

android {
    namespace = "app.morphe.extension"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.morphe.extensions.library)
    implementation(libs.gson)
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
}
