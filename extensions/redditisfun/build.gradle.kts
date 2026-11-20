dependencies {
    compileOnly(libs.morphe.extensions.library)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation(libs.okhttp)
}
