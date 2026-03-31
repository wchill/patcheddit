/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.infinity.InfinityCompatible
import app.morphe.patches.reddit.customclients.infinity.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getNode
import app.morphe.util.insertFirst
import app.morphe.util.registersUsed
import app.morphe.util.replaceStringMatchesWithFunc
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extension/infinity/APIUtils;"
internal const val FRAGMENT_CLASS_NAME = "app.morphe.extension.infinity.APIKeysPreferenceFragment"
internal const val GET_CLIENT_ID_METHOD = "$EXTENSION_CLASS_NAME->getClientId"
internal const val GET_USER_AGENT_METHOD = "$EXTENSION_CLASS_NAME->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"

private val disableBillingPatch = bytecodePatch(
    default = false
) {
    execute {
        billingClientOnServiceConnectedFingerprint.methodOrNull?.returnEarly()
        infinityStartSubscriptionActivityFingerprint.method.returnEarly()
    }
}

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption, userAgentOption ->
    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    dependsOn(
        disableBillingPatch,
        sharedExtensionPatch,
        resourcePatch(
            default = false
        ) {
            execute {
                get("res/xml/api_keys_preferences.xml").utf8Writer().use { writer ->
                    writer.write("""
                        <?xml version="1.0" encoding="utf-8"?>
                        <androidx.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:app="http://schemas.android.com/apk/res-auto">
                        </androidx.preference.PreferenceScreen>
                    """.trimIndent())
                }
                document("res/xml/api_keys_preferences.xml").use { document ->
                    val rootNode = document.getNode("androidx.preference.PreferenceScreen")
                    val clientIdPreferenceNode = document.createElement("EditTextPreference").apply {
                        setAttribute("android:defaultValue", clientId!!)
                        setAttribute("android:key", "morphe_client_id_pref_key")
                        setAttribute("android:title", "Client ID")
                        setAttribute("android:summary", "Tap to set custom client ID")
                        setAttribute("app:iconSpaceReserved", "false")
                        setAttribute("app:useSimpleSummaryProvider", "true")
                    }

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

                    rootNode.insertBefore(clientIdPreferenceNode, null)
                    rootNode.insertBefore(redirectUriPreferenceNode, null)
                    rootNode.insertBefore(userAgentPreferenceNode, null)
                }

                document("res/xml/main_preferences.xml").use { document ->
                    val rootNode = document.getNode("androidx.preference.PreferenceScreen")
                    val apiPreferenceNode = document.createElement("ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceWithBackground").apply {
                        setAttribute("app:icon", "@drawable/ic_settings_day_night_24dp")
                        setAttribute("app:title", "Patcheddit settings")
                        setAttribute("app:summary", "Tap to open Patcheddit settings")
                        setAttribute("app:fragment", FRAGMENT_CLASS_NAME)
                    }

                    // TODO: Uncomment this when preference menu works
                    //rootNode.insertFirst(apiPreferenceNode)
                }
            }
        }
    )
    compatibleWith(*InfinityCompatible)
    execute {
        getDefaultClientIdFingerprint.method.returnEarly(clientId!!)
        getDefaultRedirectUriFingerprint.method.returnEarly(redirectUri!!)
        getDefaultUserAgentFingerprint.method.returnEarly(userAgent!!)

        clientIdFingerprint.matchAll().forEach { match ->
            match.method.apply {
                replaceInstruction(
                    match.instructionMatches[0].index,
                    """
                        invoke-static {}, $GET_CLIENT_ID_METHOD()Ljava/lang/String;
                    """
                )
            }
        }

        userAgentFingerprint(packageMetadata.versionName).matchAll().forEach { match ->
            match.replaceStringMatchesWithFunc(GET_USER_AGENT_METHOD)
        }

        redirectUriFingerprint.matchAll().forEach { match ->
            match.replaceStringMatchesWithFunc(GET_REDIRECT_URI_METHOD)
        }

        settingsActivityOnBackStackChangedFingerprint.match().apply {
            val match = instructionMatches[3]
            val index = match.index
            val regA = match.instruction.registersUsed[0]
            val regB = match.instruction.registersUsed[1]
            val regC = instructionMatches[6].instruction.registersUsed[0]

            // TODO: Jank, figure out how to reference the actual goto target
            method.addInstructionsWithLabels(
                index,
                """
                    instance-of v$regA, v$regB, Lapp/morphe/extension/infinity/APIKeysPreferenceFragment;
                    if-eqz v$regA, :skip
                    const-string v$regB, "API Keys"
                    invoke-virtual {v$regC, v$regB}, Landroid/app/Activity;->setTitle(Ljava/lang/CharSequence;)V
                """, ExternalLabel("skip", method.getInstruction(index))
            )
        }

        extensionSettingsFragmentMethodFingerprint.method.apply {
            name = baseCustomFragmentMethodFingerprint.method.name

            // Fix references to setPreferencesFromResource(int, String);
            extensionSetPreferencesFromResourceFingerprint.match().apply {
                val match = instructionMatches[0]
                val registers = match.instruction.registersUsed
                val index = match.index
                val calledMethod = (baseCustomFragmentMethodFingerprint.instructionMatches[0].instruction as ReferenceInstruction).reference.toString()
                method.replaceInstruction(index,
                    """
                        invoke-virtual { v${registers[0]}, v${registers[1]}, v${registers[2]} }, $calledMethod
                    """
                )
            }

            // Fix references to findPreference(String);
            extensionFindResourceFingerprint.match().apply {
                val match = instructionMatches[0]
                val registers = match.instruction.registersUsed
                val index = match.index
                val calledMethod = (baseCustomFragmentMethodFingerprint.instructionMatches[1].instruction as ReferenceInstruction).reference.toString()
                method.replaceInstruction(index,
                    """
                        invoke-virtual { v${registers[0]}, v${registers[1]} }, $calledMethod
                    """
                )
            }

            // TODO: Fix up references to Preference listeners

            val customFontFragmentType = classDefBy(baseCustomFragmentMethodFingerprint.classDef.superclass!!)
            val prefFragmentCompatType = classDefBy(customFontFragmentType.superclass!!)

        }
    }
}
