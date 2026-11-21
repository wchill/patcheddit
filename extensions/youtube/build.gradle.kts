dependencies {
    implementation(libs.youtube.player) {
        isTransitive = false
    }
    implementation(kotlin("stdlib"))
    compileOnly(libs.annotation)
    testImplementation("junit:junit:4.13.2")
}
