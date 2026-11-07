/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.overrideOAuthStrings
import app.morphe.patches.reddit.customclients.overrideOAuthReturn
import app.morphe.patcher.util.returnEarly
import java.net.URI
import org.w3c.dom.Element
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

val spoofClientPatch = spoofClientPatch(ExtensionPatches.Infinity) { clientIdOption, redirectUriOption, userAgentOption ->
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
                    val callback = callbacks.single()
                    val intentFilter = callback.parentNode as Element
                    intentFilter.parentNode.appendChild(intentFilter.cloneNode(true))
                    callback.apply {
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

        val clientCalls = ClientIdFingerprint.matchAll()
        val clientGetters = clientCalls.map { match ->
            checkNotNull(match.instructionMatches.single().instruction.getReference<MethodReference>()) {
                "Infinity's client ID call has no method reference."
            }
        }.distinctBy { it.toString() }.associate { reference ->
            reference.toString() to Fingerprint(
                definingClass = reference.definingClass,
                name = reference.name,
                parameters = reference.parameterTypes.map(CharSequence::toString),
                returnType = reference.returnType,
            ).overrideOAuthReturn("getClientId")
        }
        for (match in clientCalls) {
            val instruction = match.instructionMatches.single()
            val reference = checkNotNull(instruction.instruction.getReference<MethodReference>())
            val getter = clientGetters.getValue(reference.toString())
            if (getter.name != reference.name) {
                match.method.replaceInstruction(instruction.index, "invoke-static {}, $getter")
            }
        }

        for (prefix in listOf("android:", "")) {
            userAgentFingerprint(packageMetadata.versionName, prefix).matchAll().forEach { match ->
                match.overrideOAuthStrings("getUserAgent")
            }
        }

        RedirectUriFingerprint.matchAll().forEach { match ->
            match.overrideOAuthStrings("getRedirectUri")
        }
    }
}
