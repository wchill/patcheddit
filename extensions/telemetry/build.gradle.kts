dependencies {
    implementation(libs.sentry)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    compileOnly(libs.material)
}
