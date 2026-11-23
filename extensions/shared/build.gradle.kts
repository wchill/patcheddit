dependencies {
    implementation(project(":extensions:shared:library"))
    implementation(libs.okhttp.transport)
    compileOnly(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    buildTypes.getByName("release") {
        proguardFiles("proguard-http.pro")
    }
}
