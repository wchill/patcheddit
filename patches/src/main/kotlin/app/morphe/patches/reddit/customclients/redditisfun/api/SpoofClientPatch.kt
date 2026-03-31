/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.redditisfun.RedditIsFunCompatible
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    compatibleWith(*RedditIsFunCompatible)

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        // region Patch client id.
        /**
         * Replaces a one register instruction with a const-string instruction
         * at the index returned by [getReplacementIndex].
         *
         * @param string The string to replace the instruction with.
         * @param getReplacementIndex A function that returns the index of the instruction to replace
         * using the [Match.StringMatch] list from the [Match].
         */
        fun List<Match>.replaceWith(
            string: String,
            getReplacementIndex: List<Match.StringMatch>.() -> Int,
        ) = forEach { match ->
            match.method.apply {
                val replacementIndex = match.stringMatches.getReplacementIndex()
                val clientIdRegister = getInstruction<OneRegisterInstruction>(replacementIndex).registerA

                replaceInstruction(replacementIndex, "const-string v$clientIdRegister, \"$string\"")
            }
        }

        // Patch OAuth authorization.
        buildAuthorizationStringFingerprint.matchAll().replaceWith(clientId!!) { first().index + 4 }

        // Path basic authorization.
        basicAuthorizationFingerprint.matchAll().replaceWith("$clientId:") { last().index + 7 }
        // endregion

        // region Patch redirect URI.
        redirectUriFingerprint.matchAll().forEach { match ->
            match.method.let {
                match.stringMatches.forEach { stringMatch ->
                    val register = it.getInstruction<OneRegisterInstruction>(stringMatch.index).registerA
                    it.replaceInstruction(stringMatch.index, "const-string v$register, \"$redirectUri\"")
                }
            }
        }
        // endregion

        // region Patch user agent.
        getUserAgentFingerprint.method.returnEarly(userAgent!!)
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
