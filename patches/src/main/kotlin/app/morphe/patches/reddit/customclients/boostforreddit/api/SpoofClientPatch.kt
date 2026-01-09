package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/http/HttpUtils;"

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    compatibleWith("com.rubenmayayo.reddit"("1.12.12"))

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        if (clientIdOption.value == null && redirectUriOption.value == null && userAgentOption.value == null) {
            throw PatchException("When spoofing client, at least one of clientId, redirectUri or userAgent should be set.")
        }

        // region Patch client id.

        if (clientId != null) {
            getClientIdFingerprint.method.returnEarly(clientId!!)
        }

        // endregion

        // region Patch redirect URI.
        if (redirectUri != null) {
            listOf(loginActivityOnCreateFingerprint, loginActivityAShouldOverrideUrlLoadingFingerprint).forEach { fingerprint ->
                fingerprint.method.let {
                    fingerprint.stringMatches.forEach { match ->
                        val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                        it.replaceInstruction(match.index, "const-string v$register, \"$redirectUriOption\"")
                    }
                }
            }

            loginActivityAShouldOverrideUrlLoadingFingerprint.method.apply {
                val index = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.IF_EQZ
                }
                addInstructions(
                    index + 1,
                    """
                const-string v1, "$redirectUriOption"
                const-string v2, "http://localhost"
                invoke-virtual {v6, v1, v2}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v6
                """
                )
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
        }
        // endregion

        // Patch user agent.
        if (userAgent != null) {
            buildUserAgentFingerprint.method.let {
                buildUserAgentFingerprint.stringMatches.forEach { match ->
                    val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                    it.replaceInstruction(match.index, "const-string v$register, \"$userAgentOption\"")
                }
            }
        }
        // endregion
    }
}
