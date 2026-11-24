/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.INSTALL_NEW_CLIENT_METHOD
import app.morphe.patches.reddit.customclients.REDGIFS_PLAYBACK
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.patches.reddit.customclients.redgifsSetting
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/baconreader/FixRedgifsApiPatch;"
private const val MEDIA = "Lcom/onelouder/baconreader/PostMediaHandler;"
private const val LINK = "Lcom/onelouder/baconreader/reddit/Link;"
private const val IMAGES = "Lcom/onelouder/baconreader/imageutils/ImageHelper;"
private const val LISTENER = "Lcom/onelouder/baconreader/imageutils/ImageHelper\$ImageHelperListener;"
private const val CONTEXT = "Landroid/content/Context;"
private const val STRING = "Ljava/lang/String;"
private const val RESOLVE = "$IMAGES->resolve($CONTEXT$STRING$STRING$LISTENER)Z"

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(
    extensionPatch = ExtensionPatches.BaconReader
) {
    compatibleWith(*AppCompatibility.BaconReader)

    execute {
        // region Patch Redgifs OkHttp3 client.

        redgifsSetting.patchMethod(GetOkHttpClientFingerprint) {
            // Remove conflicting OkHttp interceptors.
            val originalInterceptorInstallIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE && getReference<TypeReference>()?.type == "Lcom/onelouder/baconreader/media/gfycat/RedGifsManager\$HeaderInterceptor;"
            }
            removeInstructions(originalInterceptorInstallIndex, 5)

            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "build" && reference.definingClass == "Lokhttp3/OkHttpClient\$Builder;"
            }
            val register = getInstruction<FiveRegisterInstruction>(index).registerC
            replaceInstruction(
                index,
                """
                invoke-static       { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$INSTALL_NEW_CLIENT_METHOD
                """
            )
        }

        Fingerprint(
            definingClass = IMAGES,
            name = "resolve",
            parameters = listOf(CONTEXT, STRING, STRING, LISTENER),
            returnType = "Z",
        ).wrapMorpheHook(
            setting = redgifsSetting,
            before = """
                invoke-static {p1}, $REDGIFS_PLAYBACK->normalizeSourceUrl($STRING)$STRING
                move-result-object p1
            """,
        )

        val start = Fingerprint(definingClass = MEDIA, name = "start", parameters = emptyList(),
            returnType = "V", strings = listOf("resolving link "))
        val bridge = "morpheResolveRedgifsPreview"
        start.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC && getReference<MethodReference>()?.toString() == RESOLVE
            }
            val call = getInstruction<FiveRegisterInstruction>(index)
            val receiver = implementation!!.registerCount - 1
            require(receiver <= 15 && call.registerCount == 4) { "Unsupported BaconReader media resolver call" }
            // Same-width invoke: start() has a switch payload whose offsets must remain unchanged.
            replaceInstruction(index, "invoke-static {v$receiver, v${call.registerC}, v${call.registerD}, " +
                "v${call.registerE}, v${call.registerF}}, $MEDIA->$bridge($MEDIA$CONTEXT$STRING$STRING$LISTENER)Z")
        }
        start.classDef.methods.add(ImmutableMethod(
            MEDIA, bridge, listOf(MEDIA, CONTEXT, STRING, STRING, LISTENER)
                .map { ImmutableMethodParameter(it, emptySet(), null) },
            "Z", AccessFlags.PRIVATE.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
            MutableMethodImplementation(7),
        ).toMutable().apply {
            addInstructions(0, """
                ${redgifsSetting.checkEnabled("v0", "native")}
                iget-object v0, p0, $MEDIA->link:$LINK
                iget-object v1, v0, $LINK->url:$STRING
                invoke-static {p2, v1}, $REDGIFS_PLAYBACK->preferOriginal($STRING$STRING)$STRING
                move-result-object p2
                :native
                invoke-static/range {p1 .. p4}, $RESOLVE
                move-result v0
                return v0
            """)
        })
    }
}
