dependencies {
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:boostforreddit:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    defaultConfig {
        minSdk = 26
    }
}
