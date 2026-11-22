plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    compileOnly(libs.androidx.preference.boost)
    implementation(libs.morphe.extensions.library)
    compileOnly(libs.bundles.boost)
    compileOnly(project(":extensions:boost:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp.boost)
    implementation(libs.jackson.databind.boost)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp.boost)
}

android {
    buildTypes.getByName("release") {
        proguardFiles("proguard-json.pro")
    }
    sourceSets.getByName("main").java.directories.add(
        project(":extensions:shared:library").file("src/main/java").absolutePath
    )
    defaultConfig {
        minSdk = 26
    }
}
