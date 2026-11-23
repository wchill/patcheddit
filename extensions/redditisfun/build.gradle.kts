dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":extensions:shared:library"))
    testImplementation(libs.okhttp)
}
