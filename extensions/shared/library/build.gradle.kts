import org.gradle.api.attributes.java.TargetJvmEnvironment
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val vendoredGuava by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named(TargetJvmEnvironment.ANDROID))
}
val vendoredJackson by configurations.creating {
    isCanBeConsumed = false
}
val vendoringR8 by configurations.creating {
    isCanBeConsumed = false
}
val vendoredArchiveJars by configurations.creating {
    isCanBeResolved = false
}
val vendoredGuavaJar = layout.buildDirectory.file("vendored/guava.jar")
val vendoredJacksonJar = layout.buildDirectory.file("vendored/jackson.jar")

val vendorGuava by tasks.registering(JavaExec::class) {
    val keepRules = layout.projectDirectory.file("proguard-vendored-guava.pro")
    classpath = vendoringR8
    mainClass.set("com.android.tools.r8.R8")
    inputs.files(vendoredGuava)
    inputs.file(keepRules)
    outputs.file(vendoredGuavaJar)
    doFirst {
        val output = vendoredGuavaJar.get().asFile
        output.parentFile.mkdirs()
        args("--classfile", "--no-data-resources", "--lib", javaLauncher.get().metadata.installationPath.asFile,
            "--pg-conf", keepRules.asFile, "--output", output, vendoredGuava.singleFile)
    }
}

val vendorJackson by tasks.registering(JavaExec::class) {
    val keepRules = layout.projectDirectory.file("proguard-vendored-jackson.pro")
    val mapping = layout.buildDirectory.file("vendored/jackson-mapping.txt")
    val mappingRules = layout.buildDirectory.file("vendored/jackson-mapping.pro")
    classpath = vendoringR8
    mainClass.set("com.android.tools.r8.R8")
    inputs.files(vendoredJackson)
    inputs.file(keepRules)
    outputs.files(vendoredJacksonJar, mapping, mappingRules)
    doFirst {
        val output = vendoredJacksonJar.get().asFile
        output.parentFile.mkdirs()
        val classNames = vendoredJackson.flatMap { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().map { it.name }
                    .filter { it.startsWith("com/fasterxml/jackson/") && it.endsWith(".class") }
                    .map { it.removeSuffix(".class").replace('/', '.') }.toList()
            }
        }.sorted()
        check(classNames.isNotEmpty()) { "No Jackson classes to relocate" }
        mapping.get().asFile.writeText(classNames.joinToString("\n", postfix = "\n") {
            "$it -> app.morphe.extension.vendored.$it:"
        })
        mappingRules.get().asFile.writeText("-applymapping '${mapping.get().asFile.name}'\n")
        args("--classfile", "--no-data-resources", "--lib", javaLauncher.get().metadata.installationPath.asFile,
            "--pg-conf", keepRules.asFile, "--pg-conf", mappingRules.get().asFile,
            "--output", output, *vendoredJackson.files.toTypedArray())
    }
}

artifacts {
    add(vendoredArchiveJars.name, vendoredGuavaJar) { builtBy(vendorGuava) }
    add(vendoredArchiveJars.name, vendoredJacksonJar) { builtBy(vendorJackson) }
}

android {
    namespace = "app.morphe.extension.patcheddit"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(libs.morphe.extensions.library)
    implementation(libs.gson)
    compileOnly(libs.annotation)
    compileOnly(libs.okhttp)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    vendoredGuava(libs.guava.android)
    vendoredJackson(libs.jackson.databind.boost)
    vendoringR8(libs.r8.vendoring)
    implementation(files(vendoredGuavaJar).builtBy(vendorGuava))
    api(files(vendoredJacksonJar).builtBy(vendorJackson))
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp)
    testImplementation("org.json:json:20240303")
}
