package app.morphe.patches.music.layout.visual

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.music.layout.branding.icon.customBrandingIconPatch
import app.morphe.patches.music.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.morphe.patches.music.utils.patch.PatchList.VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE_MUSIC
import app.morphe.patches.music.utils.settings.ResourceUtils.SETTINGS_HEADER_PATH
import app.morphe.patches.music.utils.settings.ResourceUtils.updatePatchStatus
import app.morphe.patches.music.utils.settings.settingsPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.doRecursively
import app.morphe.util.getStringOptionValue
import app.morphe.util.underBarOrThrow
import org.w3c.dom.Element

private const val DEFAULT_ICON = "extension"

@Suppress("unused")
val visualPreferencesIconsPatch = resourcePatch(
    VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE_MUSIC.title,
    VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE_MUSIC.summary,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(settingsPatch)

    val settingsMenuIconOption = stringOption(
        key = "settingsMenuIcon",
        default = DEFAULT_ICON,
        values = mapOf(
            "Custom branding icon" to "custom_branding_icon",
            "Extension" to DEFAULT_ICON,
            "Gear" to "gear",
            "RVX Letters" to "rvx_letters",
            "RVX Letters Bold" to "rvx_letters_bold",
            "YT alt" to "yt_alt",
        ),
        title = "RVX settings menu icon",
        description = "The icon for the RVX settings menu.",
        required = true,
    )

    execute {
        // Check patch options first.
        val selectedIconType = settingsMenuIconOption
            .underBarOrThrow()

        val appIconOption = customBrandingIconPatch
            .getStringOptionValue("appIcon")

        val customBrandingIconType = appIconOption
            .underBarOrThrow()

        // region copy shared resources.

        arrayOf(
            ResourceGroup(
                "drawable",
                *preferenceKey.map { it + "_icon.xml" }.toTypedArray()
            ),
        ).forEach { resourceGroup ->
            copyResources("music/visual/shared", resourceGroup)
        }

        // endregion.

        // region copy RVX settings menu icon.

        val iconPath = when (selectedIconType) {
            "custom_branding_icon" -> "music/branding/$customBrandingIconType/settings"
            else -> "music/visual/icons/$selectedIconType"
        }
        val resourceGroup = ResourceGroup(
            "drawable",
            "rvx_morphed_settings_icon.xml"
        )

        try {
            copyResources(iconPath, resourceGroup)
        } catch (_: Exception) {
            // Ignore if resource copy fails
        }

        // endregion.

        updatePatchStatus(VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE_MUSIC)

    }

    finalize {
        // region set visual preferences icon.

        document(SETTINGS_HEADER_PATH).use { document ->
            document.doRecursively loop@{ node ->
                if (node !is Element) return@loop

                node.getAttributeNode("android:key")
                    ?.textContent
                    ?.removePrefix("@string/")
                    ?.let { title ->
                        val drawableName = when (title) {
                            in preferenceKey -> title + "_icon"
                            else -> null
                        }

                        drawableName?.let {
                            node.setAttribute("android:icon", "@drawable/$it")
                        }
                    }
            }
        }

        // endregion.
    }
}


// region preference key and icon.

private val preferenceKey = setOf(
    // YouTube settings.
    "pref_key_parent_tools",
    "settings_header_general",
    "settings_header_playback",
    "settings_header_data_saving",
    "settings_header_downloads_and_storage",
    "settings_header_notifications",
    "settings_header_privacy_and_location",
    "settings_header_recommendations",
    "settings_header_paid_memberships",
    "settings_header_about_youtube_music",

    // RVX settings.
    "rvx_morphed_settings",

    "rvx_morphed_preference_screen_account",
    "rvx_morphed_preference_screen_action_bar",
    "rvx_morphed_preference_screen_ads",
    "rvx_morphed_preference_screen_flyout",
    "rvx_morphed_preference_screen_general",
    "rvx_morphed_preference_screen_navigation",
    "rvx_morphed_preference_screen_player",
    "rvx_morphed_preference_screen_settings",
    "rvx_morphed_preference_screen_video",
    "rvx_morphed_preference_screen_ryd",
    "rvx_morphed_preference_screen_return_youtube_username",
    "rvx_morphed_preference_screen_sb",
    "rvx_morphed_preference_screen_misc",
)

// endregion.


