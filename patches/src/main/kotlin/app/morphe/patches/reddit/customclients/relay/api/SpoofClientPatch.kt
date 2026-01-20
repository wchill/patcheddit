package app.morphe.patches.reddit.customclients.relay.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.spoofClientPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    compatibleWith(
        "free.reddit.news"("10.2.40"),
        "reddit.news"("10.2.40"),
    )

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        if (clientId == null) {
            throw PatchException("When spoofing client, clientId must be set.")
        }

        // region Patch redirect URI.
        if (redirectUri != null) {
            setOf(
                loginActivityRedirectUriFingerprint,
                shouldOverrideUrlLoadingRedirectUriFingerprint,
                redditAccountManagerRedirectUriFingerprint
            ).forEach { fingerprint ->
                val redirectUriIndex = fingerprint.stringMatches.last().index
                fingerprint.method.apply {
                    val redirectUriRegister = getInstruction<OneRegisterInstruction>(redirectUriIndex).registerA

                    fingerprint.method.replaceInstruction(
                        redirectUriIndex,
                        "const-string v$redirectUriRegister, \"$redirectUri\"",
                    )
                }
            }
        }
        // endregion

        // region Patch client id.
        setOf(
            loginActivityClientIdFingerprint,
            getLoggedInBearerTokenFingerprint,
            getLoggedOutBearerTokenFingerprint,
            getRefreshTokenFingerprint,
        ).forEach { fingerprint ->
            val clientIdIndex = fingerprint.stringMatches.first().index
            fingerprint.method.apply {
                val clientIdRegister = getInstruction<OneRegisterInstruction>(clientIdIndex).registerA

                fingerprint.method.replaceInstruction(
                    clientIdIndex,
                    "const-string v$clientIdRegister, \"$clientId\"",
                )
            }
        }
        // endregion

        // region Patch user agent.
        if (userAgent != null) {
            networkModuleUserAgentFingerprint.apply {
                val invokeDirectIndex = instructionMatches.first().index
                val invokeDirectRegister = method.getInstruction<OneRegisterInstruction>(invokeDirectIndex).registerA

                val userAgentField = classDef.fields.first { field ->
                    field.type == "Ljava/lang/String;"
                }

                method.addInstructions(invokeDirectIndex, """
                    const-string v$invokeDirectRegister, "$userAgent"
                    sput-object v$invokeDirectRegister, ${userAgentField.definingClass}->${userAgentField.name}:Ljava/lang/String;
                """)
            }
        }
        // endregion

        // region Patch miscellaneous.

        // Do not load remote config which disables OAuth login remotely.
        setRemoteConfigFingerprint.method.addInstructions(0, "return-void")

        // Prevent OAuth login being disabled remotely.
        val checkIsOAuthRequestIndex = redditCheckDisableAPIFingerprint.instructionMatches.first().index

        redditCheckDisableAPIFingerprint.method.apply {
            val returnNextChain = getInstruction<BuilderInstruction21t>(checkIsOAuthRequestIndex).target
            replaceInstruction(checkIsOAuthRequestIndex, BuilderInstruction10t(Opcode.GOTO, returnNextChain))
        }

        // endregion
    }
}
