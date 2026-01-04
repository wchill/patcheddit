package app.morphe.patches.youtube.utils.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.extension.Constants.EXTENSION_THEME_UTILS_CLASS_DESCRIPTOR
import app.morphe.patches.shared.extension.Constants.EXTENSION_UTILS_CLASS_DESCRIPTOR
import app.morphe.patches.shared.mainactivity.injectConstructorMethodCall
import app.morphe.patches.shared.mainactivity.injectOnCreateMethodCall
import app.morphe.patches.shared.settings.baseSettingsPatch
import app.morphe.patches.youtube.utils.cairoFragmentConfigFingerprint
import app.morphe.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.morphe.patches.youtube.utils.extension.Constants.PATCH_STATUS_CLASS_DESCRIPTOR
import app.morphe.patches.youtube.utils.extension.Constants.UTILS_PATH
import app.morphe.patches.youtube.utils.extension.sharedExtensionPatch
import app.morphe.patches.youtube.utils.fix.attributes.themeAttributesPatch
import app.morphe.patches.youtube.utils.fix.playbackspeed.playbackSpeedWhilePlayingPatch
import app.morphe.patches.youtube.utils.fix.splash.darkModeSplashScreenPatch
import app.morphe.patches.youtube.utils.mainactivity.mainActivityResolvePatch
import app.morphe.patches.youtube.utils.patch.PatchList.SETTINGS_FOR_YOUTUBE
import app.morphe.patches.youtube.utils.playservice.is_19_16_or_greater
import app.morphe.patches.youtube.utils.playservice.is_19_34_or_greater
import app.morphe.patches.youtube.utils.playservice.versionCheckPatch
import app.morphe.patches.youtube.utils.resourceid.sharedResourceIdPatch
import app.morphe.patches.youtube.utils.settings.ResourceUtils.YOUTUBE_SETTINGS_PATH
import app.morphe.patches.youtube.utils.settingsFragmentSyntheticFingerprint
import app.morphe.util.FilesCompat
import app.morphe.util.ResourceGroup
import app.morphe.util.Utils.printWarn
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.className
import app.morphe.util.copyResources
import app.morphe.util.copyXmlNode
import app.morphe.util.findFreeRegister
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.findMethodOrThrow
import app.morphe.util.fingerprint.definingClassOrThrow
import app.morphe.util.fingerprint.methodCall
import app.morphe.util.fingerprint.methodOrThrow
import app.morphe.util.fingerprint.mutableClassOrThrow
import app.morphe.util.getReference
import app.morphe.util.hookClassHierarchy
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.insertNode
import app.morphe.util.removeStringsElements
import app.morphe.util.returnEarly
import app.morphe.util.valueOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import org.w3c.dom.Element
import java.nio.file.Files

private const val EXTENSION_INITIALIZATION_CLASS_DESCRIPTOR =
    "$UTILS_PATH/InitializationPatch;"

private lateinit var bytecodeContext: BytecodePatchContext

internal fun getBytecodeContext() = bytecodeContext

internal var cairoFragmentDisabled = false
private var targetActivityClassName = ""

private val settingsBytecodePatch = bytecodePatch(
    description = "settingsBytecodePatch"
) {
    dependsOn(
        sharedExtensionPatch,
        sharedResourceIdPatch,
        mainActivityResolvePatch,
        versionCheckPatch,
        baseSettingsPatch,
    )

    execute {
        bytecodeContext = this

        // region fix cairo fragment.

        /**
         * Disable Cairo fragment settings.
         * 1. Fix - When spoofing the app version to 19.20 or earlier, the app crashes or the Notifications tab is inaccessible.
         * 2. Fix - Preference 'Playback' is hidden.
         * 3. Some settings that were in Preference 'General' are moved to Preference 'Playback'.
         */
        // Cairo fragment have been widely rolled out in YouTube 19.34+.
        if (is_19_34_or_greater) {
            // Instead of disabling all Cairo fragment configs,
            // Just disable 'Load Cairo fragment xml' and 'Set style to Cairo preference'.
            fun MutableMethod.disableCairoFragmentConfig() {
                val cairoFragmentConfigMethodCall = cairoFragmentConfigFingerprint
                    .methodCall()
                val insertIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                            getReference<MethodReference>()?.toString() == cairoFragmentConfigMethodCall
                } + 2
                val insertRegister =
                    getInstruction<OneRegisterInstruction>(insertIndex - 1).registerA

                addInstruction(insertIndex, "const/4 v$insertRegister, 0x0")
            }

            try {
                arrayOf(
                    // Load cairo fragment xml.
                    settingsFragmentSyntheticFingerprint
                        .methodOrThrow(),
                    // Set style to cairo preference.
                    settingsFragmentStylePrimaryFingerprint
                        .methodOrThrow(),
                    settingsFragmentStyleSecondaryFingerprint
                        .methodOrThrow(settingsFragmentStylePrimaryFingerprint),
                ).forEach { method ->
                    method.disableCairoFragmentConfig()
                }
                cairoFragmentDisabled = true
            } catch (_: Exception) {
                cairoFragmentConfigFingerprint
                    .methodOrThrow()
                    .returnEarly()

                printWarn("Failed to restore 'Playback' settings. 'Autoplay next video' setting may not appear in the YouTube settings.")
            }
        }

        // endregion.

        val hostAbstractActivityClass = baseHostActivityOnCreateFingerprint.mutableClassOrThrow()
        val hostActivityClass = youtubeHostActivityOnCreateFingerprint.mutableClassOrThrow()
        val targetActivityClass = licenseMenuActivityOnCreateFingerprint.mutableClassOrThrow()

        hookClassHierarchy(
            hostActivityClass,
            targetActivityClass,
            hostAbstractActivityClass,
        )

        targetActivityClass.methods.forEach { method ->
            method.apply {
                if (!MethodUtil.isConstructor(method) && returnType == "V") {
                    val insertIndex =
                        indexOfFirstInstruction(Opcode.INVOKE_SUPER) + 1
                    if (insertIndex > 0) {
                        val freeRegister = findFreeRegister(insertIndex)

                        addInstructionsWithLabels(
                            insertIndex, """
                                invoke-virtual {p0}, ${hostAbstractActivityClass.type}->isInitialized()Z
                                move-result v$freeRegister
                                if-eqz v$freeRegister, :ignore
                                return-void
                                :ignore
                                nop
                                """
                        )
                    }
                }
            }
        }

        targetActivityClassName = targetActivityClass.type.className
        findMethodOrThrow(PATCH_STATUS_CLASS_DESCRIPTOR) {
            name == "TargetActivityClass"
        }.returnEarly(targetActivityClassName)

        // apply the current theme of the settings page
        themeSetterSystemFingerprint.methodOrThrow().apply {
            findInstructionIndicesReversedOrThrow(Opcode.RETURN_OBJECT).forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA

                addInstructionsAtControlFlowLabel(
                    index,
                    "invoke-static { v$register }, $EXTENSION_THEME_UTILS_CLASS_DESCRIPTOR->updateLightDarkModeStatus(Ljava/lang/Enum;)V"
                )
            }
        }

        if (is_19_16_or_greater) {
            val userInterfaceThemeEnum = userInterfaceThemeEnumFingerprint
                .definingClassOrThrow()

            clientContextBodyBuilderFingerprint.methodOrThrow().apply {
                findInstructionIndicesReversedOrThrow {
                    val fieldReference = getReference<FieldReference>()
                    opcode == Opcode.IGET &&
                            fieldReference?.definingClass == userInterfaceThemeEnum &&
                            fieldReference.type == "I"
                }.forEach { index ->
                    val register = getInstruction<TwoRegisterInstruction>(index).registerA

                    addInstruction(
                        index + 1,
                        "invoke-static { v$register }, $EXTENSION_THEME_UTILS_CLASS_DESCRIPTOR->updateLightDarkModeStatus(I)V",
                    )
                }
            }
        }

        injectOnCreateMethodCall(
            EXTENSION_INITIALIZATION_CLASS_DESCRIPTOR,
            "onCreate"
        )
        injectConstructorMethodCall(
            EXTENSION_UTILS_CLASS_DESCRIPTOR,
            "setActivity"
        )
    }
}

private const val DEFAULT_ELEMENT = "@string/parent_tools_key"
private const val DEFAULT_LABEL = "RVX"

private val SETTINGS_ELEMENTS_MAP = mapOf(
    "Parent settings" to DEFAULT_ELEMENT,
    "General" to "@string/general_key",
    "Account" to "@string/account_switcher_key",
    "Data saving" to "@string/data_saving_settings_key",
    "Autoplay" to "@string/auto_play_key",
    "Video quality preferences" to "@string/video_quality_settings_key",
    "Background" to "@string/offline_key",
    "Watch on TV" to "@string/pair_with_tv_key",
    "Manage all history" to "@string/history_key",
    "Your data in YouTube" to "@string/your_data_key",
    "Privacy" to "@string/privacy_key",
    "History & privacy" to "@string/privacy_key",
    "Try experimental new features" to "@string/premium_early_access_browse_page_key",
    "Purchases and memberships" to "@string/subscription_product_setting_key",
    "Billing & payments" to "@string/billing_and_payment_key",
    "Billing and payments" to "@string/billing_and_payment_key",
    "Notifications" to "@string/notification_key",
    "Connected apps" to "@string/connected_accounts_browse_page_key",
    "Live chat" to "@string/live_chat_key",
    "Captions" to "@string/captions_key",
    "Accessibility" to "@string/accessibility_settings_key",
    "About" to "@string/about_key"
)

private lateinit var settingsLabel: String

val settingsPatch = resourcePatch(
    SETTINGS_FOR_YOUTUBE.title,
    SETTINGS_FOR_YOUTUBE.summary,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(
        settingsBytecodePatch,
        darkModeSplashScreenPatch,
        playbackSpeedWhilePlayingPatch,
        themeAttributesPatch,
    )

    val insertPosition = stringOption(
        key = "insertPosition",
        default = DEFAULT_ELEMENT,
        values = SETTINGS_ELEMENTS_MAP,
        title = "Insert position",
        description = "The settings menu name that the RVX settings menu should be above.",
        required = true,
    )

    val rvxSettingsLabel = stringOption(
        key = "rvxSettingsLabel",
        default = DEFAULT_LABEL,
        values = mapOf(
            "RVX Morphed" to "RVX Morphed",
            "RVX" to DEFAULT_LABEL,
        ),
        title = "RVX settings label",
        description = "The name of the RVX settings menu.",
        required = true,
    )

    execute {
        /**
         * check patch options
         */
        settingsLabel = rvxSettingsLabel
            .valueOrThrow()

        val insertKey = insertPosition
            .valueOrThrow()

        ResourceUtils.setContext(this)

        /**
         * remove strings duplicated with RVX resources
         *
         * YouTube does not provide translations for these strings.
         * That's why it's been added to RVX resources.
         * This string also exists in RVX resources, so it must be removed to avoid being duplicated.
         */
        removeStringsElements(
            arrayOf("values"),
            arrayOf(
                "accessibility_settings_edu_opt_in_text",
                "accessibility_settings_edu_opt_out_text"
            )
        )

        /**
         * copy arrays, strings and preference
         */
        arrayOf(
            "arrays.xml",
            "dimens.xml",
            "strings.xml",
            "styles.xml"
        ).forEach { xmlFile ->
            copyXmlNode("youtube/settings/host", "values/$xmlFile", "resources")
        }

        val valuesV21Directory = get("res").resolve("values-v21")
        if (!valuesV21Directory.isDirectory)
            Files.createDirectories(valuesV21Directory.toPath())

        copyResources(
            "youtube/settings",
            ResourceGroup(
                "values-v21",
                "strings.xml"
            )
        )

        arrayOf(
            ResourceGroup(
                "drawable",
                "rvx_morphed_settings_arrow_time.xml",
                "rvx_morphed_settings_cursor.xml",
                "rvx_morphed_settings_custom_checkmark.xml",
                "rvx_morphed_settings_icon.xml",
                "rvx_morphed_settings_rounded_corners_background.xml",
                "rvx_morphed_settings_search_icon.xml",
                "rvx_morphed_settings_search_remove.xml",
                "rvx_morphed_settings_toolbar_arrow_left.xml",
            ),
            ResourceGroup(
                "layout",
                "rvx_morphed_color_dot_widget.xml",
                "rvx_morphed_color_picker.xml",
                "rvx_morphed_custom_list_item_checked.xml",
                "rvx_morphed_preference_search_history_item.xml",
                "rvx_morphed_preference_search_history_screen.xml",
                "rvx_morphed_preference_search_no_result.xml",
                "rvx_morphed_preference_search_result_color.xml",
                "rvx_morphed_preference_search_result_group_header.xml",
                "rvx_morphed_preference_search_result_list.xml",
                "rvx_morphed_preference_search_result_regular.xml",
                "rvx_morphed_preference_search_result_switch.xml",
                "rvx_morphed_settings_preferences_category.xml",
                "rvx_morphed_settings_with_toolbar.xml",
            ),
            ResourceGroup(
                "menu",
                "rvx_morphed_search_menu.xml",
            ),
            ResourceGroup(
                "xml",
                "rvx_morphed_prefs.xml",
            )
        ).forEach { resourceGroup ->
            copyResources("youtube/settings", resourceGroup)
        }

        /**
         * initialize RVX Morphed Settings
         */
        ResourceUtils.addPreferenceFragment(
            "rvx_morphed_settings",
            insertKey,
            targetActivityClassName,
        )

        /**
         * remove RVX Morphed Settings divider
         */
        document("res/values/styles.xml").use { document ->
            val themeNames = arrayOf("Theme.YouTube.Settings", "Theme.YouTube.Settings.Dark")
            with(document) {
                val resourcesNode = documentElement
                val childNodes = resourcesNode.childNodes

                for (i in 0 until childNodes.length) {
                    val node = childNodes.item(i) as? Element ?: continue

                    if (node.getAttribute("name") in themeNames) {
                        val newElement = createElement("item").apply {
                            setAttribute("name", "android:listDivider")
                            appendChild(createTextNode("@null"))
                        }
                        node.appendChild(newElement)
                    }
                }
            }
        }
    }

    finalize {
        /**
         * change RVX settings menu name
         * since it must be invoked after the Translations patch, it must be the last in the order.
         */
        if (settingsLabel != DEFAULT_LABEL) {
            removeStringsElements(
                arrayOf("rvx_morphed_settings_title")
            )
            document("res/values/strings.xml").use { document ->
                mapOf(
                    "rvx_morphed_settings_title" to settingsLabel
                ).forEach { (k, v) ->
                    val stringElement = document.createElement("string")

                    stringElement.setAttribute("name", k)
                    stringElement.textContent = v

                    document.getElementsByTagName("resources").item(0)
                        .appendChild(stringElement)
                }
            }
        }

        /**
         * Disable Cairo fragment settings.
         */
        if (cairoFragmentDisabled) {
            /**
             * If the app version is spoofed to 19.30 or earlier due to the Spoof app version patch,
             * the 'Playback' setting will be broken.
             * If the app version is spoofed, the previous fragment must be used.
             */
            val xmlDirectory = get("res").resolve("xml")
            FilesCompat.copy(
                xmlDirectory.resolve("settings_fragment.xml"),
                xmlDirectory.resolve("settings_fragment_legacy.xml")
            )

            /**
             * The Preference key for 'Playback' is '@string/playback_key'.
             * Copy the node to add the Preference 'Playback' to the legacy settings fragment.
             */
            document(YOUTUBE_SETTINGS_PATH).use { document ->
                val tags = document.getElementsByTagName("Preference")
                List(tags.length) { tags.item(it) as Element }
                    .find { it.getAttribute("android:key") == "@string/auto_play_key" }
                    ?.let { node ->
                        node.insertNode("Preference", node) {
                            for (index in 0 until node.attributes.length) {
                                with(node.attributes.item(index)) {
                                    setAttribute(nodeName, nodeValue)
                                }
                            }
                            setAttribute("android:key", "@string/playback_key")
                        }
                    }
            }
        }
    }
}
