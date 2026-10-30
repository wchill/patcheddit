dependencies {
    implementation(project(":extensions:shared:library"))
    implementation(libs.okhttp.transport)
    compileOnly(libs.okhttp)
}

android {
    buildTypes.getByName("release") {
        proguardFiles("proguard-http.pro")
    }
}
