/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.continuum.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.transformation.transformInstructionsPatch
import app.morphe.patches.reddit.customclients.continuum.ContinuumCompatible
import app.morphe.patches.reddit.customclients.continuum.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.copyResources
import app.morphe.util.getNode
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extension/continuum/APIUtils;"
internal const val FRAGMENT_CLASS_NAME = "Lml/docilealligator/infinityforreddit/customviews/preference/CustomFontPreferenceFragmentCompat;"
internal const val GET_USER_AGENT_METHOD = "$EXTENSION_CLASS_NAME->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"

internal val settingsPatch = resourcePatch(
    default = true
) {
    dependsOn(
        addResourcesPatch
    )
    compatibleWith(*ContinuumCompatible)

    execute {
        document("res/xml/api_keys_preferences.xml").use { document ->
            val rootNode = document.getNode("androidx.preference.PreferenceScreen")
            val redirectUriPreferenceNode = document.createElement("EditTextPreference").apply {
                setAttribute("android:defaultValue", "continuum://localhost")
                setAttribute("android:key", "morphe_redirect_uri_pref_key")
                setAttribute("android:title", "Redirect URI")
                setAttribute("android:summary", "Tap to set custom redirect URI")
                setAttribute("app:iconSpaceReserved", "false")
                setAttribute("app:useSimpleSummaryProvider", "true")
            }

            val userAgentPreferenceNode = document.createElement("EditTextPreference").apply {
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

val spoofClientPatch = spoofClientPatch(
    name = "Spoof client",
    description = "Allows modifying Continuum's client ID, redirect URI and user agent in API Keys settings menu. " +
        "Patch options will modify default values.",
) { clientIdOption, redirectUriOption, userAgentOption ->
    val clientId = clientIdOption.value!!
    dependsOn(
        settingsPatch,
        sharedExtensionPatch,
        resourcePatch(
            default = false
        ) {
            execute {
                document("res/values/strings.xml").use { document ->
                    val nodeList = document.getElementsByTagName("string")
                    for (i in 0 until nodeList.length) {
                        val node = nodeList.item(i)
                        if (node.attributes?.getNamedItem("name")?.nodeValue == "default_client_id") {
                            node.textContent = clientId
                            break
                        }
                    }
                }
            }
        },
        transformInstructionsPatch(
            filterMap = { classDef, _, instruction, instructionIndex ->
                if (classDef.type.startsWith("Lapp/morphe/")) {
                    return@transformInstructionsPatch null
                }

                if (instruction.opcode != Opcode.CONST_STRING) {
                    return@transformInstructionsPatch null
                }

                val stringReference = instruction.getReference<StringReference>()
                if (stringReference?.string == "continuum://localhost") {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

                return@transformInstructionsPatch null
            },
            transform = { method, entry ->
                val (stringConst, instruction, index) = entry
                val register = (instruction as OneRegisterInstruction).registerA
                method.replaceInstruction(index, "nop")

                if (stringConst == "continuum://localhost") {
                    method.addInstructions(index,
                    """
                        invoke-static {}, $GET_REDIRECT_URI_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                    )
                }
            }
        )
    )
    compatibleWith(*ContinuumCompatible)
    execute {
        getDefaultClientIdFingerprint.method.returnEarly(clientIdOption.value!!)
        getDefaultRedirectUriFingerprint.method.returnEarly(redirectUriOption.value!!)
        getDefaultUserAgentFingerprint.method.returnEarly(userAgentOption.value!!)

        val userAgentFingerprint = Fingerprint(
            filters = listOf(
                string("android:org.cygnusx1.continuum:${packageMetadata.versionName} (by /u/edgan)")
            )
        )

        userAgentFingerprint.matchAll().forEach { match ->
            match.instructionMatches.forEach { instMatch ->
                val inst = instMatch.instruction
                val register = (inst as OneRegisterInstruction).registerA
                val index = instMatch.index
                match.method.replaceInstruction(index, "nop")
                match.method.addInstructions(
                    index,
                    """
                        invoke-static {}, $GET_USER_AGENT_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                )
            }
        }

        redirectUriFingerprint.matchAll().forEach { match ->
            match.instructionMatches.forEach { instMatch ->
                val inst = instMatch.instruction
                val register = (inst as OneRegisterInstruction).registerA
                val index = instMatch.index
                match.method.replaceInstruction(index, "nop")
                match.method.addInstructions(
                    index,
                    """
                        invoke-static {}, $GET_REDIRECT_URI_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                )
            }
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
