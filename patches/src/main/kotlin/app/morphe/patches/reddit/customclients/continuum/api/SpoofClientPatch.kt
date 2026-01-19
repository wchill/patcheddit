package app.morphe.patches.reddit.customclients.continuum.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.transformation.transformInstructionsPatch
import app.morphe.patches.reddit.customclients.continuum.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.getNode
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val EXTENSION_CLASS_NAME = "Lapp/morphe/extensions/continuum/APIUtils;"
internal const val FRAGMENT_CLASS_NAME = "Lml/docilealligator/infinityforreddit/customviews/preference/CustomFontPreferenceFragmentCompat;"
internal const val GET_USER_AGENT_METHOD = "$EXTENSION_CLASS_NAME->getUserAgent"
internal const val GET_REDIRECT_URI_METHOD = "$EXTENSION_CLASS_NAME->getRedirectUri"

internal val settingsPatch = resourcePatch {
    dependsOn(
        addResourcesPatch
    )
    compatibleWith("org.cygnusx1.continuum")

    execute {
        addAppResources("continuum")

        document("res/xml/api_keys_preferences.xml").use { document ->
            val rootNode = document.getNode("PreferenceScreen")
            assert(rootNode.childNodes.length == 2)
            val giphyPreferenceNode = rootNode.childNodes.item(1)

            val redirectUriPreferenceNode = document.createElement("EditTextPreference").apply {
                setAttribute("android:defaultValue", "@string/morphe_default_redirect_uri")
                setAttribute("android:key", "@string/morphe_redirect_uri_pref_key")
                setAttribute("android:title", "@string/morphe_settings_redirect_uri_title")
                setAttribute("android:summary", "@string/morphe_tap_to_set_redirect_uri")
                setAttribute("app:iconSpaceReserved", "false")
                setAttribute("app:useSimpleSummaryProvider", "true")
            }

            val userAgentPreferenceNode = document.createElement("EditTextPreference").apply {
                setAttribute("android:key", "@string/morphe_user_agent_pref_key")
                setAttribute("android:title", "@string/morphe_settings_user_agent_title")
                setAttribute("android:summary", "@string/morphe_tap_to_set_user_agent")
                setAttribute("app:iconSpaceReserved", "false")
                setAttribute("app:useSimpleSummaryProvider", "true")
            }

            rootNode.insertBefore(redirectUriPreferenceNode, giphyPreferenceNode)
            rootNode.insertBefore(userAgentPreferenceNode, giphyPreferenceNode)
        }
    }
}

val spoofClientPatch = spoofClientPatch(
    name = "Spoof client",
    description = "Allows modifying Continuum's client ID, redirect URI and user agent in API Keys settings menu. " +
        "Patch options will modify default values.",
) { clientIdOption, redirectUriOption, userAgentOption ->
    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption
    dependsOn(
        settingsPatch,
        sharedExtensionPatch,
        resourcePatch {
            execute {
                if (clientId == null) {
                    return@execute
                }
                document("res/values/strings.xml").use { document ->
                    val nodeList = document.getElementsByTagName("string")
                    for (i in 0 until nodeList.length) {
                        val node = nodeList.item(i)
                        if (node.nodeName == "default_client_id") {
                            node.nodeValue = clientId!!.trim()
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
                if (stringReference?.string?.matches(Regex("""android:org\.cygnusx1\.continuum:\d\.\d\.\d\.\d (by /u/edgan)""")) == true) {
                    return@transformInstructionsPatch Triple(stringReference.string, instruction, instructionIndex)
                }

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
                } else {
                    method.addInstructions(index,
                        """
                        invoke-static {}, $GET_USER_AGENT_METHOD()Ljava/lang/String;
                        move-result-object v$register
                    """
                    )
                }
            }
        )
    )
    compatibleWith("org.cygnusx1.continuum")
    execute {
        if (clientId != null) {
            getDefaultClientIdFingerprint.method.returnEarly(clientId!!.trim())
        }

        if (redirectUri != null) {
            getDefaultRedirectUriFingerprint.method.returnEarly(redirectUri!!.trim())
        }

        if (userAgent != null) {
            getDefaultUserAgentFingerprint.method.returnEarly(userAgent!!.trim())
        }

        apiKeysOnCreatePreferencesFingerprint.method.apply {
            val inst = apiKeysOnCreatePreferencesFingerprint.instructionMatches.first().instruction
            val register = (inst as OneRegisterInstruction).registerA
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
