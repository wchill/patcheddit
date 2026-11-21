/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.blocks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.rifFeatureResources
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val HOOK = "Lapp/morphe/extension/redditisfun/feed/RedditBlocks;"

@Suppress("unused")
val explainRedditBlocksPatch = morpheBytecodePatch(
    name = "Explain Reddit blocks",
    description = "Explains recognized signed-in feed blocks without changing requests or other API errors.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.FEED,
    readOnly = true,
) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(rifFeatureResources)
    execute {
        val free = resolveRifNativeSku(buildList { classDefForEach(::add) }) == RifNativeSku.FREE
        val prefix = if (free) "Lh3/" else "Li3/"
        val feed = if (free) "Lb5/y0;" else "Lc5/y0;"
        val base = mutableClassDefBy("${prefix}c;")
        val response = base.methods.single {
            it.returnType == "Ljava/lang/Object;" && it.parameterTypes.size == 4 &&
                    it.parameterTypes.takeLast(2) == listOf("Z", "Ljava/lang/String;")
        }
        // Capture this worker's OAuth state before native error handling.
        wrapMorpheHook(base, response, before = "invoke-static {p0, p3}, $HOOK->begin(Ljava/lang/Object;Z)V")

        // Recognize parsed listings through the GET worker's weak owner.
        val worker = mutableClassDefBy("${prefix}a\$a;")
        val reference = worker.fields.single { it.type == "Ljava/lang/ref/WeakReference;" }
        worker.interfaces.add("${HOOK.dropLast(1)}\$Request;")
        worker.methods.add(ImmutableMethod(
            worker.type, "morpheIsFeed", emptyList(), "Z", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $reference
                invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
                move-result-object v0
                instance-of v0, v0, $feed
                return v0
            """)
        })
        // Inspect only 403 bodies, replaying their bytes to the native parser.
        val forbidden = worker.methods.single {
            it.name == "V" && it.parameterTypes == listOf("Ljava/io/InputStream;") && it.returnType == "V"
        }
        wrapMorpheHook(worker, forbidden, before = """
            invoke-static {p0, p1}, $HOOK->inspect(Ljava/lang/Object;Ljava/io/InputStream;)Ljava/io/InputStream;
            move-result-object p1
        """)

        // UI callbacks must consume findings for their current worker, not the latest response.
        val loader = mutableClassDefBy("${prefix}a;")
        val current = loader.fields.single { it.name == "v" && it.type == base.type }
        loader.interfaces.add("${HOOK.dropLast(1)}\$Loader;")
        loader.methods.add(ImmutableMethod(
            loader.type, "morpheRequest", emptyList(), "Ljava/lang/Object;", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $current
                return-object v0
            """)
        })
        // Replace only feed error presentation; other error screens stay native.
        val error = Fingerprint(
            definingClass = if (free) "Le5/n;" else "Lf5/n;",
            parameters = listOf("Lo0/c;"), returnType = "V",
            strings = listOf("private", "quarantined", "banned"),
        )
        val context = error.method.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .first { it.returnType == "Landroid/content/Context;" && it.parameterTypes.isEmpty() }
        error.wrapMorpheHook(before = """
            invoke-virtual {p0}, $context
            move-result-object v0
            invoke-static {p1, v0}, $HOOK->show(Ljava/lang/Object;Landroid/content/Context;)Z
            move-result v0
            if-eqz v0, :morphe_native_error
            return-void
            :morphe_native_error
            nop
        """)
    }
}
