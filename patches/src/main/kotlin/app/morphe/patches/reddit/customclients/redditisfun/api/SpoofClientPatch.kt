package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    compatibleWith(
        "com.andrewshu.android.reddit"("5.6.22")
    )

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        if (clientIdOption.value == null && redirectUriOption.value == null && userAgentOption.value == null) {
            throw PatchException("When spoofing client, at least one of clientId, redirectUri or userAgent should be set.")
        }

        // region Patch client id.
        if (clientId != null) {
            /**
             * Replaces a one register instruction with a const-string instruction
             * at the index returned by [getReplacementIndex].
             *
             * @param string The string to replace the instruction with.
             * @param getReplacementIndex A function that returns the index of the instruction to replace
             * using the [Match.StringMatch] list from the [Match].
             */
            fun Fingerprint.replaceWith(
                string: String,
                getReplacementIndex: List<Match.StringMatch>.() -> Int,
            ) = method.apply {
                val replacementIndex = stringMatches.getReplacementIndex()
                val clientIdRegister = getInstruction<OneRegisterInstruction>(replacementIndex).registerA

                replaceInstruction(replacementIndex, "const-string v$clientIdRegister, \"$string\"")
            }

            // Patch OAuth authorization.
            buildAuthorizationStringFingerprint.replaceWith(clientId!!) { first().index + 4 }

            // Path basic authorization.
            basicAuthorizationFingerprint.replaceWith("$clientId:") { last().index + 7 }
        }
        // endregion

        // region Patch redirect URI.
        if (redirectUri != null) {
            listOf(oAuth2ActivityD0Fingerprint, oAuth2ActivityShouldOverrideUrlLoadingFingerprint, cActivityJFingerprint).forEach { fingerprint ->
                fingerprint.method.let {
                    fingerprint.stringMatches.forEach { match ->
                        val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                        it.replaceInstruction(match.index, "const-string v$register, \"$redirectUriOption\"")
                    }
                }
            }
        }
        // endregion

        // region Patch user agent.
        if (userAgent != null) {
            getUserAgentFingerprint.method.returnEarly(userAgent!!)
        }
        // endregion

        // region Patch miscellaneous.

        // Reddit messed up and does not append a redirect uri to the authorization url to old.reddit.com/login.
        // Replace old.reddit.com with www.reddit.com to fix this.
        buildAuthorizationStringFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                getReference<StringReference>()?.contains("old.reddit.com") == true
            }

            val targetRegister = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(
                index,
                "const-string v$targetRegister, \"https://www.reddit.com/api/v1/authorize.compact\"",
            )
        }
        // endregion
    }
}
