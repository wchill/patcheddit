package app.morphe.patches.scoresapp.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.scoresapp.ScoresAppCompatible
import app.morphe.patches.scoresapp.license.disableLicenseCheckPatch
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes all ads by making the app think the \"Remove ads\" purchase is active.",
    default = true
) {
    // A patched app is re-signed, which fails the Play license check on launch.
    dependsOn(disableLicenseCheckPatch)

    compatibleWith(*ScoresAppCompatible)

    execute {
        // 1. Find the "are ads removed?" getter: the first static (X)Z call in the ad loader.
        val isAdsRemovedRef = loadAdFingerprint.method.instructions.firstNotNullOfOrNull {
            if (it.opcode != Opcode.INVOKE_STATIC) return@firstNotNullOfOrNull null
            it.getReference<MethodReference>()?.takeIf { ref ->
                ref.returnType == "Z" && ref.parameterTypes.size == 1
            }
        } ?: throw PatchException("Could not find the ads-removed getter")

        val isAdsRemovedMethod = Fingerprint(
            definingClass = isAdsRemovedRef.definingClass,
            name = isAdsRemovedRef.name,
            returnType = "Z",
            parameters = isAdsRemovedRef.parameterTypes.map { it.toString() },
        ).method

        // 2. The getter reads settings.adsRemoved (a StateFlow<Boolean>) and returns its value.
        val adsRemovedField = isAdsRemovedMethod.instructions.firstNotNullOfOrNull {
            if (it.opcode != Opcode.IGET_OBJECT) null else it.getReference<FieldReference>()
        } ?: throw PatchException("Could not find the ads-removed field")

        // 3. Borrow MutableStateFlow(value) and asStateFlow() from the billing manager constructor.
        val billingInit = Fingerprint(
            definingClass = billingManagerFingerprint.classDef.type,
            name = "<init>",
        ).method
        val staticCalls = billingInit.instructions
            .filter { it.opcode == Opcode.INVOKE_STATIC }
            .mapNotNull { it.getReference<MethodReference>() }
        val mutableStateFlowRef = staticCalls.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == "Ljava/lang/Object;"
        } ?: throw PatchException("Could not find MutableStateFlow()")
        val asStateFlowRef = staticCalls.firstOrNull {
            it.parameterTypes.size == 1 && it.returnType == adsRemovedField.type
        } ?: throw PatchException("Could not find asStateFlow()")

        // 4. At the end of the settings constructor, replace the flow with a constant `true`.
        // Every reader (ad loaders, banner, menu item, SDK init) then sees "ads removed".
        Fingerprint(
            definingClass = adsRemovedField.definingClass,
            name = "<init>",
        ).method.apply {
            val returnIndices = instructions
                .filter { it.opcode == Opcode.RETURN_VOID }
                .map { it.location.index }
                .reversed()

            returnIndices.forEach { index ->
                addInstructions(
                    index,
                    """
                        sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                        invoke-static { v0 }, $mutableStateFlowRef
                        move-result-object v0
                        invoke-static { v0 }, $asStateFlowRef
                        move-result-object v0
                        iput-object v0, p0, $adsRemovedField
                    """
                )
            }
        }

        // 5. Belt and braces for the direct callers of the getter.
        isAdsRemovedMethod.returnEarly(true)
    }
}
