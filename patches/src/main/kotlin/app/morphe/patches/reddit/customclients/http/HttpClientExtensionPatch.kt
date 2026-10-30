/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableAnnotation.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.misc.extension.clientExtensionPatch
import app.morphe.patches.reddit.customclients.misc.extension.mapping.DexMappingRewriter
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import app.morphe.patches.reddit.customclients.misc.extension.mapping.forEachReference
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.util.FieldUtil
import com.android.tools.smali.dexlib2.util.MethodUtil
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayInputStream
import java.util.logging.Logger

private val httpLibraryResourcesPatch = resourcePatch(default = false) {
    execute {
        for (name in listOf("PublicSuffixDatabase.gz", "NOTICE")) {
            val resource = "okhttp3/internal/publicsuffix/$name"
            val input = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("http/$resource")) {
                "Bundled OkHttp resource was not found: $resource"
            }
            input.use { source ->
                this["${HTTP_LIBRARY_PREFIX.removePrefix("L")}$resource", false].outputStream().use(source::copyTo)
            }
        }
    }
}

internal fun httpClientExtensionPatch(
    client: String,
    vararg hookedClasses: String,
): BytecodePatch {
    var nativeClasses = emptyList<ClassDef>()
    lateinit var profile: HttpLibraryProfile
    var packageName = ""
    val packageMetadata = resourcePatch(default = false) {
        execute {
            document("AndroidManifest.xml").use {
                packageName = it.documentElement.getAttribute("package")
            }
        }
    }
    val native = bytecodePatch(default = false) {
        dependsOn(packageMetadata)
        execute {
            nativeClasses = buildList { classDefForEach(::add) }
            val resourceNamespace = if (client == "redditisfun") {
                when (packageName) {
                    "com.andrewshu.android.reddit" -> "redditisfun/free"
                    "com.andrewshu.android.redditdonation" -> "redditisfun/donation"
                    else -> error("Unsupported RIF HTTP library profile: $packageName")
                }
            } else {
                client
            }
            val input = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("$resourceNamespace/mapping.txt")) {
                "HTTP library mapping for $resourceNamespace was not found"
            }
            profile = input.reader().use(HttpLibraryProfile::read)
        }
    }
    val extension = clientExtensionPatch(client, hookedClasses) { input ->
        val dex = input.use { DexBackedDexFile.fromInputStream(null, it.buffered()) }
        val definitions = dex.classes.toList()
        val names = definitions.filter { privateSharedLibraryType(it.type) != it.type }
            .associate { it.type to privateSharedLibraryType(it.type) }
        check(names.keys.any { it.startsWith("Lokhttp3/") } && names.keys.any { it.startsWith("Lokio/") }) {
            "HTTP library payload is missing OkHttp or Okio"
        }
        val rewritten = DexMappingRewriter(
            ProguardMapping.classNames(names), definitions, resolveReflectiveFields = false,
        ).rewrite(definitions)
        val pool = DexPool(dex.opcodes)
        rewritten.forEach(pool::internClass)
        val output = MemoryDataStore()
        pool.writeTo(output)
        ByteArrayInputStream(output.data)
    }
    return bytecodePatch(default = false) {
        dependsOn(native, httpLibraryResourcesPatch, extension)
        execute {
            val hook = "Lapp/morphe/extension/$client/http/OkHttpRequestHook;"
            val builder = privateHttpType($$"Lokhttp3/OkHttpClient$Builder;")
            val constructor = mutableClassDefBy(privateHttpType("Lokhttp3/OkHttpClient;")).methods.single {
                it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf(builder)
            }
            constructor.addInstructions(
                0,
                """
                    invoke-static {}, $hook->init()V
                    invoke-static/range {p1 .. p1}, $hook->installInterceptor($builder)$builder
                    move-result-object p1
                """,
            )
        }
        finalize {
            val definitions = buildList { classDefForEach(::add) }
            val completeTypes = definitions.map { it.type }.filter { it.startsWith(HTTP_LIBRARY_PREFIX) }
            val canonicalTypes = completeTypes.associate {
                "L${it.removePrefix(HTTP_LIBRARY_PREFIX)}" to it
            }.filterKeys { privateHttpType(it) != it }
            val nativeMapping = profile.mapping.withNestedClasses(nativeClasses.map { it.type })
            val redirected = nativeMapping.mapTargets(::privateHttpType)
                .withBindings(canonicalTypes, emptyMap(), emptyMap())
            val rewriter = DexMappingRewriter(redirected, definitions, resolveReflectiveFields = false)
            val callers = definitions.filter { cls ->
                if (profile.isNative(cls.type) || cls.type.startsWith(HTTP_LIBRARY_PREFIX)) return@filter false
                var affected = false
                cls.forEachReference { reference ->
                    if (reference is TypeReference && redirected.type(reference.type) != reference.type) {
                        affected = true
                    }
                }
                affected
            }
            for (rewritten in rewriter.rewrite(callers)) {
                val target = mutableClassDefBy(rewritten.type)
                target.setSuperClass(rewritten.superclass)
                target.setAccessFlags(rewritten.accessFlags)
                target.interfaces.apply { clear(); addAll(rewritten.interfaces) }
                target.annotations.apply { clear(); addAll(rewritten.annotations.map { it.toMutable() }) }
                val fields = rewritten.fields.map { it.toMutable() }
                target.fields.apply { clear(); addAll(fields) }
                target.staticFields.apply { clear(); addAll(fields.filter(FieldUtil::isStatic)) }
                target.instanceFields.apply { clear(); addAll(fields.filterNot(FieldUtil::isStatic)) }
                val methods = rewritten.methods.map { it.toMutable() }
                target.methods.apply { clear(); addAll(methods) }
                target.directMethods.apply { clear(); addAll(methods.filter(MethodUtil::isDirect)) }
                target.virtualMethods.apply { clear(); addAll(methods.filterNot(MethodUtil::isDirect)) }
            }
            val callerTypes = callers.mapTo(hashSetOf()) { it.type }
            verifyHttpLibraryReferences(buildList { classDefForEach(::add) }, callerTypes)
            Logger.getLogger("HttpClientExtension").info(
                "Redirected ${callers.size} $client classes to complete private OkHttp/Okio libraries",
            )
        }
    }
}
