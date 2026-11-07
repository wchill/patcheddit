/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.relay.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.OAUTH_SETTINGS_DESCRIPTOR
import app.morphe.patches.reddit.customclients.overrideOAuthStrings
import app.morphe.patches.reddit.customclients.spoofClientSetting
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val spoofClientPatch = spoofClientPatch(ExtensionPatches.Relay) { _, _, _ ->
    dependsOn(ExtensionPatches.Relay)
    compatibleWith(*AppCompatibility.Relay)

    execute {
        // region Patch redirect URI.
        RedirectUriFingerprint.matchAll().forEach { match ->
            match.overrideOAuthStrings("getRedirectUri")
        }
        // endregion

        // region Patch client id.
        ClientIdFingerprint.matchAll().forEach { match ->
            match.overrideOAuthStrings("getClientId")
        }
        // endregion

        // region Patch user agent.
        NetworkModuleUserAgentFingerprint.apply {
            val invokeDirectIndex = instructionMatches.first().index
            val invokeDirectRegister = method.getInstruction<OneRegisterInstruction>(invokeDirectIndex).registerA

            val userAgentField = classDef.fields.first { field ->
                field.type == "Ljava/lang/String;"
            }

            method.addInstructions(invokeDirectIndex, """
                sget-object v$invokeDirectRegister, ${userAgentField.definingClass}->${userAgentField.name}:Ljava/lang/String;
                invoke-static/range {v$invokeDirectRegister .. v$invokeDirectRegister}, $OAUTH_SETTINGS_DESCRIPTOR->getUserAgent(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$invokeDirectRegister
                sput-object v$invokeDirectRegister, ${userAgentField.definingClass}->${userAgentField.name}:Ljava/lang/String;
            """)
        }
        // endregion

        // region Patch miscellaneous.

        // Do not load remote config which disables OAuth login remotely.
        spoofClientSetting.patchMethod(SetRemoteConfigFingerprint) { returnEarly() }

        // Prevent OAuth login being disabled remotely.
        val checkIsOAuthRequestIndex = RedditCheckDisableAPIFingerprint.instructionMatches.first().index

        spoofClientSetting.patchMethod(RedditCheckDisableAPIFingerprint) {
            val returnNextChain = getInstruction<BuilderInstruction21t>(checkIsOAuthRequestIndex).target
            replaceInstruction(checkIsOAuthRequestIndex, BuilderInstruction10t(Opcode.GOTO, returnNextChain))
        }

        // endregion
    }
}
