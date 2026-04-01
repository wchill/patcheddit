/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.continuum.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.continuum.ContinuumCompatible
import app.morphe.patches.reddit.customclients.continuum.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getNode
import app.morphe.util.replaceStringMatchesWithFunc
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import org.w3c.dom.Element

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extension/continuum/APIUtils;"
internal const val FRAGMENT_CLASS_NAME = "Lml/docilealligator/infinityforreddit/customviews/preference/CustomFontPreferenceFragmentCompat;"
internal const val GET_USER_AGENT_METHOD = "$EXTENSION_CLASS_NAME->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"

val spoofClientPatch = spoofClientPatch(
    name = "Spoof client",
    description = "Allows modifying Continuum's client ID, redirect URI and user agent in API Keys settings menu. " +
        "Patch options will modify default values.",
) { clientIdOption, redirectUriOption, userAgentOption ->
    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    dependsOn(
        sharedExtensionPatch,
        resourcePatch(
            default = false
        ) {
            execute {
                document("res/xml/api_keys_preferences.xml").use { document ->
                    val rootNode = document.getNode("androidx.preference.PreferenceScreen")
                    val clientIdPreferenceNode = rootNode?.childNodes?.let { nodeList ->
                        (0 until nodeList.length)
                            .mapNotNull { nodeList.item(it) }
                            .first { it.attributes?.getNamedItem("android:key")?.nodeValue == "@string/client_id_pref_key" }
                    } as Element
                    clientIdPreferenceNode.setAttribute("android:defaultValue", clientId!!)

                    val redirectUriPreferenceNode = document.createElement("EditTextPreference").apply {
                        setAttribute("android:defaultValue", redirectUri!!)
                        setAttribute("android:key", "morphe_redirect_uri_pref_key")
                        setAttribute("android:title", "Redirect URI")
                        setAttribute("android:summary", "Tap to set custom redirect URI")
                        setAttribute("app:iconSpaceReserved", "false")
                        setAttribute("app:useSimpleSummaryProvider", "true")
                    }

                    val userAgentPreferenceNode = document.createElement("EditTextPreference").apply {
                        setAttribute("android:defaultValue", userAgent!!)
                        setAttribute("android:key", "morphe_user_agent_pref_key")
                        setAttribute("android:title", "User Agent")
                        setAttribute("android:summary", "Tap to set custom user agent")
                        setAttribute("app:iconSpaceReserved", "false")
                        setAttribute("app:useSimpleSummaryProvider", "true")
                    }

                    rootNode.insertBefore(redirectUriPreferenceNode, null)
                    rootNode.insertBefore(userAgentPreferenceNode, null)
                }
            }
        }
    )
    compatibleWith(*ContinuumCompatible)
    execute {
        getDefaultClientIdFingerprint.method.returnEarly(clientId!!)
        getDefaultRedirectUriFingerprint.method.returnEarly(redirectUri!!)
        getDefaultUserAgentFingerprint.method.returnEarly(userAgent!!)

        userAgentFingerprint(packageMetadata.versionName).matchAll().forEach { match ->
            match.replaceStringMatchesWithFunc(GET_USER_AGENT_METHOD)
        }

        redirectUriFingerprint.matchAll().forEach { match ->
            match.replaceStringMatchesWithFunc(GET_REDIRECT_URI_METHOD)
        }

        apiKeysOnCreatePreferencesFingerprint.method.apply {
            val inst = apiKeysOnCreatePreferencesFingerprint.instructionMatches.first().instruction
            val register = (inst as FiveRegisterInstruction).registerC
            val index = apiKeysOnCreatePreferencesFingerprint.instructionMatches.last().index
            addInstructions(
                index,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS_NAME->setupRedirectUriPreference($FRAGMENT_CLASS_NAME)V
                    invoke-static { v$register }, $EXTENSION_CLASS_NAME->setupUserAgentPreference($FRAGMENT_CLASS_NAME)V
                """
            )
        }
    }
}
