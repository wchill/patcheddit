plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    compileOnly(libs.androidx.preference.boost)
    implementation(libs.morphe.extensions.library)
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:boost:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp.boost)
    implementation(project(":extensions:shared:library"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp.boost)
}

android {
    defaultConfig {
        minSdk = 26
    }
}
