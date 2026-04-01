/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.all.misc.string.replaceStringPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.util.Base64

val spoofClientPatch = spoofClientPatch { clientId, redirectUri, userAgent ->
    dependsOn(
        disablePiracyDetectionPatch,
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com")
    )

    compatibleWith(*SyncForRedditCompatible)

    execute {
        // region Patch client id.
        getBearerTokenFingerprint.method.apply {
            val auth = Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))
            returnEarly("Basic $auth")

            val occurrenceIndex =
                getAuthorizationStringFingerprint.stringMatches.first().index

            getAuthorizationStringFingerprint.method.apply {
                val authorizationStringInstruction = getInstruction<ReferenceInstruction>(occurrenceIndex)
                val targetRegister = (authorizationStringInstruction as OneRegisterInstruction).registerA
                val reference = authorizationStringInstruction.reference as StringReference

                val newAuthorizationUrl = reference.string.replace(
                    "client_id=.*?&".toRegex(),
                    "client_id=$clientId&",
                )

                replaceInstruction(
                    occurrenceIndex,
                    "const-string v$targetRegister, \"$newAuthorizationUrl\"",
                )
            }
        }
        // endregion

        // region Patch redirect URI.
        getRedirectUriFingerprint.method.returnEarly(redirectUri.value!!)
        // endregion

        // region Patch user agent.
        getUserAgentFingerprint.method.returnEarly(userAgent.value!!)
        // endregion

        // region Patch Imgur API URL.

        imgurImageAPIFingerprint.let {
            val apiUrlIndex = it.stringMatches.first().index
            it.method.replaceInstruction(
                apiUrlIndex,
                "const-string v1, \"https://api.imgur.com/3/image\"",
            )
        }

        // endregion
    }
}
