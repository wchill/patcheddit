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
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.patches.reddit.customclients.overrideOAuthReturn
import app.morphe.patches.reddit.customclients.overrideOAuthStrings
import app.morphe.patches.reddit.customclients.overrideOAuthValue
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val spoofClientPatch = spoofClientPatch(ExtensionPatches.RIF) { _, _, _ ->
    dependsOn(ExtensionPatches.RIF)
    compatibleWith(*AppCompatibility.RedditIsFun)

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
            getter: String,
            getReplacementIndex: List<Match.StringMatch>.() -> Int,
        ) = forEach { match ->
            match.method.apply {
                val replacementIndex = match.stringMatches.getReplacementIndex()
                overrideOAuthValue(replacementIndex, getter)
            }
        }

        // Patch OAuth authorization.
         BuildAuthorizationStringFingerprint.matchAll().replaceWith("getClientId") { first().index + 4 }

         // Path basic authorization.
         BasicAuthorizationFingerprint.matchAll().replaceWith("getBasicAuthClientId") { last().index + 7 }
        // endregion

         // region Patch redirect URI.
         RedirectUriFingerprint.matchAll().forEach { match ->
             match.overrideOAuthStrings("getRedirectUri")
         }
         // endregion

         // region Patch user agent.
         GetUserAgentFingerprint.overrideOAuthReturn("getUserAgent")
         // endregion

         // region Patch miscellaneous.

         // Reddit messed up and does not append a redirect uri to the authorization url to old.reddit.com/login.
         // Replace old.reddit.com with www.reddit.com to fix this.
         BuildAuthorizationStringFingerprint.method.apply {
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
