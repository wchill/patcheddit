/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.joeyforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.joeyforreddit.JoeyForRedditCompatible
import app.morphe.patches.reddit.customclients.joeyforreddit.detection.piracy.disablePiracyDetectionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    dependsOn(disablePiracyDetectionPatch)

    compatibleWith(*JoeyForRedditCompatible)

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        // region Patch client id.
        getClientIdFingerprint.method.returnEarly(clientId!!)
        // endregion

        // region Patch redirect URI.
        oauthHelperConstructorFingerprint.apply {
            val invokeStaticIndex = instructionMatches.first().index
            val stringRegister = method.getInstruction<FiveRegisterInstruction>(invokeStaticIndex).registerC
            method.addInstructions(invokeStaticIndex, "const-string v$stringRegister, \"$redirectUri\"")
        }
        oauthContainsCodeFingerprint.apply {
            val index = instructionMatches.first().index
            val register = method.getInstruction<OneRegisterInstruction>(index).registerA
            method.replaceInstruction(index, "const/4 v$register, 0x1")
        }
        oauthShouldOverrideUrlLoadingFingerprint.apply {
            val ifIndex = instructionMatches.first().index
            val resultRegister = method.getInstruction<OneRegisterInstruction>(ifIndex).registerA

            val invokeDirectIndex = instructionMatches.last().index
            val invokeInstruction = method.getInstruction<FiveRegisterInstruction>(invokeDirectIndex)
            val invokeReference = invokeInstruction.getReference<MethodReference>()
            method.addInstructionsWithLabels(ifIndex + 1, """
                invoke-direct { v${invokeInstruction.registerC}, v${invokeInstruction.registerD} }, $invokeReference
                return v$resultRegister
            """)
        }
        // endregion

        // region Patch user agent.
        authUtilityUserAgentFingerprint.method.returnEarly(userAgent!!)
        // endregion
    }
}
