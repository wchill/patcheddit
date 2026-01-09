package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
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

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        if (clientIdOption.value == null && redirectUriOption.value == null && userAgentOption.value == null) {
            throw PatchException("When spoofing client, at least one of clientId, redirectUri or userAgent should be set.")
        }

        if (clientId != null) {
            fun Fingerprint.patch(replacementString: String) {
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
            getAuthorizationUrlFingerprint.patch("client_id=$clientId")

            // Patch client id for access token request.
            requestTokenFingerprint.patch(clientId!!)
        }

        if (redirectUri != null) {
            getAuthorizeUrlFingerprint.method.apply {
                val stringIndex = getAuthorizeUrlFingerprint.stringMatches.first().index
                val stringRegister = getInstruction<OneRegisterInstruction>(stringIndex).registerA
                replaceInstruction(
                    stringIndex,
                    "const-string v$stringRegister, \"redirect_uri=$redirectUri\""
                )
            }
            setOf(runTaskFingerprint, isRedirectUrlFingerprint).forEach { fingerprint ->
                fingerprint.method.apply {
                    val stringIndex = fingerprint.stringMatches.first().index
                    val stringRegister = getInstruction<OneRegisterInstruction>(stringIndex).registerA
                    replaceInstruction(
                        stringIndex,
                        "const-string v$stringRegister, \"$redirectUri\""
                    )
                }
            }
        }

        if (userAgent != null) {
            getRestClientUserAgentFingerprint.method.returnEarly(userAgent!!)
            getRedditUserAgentFingerprint.method.returnEarly(userAgent!!)
        }
    }
}
