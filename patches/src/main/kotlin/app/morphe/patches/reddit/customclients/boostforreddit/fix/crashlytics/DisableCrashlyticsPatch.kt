/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.crashlytics

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val CRASHLYTICS_REGISTRAR_META_DATA =
    "com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar"

private val CRASHLYTICS_GET_INSTANCE_REFERENCE =
    BoostApi.FirebaseApp.getInstance.toString()

private val CRASHLYTICS_SET_COLLECTION_REFERENCE =
    BoostApi.FirebaseCrashlytics.setCollectionEnabled.toString()

private val removeBoostCrashlyticsRegistrarPatch = resourcePatch(
    name = "Remove Boost Crashlytics registrar",
    description = "Removes Firebase Crashlytics auto-registration while keeping other Firebase components.",
    default = true
) {
    compatibleWith(*AppCompatibility.Boost)

    execute {
        document("AndroidManifest.xml").use { document ->
            val metaDataNodes = document.getElementsByTagName("meta-data")
            val matches = (0 until metaDataNodes.length)
                .map { metaDataNodes.item(it) as Element }
                .filter { it.getAttribute("android:name") == CRASHLYTICS_REGISTRAR_META_DATA }

            require(matches.size == 1) {
                "Expected exactly one CrashlyticsRegistrar meta-data entry, found ${matches.size}"
            }

            matches.single().parentNode.removeChild(matches.single())
        }
    }
}

@Suppress("unused")
val disableBoostCrashlyticsPatch = morpheBytecodePatch(
    name = "Disable Boost Crashlytics startup network calls",
    description = "Stops Boost's crash reporting (Crashlytics) from connecting at startup. Other Firebase services keep working.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.ADS,
    readOnly = true,
) {
    dependsOn(removeBoostCrashlyticsRegistrarPatch)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        boostApplicationOnCreateFingerprint.method.apply {
            val instructions = implementation!!.instructions

            val crashlyticsTypeIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.CONST_CLASS &&
                    getReference<TypeReference>()?.type == BoostApi.FirebaseCrashlytics.type
            }
            val getInstanceIndex = instructions.withIndex().last { (index, instruction) ->
                index < crashlyticsTypeIndex &&
                    instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()?.toString() == CRASHLYTICS_GET_INSTANCE_REFERENCE
            }.index

            val setCollectionIndex = instructions.withIndex().first { (index, instruction) ->
                index > getInstanceIndex &&
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    instruction.getReference<MethodReference>()?.toString() == CRASHLYTICS_SET_COLLECTION_REFERENCE
            }.index

            removeInstructions(
                getInstanceIndex,
                setCollectionIndex - getInstanceIndex + 1
            )
        }
    }
}
