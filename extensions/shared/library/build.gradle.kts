plugins {
    alias(libs.plugins.android.library) 
}

android {
    namespace = "app.morphe.extension.jeddit"
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
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp)
    testImplementation("org.json:json:20240303")
}
