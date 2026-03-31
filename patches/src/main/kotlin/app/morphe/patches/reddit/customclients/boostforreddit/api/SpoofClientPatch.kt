/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/http/HttpUtils;"

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    compatibleWith(*BoostCompatible)

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        // region Patch client id.
        getClientIdFingerprint.method.returnEarly(clientId!!)

        // endregion

        // region Patch redirect URI.
        shouldOverrideUrlLoadingFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.IF_EQZ
            }
            addInstructions(
                index + 1,
                """
                    const-string v1, "$redirectUri"
                    const-string v2, "http://localhost"
                    invoke-virtual {v6, v1, v2}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                    move-result-object v6
                """
            )
        }

        redirectUriFingerprint.matchAll().forEach { match ->
            match.method.let {
                match.stringMatches.forEach { match ->
                    val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                    it.replaceInstruction(match.index, "const-string v$register, \"$redirectUri\"")
                }
            }
        }

        jrawNewUrlFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE && getReference<TypeReference>()?.type == "Ljava/net/URL;"
            }
            addInstructions(
                index,
                """
                    invoke-static       { p0 }, $JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR->createUrl(Ljava/lang/String;)Ljava/net/URL;
                    move-result-object  v0
                    return-object v0
                """
            )
        }
        // endregion

        // Patch user agent.
        buildUserAgentFingerprint.method.let {
            buildUserAgentFingerprint.stringMatches.forEach { match ->
                val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                it.replaceInstruction(match.index, "const-string v$register, \"$userAgent\"")
            }
        }
        // endregion
    }
}
