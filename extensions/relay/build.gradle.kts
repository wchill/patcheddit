dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:relay:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
}
