dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:continuum:stub"))
    compileOnly(libs.androidx.preference)
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
}
