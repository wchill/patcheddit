/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.baconreader.BaconReaderCompatible
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.shared.misc.string.replaceStringPatch
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    dependsOn(
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com")
    )

    compatibleWith(*BaconReaderCompatible)

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        fun Match.patch(replacementString: String) {
            val clientIdIndex = stringMatches.first().index

            method.apply {
                val clientIdRegister = getInstruction<OneRegisterInstruction>(clientIdIndex).registerA
                replaceInstruction(
                    clientIdIndex,
                    "const-string v$clientIdRegister, \"$replacementString\"",
                )
            }
        }

        // Patch client id in authorization url.
        getAuthorizationUrlFingerprint.match().patch("client_id=$clientId")

        // Patch client id for access token request.
        requestTokenFingerprint.match().patch(clientId!!)

        getAuthorizeUrlFingerprint.match().patch(redirectUri!!)
        authUrlFingerprint.matchAll().forEach { match -> match.patch(redirectUri!!) }

        getRestClientUserAgentFingerprint.method.returnEarly(userAgent!!)
        getRedditUserAgentFingerprint.method.returnEarly(userAgent!!)
    }
}
