/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.replaceStringMatchesWithFunc
import app.morphe.patcher.util.returnEarly
import java.net.URI
import org.w3c.dom.Element

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extension/infinity/APIUtils;"
internal const val GET_CLIENT_ID_METHOD = "$EXTENSION_CLASS_NAME->getClientId"
internal const val GET_USER_AGENT_METHOD = "$EXTENSION_CLASS_NAME->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"

private val disableBillingPatch = bytecodePatch(default = false) {
    execute {
        // The Patreon build has no Google Play billing client.
        if (packageMetadata.packageName != "ml.docilealligator.infinityforreddit.patreon") {
            BillingClientOnServiceConnectedFingerprint.method.returnEarly()
        }
        InfinityStartSubscriptionActivityFingerprint.method.returnEarly()
    }
}

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    dependsOn(
        disableBillingPatch,
        ExtensionPatches.Infinity,
        resourcePatch(default = false) {
            execute {
                val uri = URI(redirectUri!!)
                // Android accepts underscores in hosts such as RedReader's rr_oauth_redir.
                val host = uri.host ?: uri.rawAuthority
                require(!uri.scheme.isNullOrEmpty() && host?.contains('@') != true) {
                    "Infinity's redirect URI must include a scheme and must not contain user information"
                }
                document("AndroidManifest.xml").use { document ->
                    val data = document.getElementsByTagName("data")
                    val callbacks = (0 until data.length).map { data.item(it) as Element }.filter {
                        it.getAttribute("android:scheme") == "infinity" &&
                            it.getAttribute("android:host") == "localhost"
                    }
                    require(callbacks.size == 1) {
                        "Expected one Infinity OAuth callback intent filter, found ${callbacks.size}"
                    }
                    callbacks.single().apply {
                        setAttribute("android:scheme", uri.scheme)
                        if (host == null) removeAttribute("android:host")
                        else setAttribute("android:host", host)
                        if (uri.port != -1) setAttribute("android:port", uri.port.toString())
                    }
                }
            }
        }
    )
    compatibleWith(*AppCompatibility.Infinity)

    execute {
        GetDefaultClientIdFingerprint.method.returnEarly(clientId!!)
        GetDefaultRedirectUriFingerprint.method.returnEarly(redirectUri!!)
        GetDefaultUserAgentFingerprint.method.returnEarly(userAgent!!)

        ClientIdFingerprint.matchAll().forEach { match ->
            match.method.replaceInstruction(
                match.instructionMatches.single().index,
                "invoke-static {}, $GET_CLIENT_ID_METHOD()Ljava/lang/String;"
            )
        }

        for (prefix in listOf("android:", "")) {
            userAgentFingerprint(packageMetadata.versionName, prefix).matchAll().forEach { match ->
                match.replaceStringMatchesWithFunc(GET_USER_AGENT_METHOD)
            }
        }

        RedirectUriFingerprint.matchAll().forEach { match ->
            match.replaceStringMatchesWithFunc(GET_REDIRECT_URI_METHOD)
        }
    }
}
