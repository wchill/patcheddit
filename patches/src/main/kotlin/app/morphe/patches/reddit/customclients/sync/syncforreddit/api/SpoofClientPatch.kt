package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch
import app.morphe.patches.shared.misc.string.replaceStringPatch
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.util.Base64

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    dependsOn(
        disablePiracyDetectionPatch,
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com")
    )

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
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
            getBearerTokenFingerprint.match(getAuthorizationStringFingerprint.originalClassDef).method.apply {
                val auth = Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))
                returnEarly("Basic $auth")

                val occurrenceIndex =
                    getAuthorizationStringFingerprint.stringMatches!!.first().index

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
        }
        // endregion

        // region Patch redirect URI.
        if (redirectUri != null) {
            getRedirectUriFingerprint.method.returnEarly(redirectUri!!)
        }
        // endregion

        // region Patch user agent.
        if (userAgent != null) {
            getUserAgentFingerprint.method.returnEarly(userAgent!!)
        }
        // endregion

        // region Patch Imgur API URL.

        imgurImageAPIFingerprint.let {
            val apiUrlIndex = it.stringMatches!!.first().index
            it.method.replaceInstruction(
                apiUrlIndex,
                "const-string v1, \"https://api.imgur.com/3/image\"",
            )
        }

        // endregion
    }
}
