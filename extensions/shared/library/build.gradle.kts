plugins {
    alias(libs.plugins.android.library) 
}

android {
    namespace = "app.morphe.extension.patcheddit"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(libs.morphe.extensions.library)
    implementation(libs.gson)
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
}
