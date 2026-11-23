dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:relay:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    testImplementation(project(":extensions:shared:library"))
    testImplementation("junit:junit:4.13.2")
}
