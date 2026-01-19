package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.all.misc.transformation.transformInstructionsPatch
import app.morphe.patches.reddit.customclients.slide.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extension/slide/APIUtils;"
internal const val GET_USER_AGENT_METHOD = "${EXTENSION_CLASS_NAME}->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"
internal const val MODIFY_DIALOG_METHOD = "$EXTENSION_CLASS_NAME->modifyDialog(Landroid/widget/LinearLayout;)V"
internal const val JRAW_UTILS_EXTENSION_CLASS_NAME = "Lapp/morphe/extension/reddit/http/JrawUtils;"

val spoofClientPatch = spoofClientPatch(
    name = "Spoof client",
    description = "Allows modifying Slide's client ID, redirect URI and user agent in settings. " +
            "Patch options will modify default values.",
) { clientIdOption, redirectUriOption, userAgentOption ->
    dependsOn(
        sharedExtensionPatch,
        transformInstructionsPatch(
            filterMap = { classDef, _, instruction, instructionIndex ->
                if (classDef.type.startsWith("Lapp/morphe/")) {
                    return@transformInstructionsPatch null
                }

                if (instruction.opcode != Opcode.CONST_STRING) {
                    return@transformInstructionsPatch null
                }

                val stringReference = instruction.getReference<StringReference>()

                if (stringReference?.string?.startsWith("android:me.edgan.RedditSlide:v") == true) {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

                if (stringReference?.string?.matches(Regex("""me\.edgan\.redditslide/\d+\.\d+\.\d+""")) == true) {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

                if (stringReference?.string == "Slide flair search") {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

                if (stringReference?.string == "http://www.ccrama.me") {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

                return@transformInstructionsPatch null
            },
            transform = { method, entry ->
                val (stringConst, instruction, index) = entry
                val register = (instruction as OneRegisterInstruction).registerA
                method.replaceInstruction(index, "nop")

                if (stringConst == "http://www.ccrama.me") {
                    method.addInstructions(index,
                        """
                        invoke-static {}, $GET_REDIRECT_URI_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                    )
                } else {
                    method.addInstructions(index,
                        """
                        invoke-static {}, $GET_USER_AGENT_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                    )
                }
            }
        )
    )
    compatibleWith("me.edgan.redditslide")

    val clientId = clientIdOption.value?.trim()
    val redirectUri = redirectUriOption.value?.trim()
    val userAgent = userAgentOption.value?.trim()

    execute {
        tutorialFingerprint.method.apply {
            val index = tutorialFingerprint.instructionMatches.last().index
            val register = (getInstruction(index) as FiveRegisterInstruction).registerC
            addInstruction(
                index + 1,
                """
                    invoke-static { v$register }, $MODIFY_DIALOG_METHOD
                """
            )
        }

        tutorialSaveFingerprint.method.apply {
            val index = tutorialSaveFingerprint.instructionMatches.first().index
            addInstruction(
                index,
                """
                    invoke-static {}, $EXTENSION_CLASS_NAME->persistSettings()V
                """
            )
        }

        showClientIdDialogSetupLayoutFingerprint.method.apply {
            val index = showClientIdDialogSetupLayoutFingerprint.instructionMatches.first().index
            val inst = showClientIdDialogSetupLayoutFingerprint.instructionMatches.first().instruction
            val register = (inst as FiveRegisterInstruction).registerC
            addInstruction(
                index + 1,
                """
                    invoke-static { v$register }, $MODIFY_DIALOG_METHOD
                """
            )
        }

        showClientIdDialogSaveFingerprint.method.apply {
            addInstruction(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_NAME->persistSettings()V
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
                invoke-static       { p0 }, $JRAW_UTILS_EXTENSION_CLASS_NAME->createUrl(Ljava/lang/String;)Ljava/net/URL;
                move-result-object  v0
                return-object v0
                """
            )
        }

        jrawOnUserChallengeFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static { p1 }, $JRAW_UTILS_EXTENSION_CLASS_NAME->fixOauthFinalUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object p1
                """
            )
        }

        if (!clientId.isNullOrEmpty()) {
            listOf(
                showClientIdDialogLoadDefaultClientIdFingerprint,
                showClientIdDialogDefaultStringFingerprint,
                tutorialLoadDefaultFingerprint,
                settingsFragmentShowClientIdFingerprint
            ).forEach { fingerprint ->
                fingerprint.method.apply {
                    val index = fingerprint.instructionMatches.last().index
                    replaceInstruction(
                        index,
                        """
                            invoke-static {}, $EXTENSION_CLASS_NAME->getClientId()Ljava/lang/String;
                        """
                    )
                }
            }

            getDefaultClientIdFingerprint.method.returnEarly(clientId)
        }

        if (!redirectUri.isNullOrEmpty()) {
            getDefaultRedirectUriFingerprint.method.returnEarly(redirectUri)
        }

        if (!userAgent.isNullOrEmpty()) {
            getDefaultUserAgentFingerprint.method.returnEarly(userAgent)
        }
    }
}
