/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joey.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.joey.detection.piracy.disablePiracyDetectionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.OAUTH_SETTINGS_DESCRIPTOR
import app.morphe.patches.reddit.customclients.overrideOAuthReturn
import app.morphe.patches.reddit.customclients.spoofClientSetting
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val spoofClientPatch = spoofClientPatch(ExtensionPatches.Joey) { _, _, _ ->
    dependsOn(ExtensionPatches.Joey, disablePiracyDetectionPatch)

    compatibleWith(*AppCompatibility.JoeyForReddit)

    execute {
        // region Patch client id.
        GetClientIdFingerprint.overrideOAuthReturn("getClientId")
        // endregion

        // region Patch redirect URI.
        OauthHelperConstructorFingerprint.apply {
            val invokeStaticIndex = instructionMatches.first().index
            val stringRegister = method.getInstruction<FiveRegisterInstruction>(invokeStaticIndex).registerC
            method.addInstructions(invokeStaticIndex, """
                invoke-static/range {v$stringRegister .. v$stringRegister}, $OAUTH_SETTINGS_DESCRIPTOR->getRedirectUri(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$stringRegister
            """)
        }
        spoofClientSetting.patchMethod(OauthContainsCodeFingerprint) {
            val index = OauthContainsCodeFingerprint.instructionMatches.first().index
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const/4 v$register, 0x1")
        }
        spoofClientSetting.patchMethod(OauthShouldOverrideUrlLoadingFingerprint) {
            val ifIndex = OauthShouldOverrideUrlLoadingFingerprint.instructionMatches.first().index
            val resultRegister = getInstruction<OneRegisterInstruction>(ifIndex).registerA

            val invokeDirectIndex = OauthShouldOverrideUrlLoadingFingerprint.instructionMatches.last().index
            val invokeInstruction = getInstruction<FiveRegisterInstruction>(invokeDirectIndex)
            val invokeReference = invokeInstruction.getReference<MethodReference>()
            addInstructionsWithLabels(ifIndex + 1, """
                invoke-direct { v${invokeInstruction.registerC}, v${invokeInstruction.registerD} }, $invokeReference
                return v$resultRegister
            """)
        }
        // endregion

        // region Patch user agent.
        AuthUtilityUserAgentFingerprint.overrideOAuthReturn("getUserAgent")
        // endregion
    }
}
