plugins {
    alias(libs.plugins.ksp)
}

val sharedSources = project(":extensions:shared:library").file("src/main")

dependencies {
    compileOnly(libs.androidx.preference.boost)
    implementation(libs.morphe.extensions.library)
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:boost:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp.boost)
    implementation(project(path = ":extensions:shared:library", configuration = "vendoredArchiveJars"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp.boost)
}

android {
    sourceSets.getByName("main") {
        java.srcDir(sharedSources.resolve("java"))
        kotlin.srcDir(sharedSources.resolve("kotlin"))
    }
    defaultConfig {
        minSdk = 26
    }
}
