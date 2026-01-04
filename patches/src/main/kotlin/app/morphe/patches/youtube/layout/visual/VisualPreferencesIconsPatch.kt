package app.morphe.patches.youtube.layout.visual

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.youtube.layout.branding.icon.customBrandingIconPatch
import app.morphe.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.morphe.patches.youtube.utils.patch.PatchList.VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE
import app.morphe.patches.youtube.utils.settings.ResourceUtils.RVX_PREFERENCE_PATH
import app.morphe.patches.youtube.utils.settings.ResourceUtils.YOUTUBE_SETTINGS_PATH
import app.morphe.patches.youtube.utils.settings.ResourceUtils.addPreference
import app.morphe.patches.youtube.utils.settings.settingsPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.Utils.trimIndentMultiline
import app.morphe.util.copyResources
import app.morphe.util.doRecursively
import app.morphe.util.getStringOptionValue
import app.morphe.util.underBarOrThrow
import org.w3c.dom.Element

private const val DEFAULT_ICON = "extension"
private const val EMPTY_ICON = "empty_icon"

@Suppress("unused")
val visualPreferencesIconsPatch = resourcePatch(
    VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE.title,
    VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE.summary,
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

    val applyToAll by booleanOption(
        key = "applyToAll",
        default = false,
        title = "Apply to all settings menu",
        description = """
            Whether to apply Visual preferences icons to all settings menus.

            If true: icons are applied to the parent PreferenceScreen of YouTube settings, the parent PreferenceScreen of RVX settings and the RVX sub-settings (if supported).

            If false: icons are applied only to the parent PreferenceScreen of YouTube settings and RVX settings.
            """.trimIndentMultiline(),
        required = true
    )

    lateinit var preferenceIcon: Map<String, String>

    fun Set<String>.setPreferenceIcon() = associateWith { title ->
        when (title) {
            // Internal RVX settings
            "rvx_morphed_alt_thumbnail_home" -> "rvx_morphed_hide_navigation_home_button_icon"
            "rvx_morphed_alt_thumbnail_library" -> "rvx_morphed_preference_screen_video_icon"
            "rvx_morphed_alt_thumbnail_player" -> "rvx_morphed_preference_screen_player_icon"
            "rvx_morphed_alt_thumbnail_search" -> "rvx_morphed_hide_shorts_shelf_search_icon"
            "rvx_morphed_alt_thumbnail_subscriptions" -> "rvx_morphed_hide_navigation_subscriptions_button_icon"
            "rvx_morphed_change_share_sheet" -> "rvx_morphed_hide_share_button_icon"
            "rvx_morphed_change_shorts_background_repeat_state" -> "rvx_morphed_change_shorts_repeat_state_icon"
            "rvx_morphed_default_app_settings" -> "rvx_morphed_preference_screen_settings_menu_icon"
            "rvx_morphed_disable_shorts_background_playback" -> "offline_key_icon"
            "rvx_morphed_settings_search_history" -> "history_key_icon"
            "rvx_morphed_hide_download_button" -> "rvx_morphed_overlay_button_external_downloader_icon"
            "rvx_morphed_hide_keyword_content_home" -> "rvx_morphed_hide_navigation_home_button_icon"
            "rvx_morphed_hide_keyword_content_search" -> "rvx_morphed_hide_shorts_shelf_search_icon"
            "rvx_morphed_hide_keyword_content_subscriptions" -> "rvx_morphed_hide_navigation_subscriptions_button_icon"
            "rvx_morphed_hide_like_dislike_button" -> "sb_voting_button_icon"
            "rvx_morphed_hide_navigation_label" -> "rvx_morphed_swipe_text_overlay_size_icon"
            "rvx_morphed_hide_navigation_library_button" -> "rvx_morphed_preference_screen_video_icon"
            "rvx_morphed_hide_navigation_notifications_button" -> "notification_key_icon"
            "rvx_morphed_hide_navigation_shorts_button" -> "rvx_morphed_preference_screen_shorts_icon"
            "rvx_morphed_hide_player_autoplay_button" -> "rvx_morphed_change_player_flyout_menu_toggle_icon"
            "rvx_morphed_hide_player_captions_button" -> "captions_key_icon"
            "rvx_morphed_hide_player_flyout_menu_ambient_mode" -> "rvx_morphed_preference_screen_ambient_mode_icon"
            "rvx_morphed_hide_player_flyout_menu_captions" -> "captions_key_icon"
            "rvx_morphed_hide_player_flyout_menu_enhanced_bitrate" -> "video_quality_settings_key_icon"
            "rvx_morphed_hide_player_flyout_menu_listen_with_youtube_music" -> "rvx_morphed_hide_player_youtube_music_button_icon"
            "rvx_morphed_hide_player_flyout_menu_loop_video" -> "rvx_morphed_overlay_button_always_repeat_icon"
            "rvx_morphed_hide_player_flyout_menu_more_info" -> "about_key_icon"
            "rvx_morphed_hide_player_flyout_menu_pip" -> "offline_key_icon"
            "rvx_morphed_hide_player_flyout_menu_premium_controls" -> "premium_early_access_browse_page_key_icon"
            "rvx_morphed_hide_player_flyout_menu_report" -> "rvx_morphed_hide_report_button_icon"
            "rvx_morphed_hide_player_fullscreen_button" -> "rvx_morphed_preference_screen_fullscreen_icon"
            "rvx_morphed_hide_shorts_shelf" -> "rvx_morphed_preference_screen_shorts_icon"
            "rvx_morphed_hide_shorts_shelf_channel" -> "account_switcher_key_icon"
            "rvx_morphed_hide_shorts_shelf_home_related_videos" -> "rvx_morphed_hide_navigation_home_button_icon"
            "rvx_morphed_hide_shorts_shelf_subscriptions" -> "rvx_morphed_hide_navigation_subscriptions_button_icon"
            "rvx_morphed_open_shorts_in_regular_player" -> "rvx_morphed_preference_screen_player_icon"
            "rvx_morphed_preference_screen_account_menu" -> "account_switcher_key_icon"
            "rvx_morphed_preference_screen_channel_bar" -> "account_switcher_key_icon"
            "rvx_morphed_preference_screen_channel_page" -> "account_switcher_key_icon"
            "rvx_morphed_preference_screen_feed_flyout_menu" -> "rvx_morphed_preference_screen_player_flyout_menu_icon"
            "rvx_morphed_preference_screen_general" -> "general_key_icon"
            "rvx_morphed_preference_screen_haptic_feedback" -> "rvx_morphed_swipe_haptic_feedback_icon"
            "rvx_morphed_preference_screen_hook_buttons" -> "rvx_morphed_preference_screen_import_export_icon"
            "rvx_morphed_preference_screen_miniplayer" -> "offline_key_icon"
            "rvx_morphed_preference_screen_patch_information" -> "about_key_icon"
            "rvx_morphed_preference_screen_sb" -> "sb_create_new_segment_icon"
            "rvx_morphed_preference_screen_shorts_player" -> "rvx_morphed_preference_screen_shorts_icon"
            "rvx_morphed_preference_screen_snack_bar" -> "rvx_morphed_preference_screen_action_buttons_icon"
            "rvx_morphed_preference_screen_video_filter" -> "rvx_morphed_preference_screen_video_icon"
            "rvx_morphed_preference_screen_watch_history" -> "history_key_icon"
            "rvx_morphed_swipe_gestures_lock_mode" -> "rvx_morphed_hide_player_flyout_menu_lock_screen_icon"
            "rvx_morphed_swipe_overlay_progress_brightness_color" -> "rvx_morphed_swipe_brightness_icon"
            "rvx_morphed_swipe_overlay_progress_volume_color" -> "rvx_morphed_swipe_volume_icon"
            else -> "${title}_icon"
        }
    }

    execute {
        // Check patch options first.
        val selectedIconType = settingsMenuIconOption
            .underBarOrThrow()

        val appIconOption = customBrandingIconPatch
            .getStringOptionValue("appIcon")

        val customBrandingIconType = appIconOption
            .underBarOrThrow()

        if (applyToAll == true) {
            preferenceKey += rvxPreferenceKey
        }

        preferenceIcon = preferenceKey.setPreferenceIcon()

        // region copy shared resources.

        arrayOf(
            ResourceGroup(
                "drawable",
                *preferenceIcon.values.map { "$it.xml" }.toTypedArray()
            ),
            ResourceGroup(
                "drawable-xxhdpi",
                "$EMPTY_ICON.png"
            ),
        ).forEach { resourceGroup ->
            copyResources("youtube/visual/shared", resourceGroup)
        }

        // endregion.

        // region copy RVX settings menu icon.

        val fallbackIconPath = "youtube/visual/icons/extension"
        val iconPath = when (selectedIconType) {
            "custom_branding_icon" -> "youtube/branding/$customBrandingIconType/settings"
            else -> "youtube/visual/icons/$selectedIconType"
        }
        val resourceGroup = ResourceGroup(
            "drawable",
            "rvx_morphed_settings_key_icon.xml"
        )

        try {
            copyResources(iconPath, resourceGroup)
        } catch (_: Exception) {
            // Ignore if resource copy fails

            // Add a fallback extended icon
            // It's needed if someone provides custom path to icon(s) folder
            // but custom branding icons for Extended setting are predefined,
            // so it won't copy custom branding icon
            // and will raise an error without fallback icon
            copyResources(fallbackIconPath, resourceGroup)
        }

        // endregion.

        addPreference(VISUAL_PREFERENCES_ICONS_FOR_YOUTUBE)
    }

    finalize {
        // region set visual preferences icon.

        arrayOf(
            RVX_PREFERENCE_PATH,
            YOUTUBE_SETTINGS_PATH
        ).forEach { xmlFile ->
            document(xmlFile).use { document ->
                document.doRecursively loop@{ node ->
                    if (node !is Element) return@loop

                    node.getAttributeNode("android:key")
                        ?.textContent
                        ?.removePrefix("@string/")
                        ?.let { title ->
                            val drawableName = when (title) {
                                in preferenceKey -> preferenceIcon[title]

                                // Add custom RVX settings menu icon
                                in intentKey -> intentIcon[title]
                                in emptyTitles -> EMPTY_ICON
                                else -> null
                            }
                            if (drawableName == EMPTY_ICON &&
                                applyToAll == false
                            ) return@loop

                            drawableName?.let {
                                node.setAttribute("android:icon", "@drawable/$it")
                            }
                        }
                }

                // overlay buttons empty icon
                if (applyToAll == true) {
                    val tags = document.getElementsByTagName("PreferenceScreen")
                    List(tags.length) { tags.item(it) as Element }
                        .find { it.getAttribute("android:key") == "rvx_morphed_preference_screen_player_buttons" }
                        ?.let { pref ->
                            val childNodes = pref.childNodes
                            for (i in 0 until childNodes.length) {
                                val node = childNodes.item(i) as? Element
                                node?.getAttributeNode("android:key")
                                    ?.textContent
                                    ?.let { key ->
                                        if (emptyTitlesOverlayButtons.contains(key)) {
                                            node.setAttribute(
                                                "android:icon",
                                                "@drawable/$EMPTY_ICON"
                                            )
                                        }
                                    }
                            }
                        }
                }
            }
        }

        // endregion.
    }
}

private var preferenceKey = setOf(
    // YouTube settings.
    "about_key",
    "accessibility_settings_key",
    "account_switcher_key",
    "auto_play_key",
    "billing_and_payment_key",
    "captions_key",
    "connected_accounts_browse_page_key",
    "data_saving_settings_key",
    "general_key",
    "history_key",
    "live_chat_key",
    "notification_key",
    "offline_key",
    "pair_with_tv_key",
    "parent_tools_key",
    "playback_key",
    "premium_early_access_browse_page_key",
    "privacy_key",
    "subscription_product_setting_key",
    "video_quality_settings_key",
    "your_data_key",

    // RVX settings.
    "rvx_morphed_preference_screen_ads",
    "rvx_morphed_preference_screen_alt_thumbnails",
    "rvx_morphed_preference_screen_feed",
    "rvx_morphed_preference_screen_general",
    "rvx_morphed_preference_screen_player",
    "rvx_morphed_preference_screen_shorts",
    "rvx_morphed_preference_screen_swipe_controls",
    "rvx_morphed_preference_screen_video",
    "rvx_morphed_preference_screen_ryd",
    "rvx_morphed_preference_screen_return_youtube_username",
    "rvx_morphed_preference_screen_sb",
    "rvx_morphed_preference_screen_misc",
)

private var rvxPreferenceKey = setOf(
    // Internal RVX settings (items without prefix are listed first, others are sorted alphabetically)
    "gms_core_settings",
    "sb_create_new_segment",
    "sb_voting_button",

    "rvx_morphed_alt_thumbnail_home",
    "rvx_morphed_alt_thumbnail_library",
    "rvx_morphed_alt_thumbnail_player",
    "rvx_morphed_alt_thumbnail_search",
    "rvx_morphed_alt_thumbnail_subscriptions",
    "rvx_morphed_bypass_url_redirects",
    "rvx_morphed_change_player_flyout_menu_toggle",
    "rvx_morphed_change_share_sheet",
    "rvx_morphed_change_shorts_background_repeat_state",
    "rvx_morphed_change_shorts_repeat_state",
    "rvx_morphed_default_app_settings",
    "rvx_morphed_disable_hdr_auto_brightness",
    "rvx_morphed_disable_quic_protocol",
    "rvx_morphed_disable_resuming_shorts_player",
    "rvx_morphed_disable_shorts_background_playback",
    "rvx_morphed_disable_swipe_to_switch_video",
    "rvx_morphed_settings_search_history",
    "rvx_morphed_hide_ask_button",
    "rvx_morphed_hide_clip_button",
    "rvx_morphed_hide_download_button",
    "rvx_morphed_hide_hype_button",
    "rvx_morphed_hide_keyword_content_comments",
    "rvx_morphed_hide_keyword_content_home",
    "rvx_morphed_hide_keyword_content_search",
    "rvx_morphed_hide_keyword_content_subscriptions",
    "rvx_morphed_hide_like_dislike_button",
    "rvx_morphed_hide_navigation_create_button",
    "rvx_morphed_hide_navigation_home_button",
    "rvx_morphed_hide_navigation_label",
    "rvx_morphed_hide_navigation_library_button",
    "rvx_morphed_hide_navigation_notifications_button",
    "rvx_morphed_hide_navigation_shorts_button",
    "rvx_morphed_hide_navigation_subscriptions_button",
    "rvx_morphed_hide_player_autoplay_button",
    "rvx_morphed_hide_player_captions_button",
    "rvx_morphed_hide_player_cast_button",
    "rvx_morphed_hide_player_collapse_button",
    "rvx_morphed_hide_player_flyout_menu_ambient_mode",
    "rvx_morphed_hide_player_flyout_menu_audio_track",
    "rvx_morphed_hide_player_flyout_menu_captions",
    "rvx_morphed_hide_player_flyout_menu_enhanced_bitrate",
    "rvx_morphed_hide_player_flyout_menu_help",
    "rvx_morphed_hide_player_flyout_menu_listen_with_youtube_music",
    "rvx_morphed_hide_player_flyout_menu_lock_screen",
    "rvx_morphed_hide_player_flyout_menu_loop_video",
    "rvx_morphed_hide_player_flyout_menu_more_info",
    "rvx_morphed_hide_player_flyout_menu_pip",
    "rvx_morphed_hide_player_flyout_menu_playback_speed",
    "rvx_morphed_hide_player_flyout_menu_premium_controls",
    "rvx_morphed_hide_player_flyout_menu_quality_header",
    "rvx_morphed_hide_player_flyout_menu_report",
    "rvx_morphed_hide_player_flyout_menu_sleep_timer",
    "rvx_morphed_hide_player_flyout_menu_stable_volume",
    "rvx_morphed_hide_player_flyout_menu_stats_for_nerds",
    "rvx_morphed_hide_player_flyout_menu_watch_in_vr",
    "rvx_morphed_hide_player_fullscreen_button",
    "rvx_morphed_hide_player_previous_next_button",
    "rvx_morphed_hide_player_youtube_music_button",
    "rvx_morphed_hide_playlist_button",
    "rvx_morphed_hide_remix_button",
    "rvx_morphed_hide_report_button",
    "rvx_morphed_hide_rewards_button",
    "rvx_morphed_hide_share_button",
    "rvx_morphed_hide_shop_button",
    "rvx_morphed_hide_shorts_floating_button",
    "rvx_morphed_hide_shorts_shelf",
    "rvx_morphed_hide_shorts_shelf_channel",
    "rvx_morphed_hide_shorts_shelf_history",
    "rvx_morphed_hide_shorts_shelf_home_related_videos",
    "rvx_morphed_hide_shorts_shelf_search",
    "rvx_morphed_hide_shorts_shelf_subscriptions",
    "rvx_morphed_hide_thanks_button",
    "rvx_morphed_language",
    "rvx_morphed_open_links_externally",
    "rvx_morphed_open_shorts_in_regular_player",
    "rvx_morphed_overlay_button_always_repeat",
    "rvx_morphed_overlay_button_copy_video_url",
    "rvx_morphed_overlay_button_copy_video_url_timestamp",
    "rvx_morphed_overlay_button_external_downloader",
    "rvx_morphed_overlay_button_mute_volume",
    "rvx_morphed_overlay_button_play_all",
    "rvx_morphed_overlay_button_speed_dialog",
    "rvx_morphed_overlay_button_whitelist",
    "rvx_morphed_preference_screen_account_menu",
    "rvx_morphed_preference_screen_action_buttons",
    "rvx_morphed_preference_screen_ambient_mode",
    "rvx_morphed_preference_screen_carousel_shelf",
    "rvx_morphed_preference_screen_category_bar",
    "rvx_morphed_preference_screen_channel_bar",
    "rvx_morphed_preference_screen_channel_page",
    "rvx_morphed_preference_screen_comments",
    "rvx_morphed_preference_screen_community_posts",
    "rvx_morphed_preference_screen_custom_filter",
    "rvx_morphed_preference_screen_debugging",
    "rvx_morphed_preference_screen_feed_flyout_menu",
    "rvx_morphed_preference_screen_fullscreen",
    "rvx_morphed_preference_screen_haptic_feedback",
    "rvx_morphed_preference_screen_hook_buttons",
    "rvx_morphed_preference_screen_import_export",
    "rvx_morphed_preference_screen_miniplayer",
    "rvx_morphed_preference_screen_navigation_bar",
    "rvx_morphed_preference_screen_patch_information",
    "rvx_morphed_preference_screen_player_buttons",
    "rvx_morphed_preference_screen_player_flyout_menu",
    "rvx_morphed_preference_screen_seekbar",
    "rvx_morphed_preference_screen_settings_menu",
    "rvx_morphed_preference_screen_shorts_player",
    "rvx_morphed_preference_screen_snack_bar",
    "rvx_morphed_preference_screen_spoof_streaming_data",
    "rvx_morphed_preference_screen_toolbar",
    "rvx_morphed_preference_screen_video_description",
    "rvx_morphed_preference_screen_video_filter",
    "rvx_morphed_preference_screen_watch_history",
    "rvx_morphed_sanitize_sharing_links",
    "rvx_morphed_swipe_brightness",
    "rvx_morphed_swipe_gestures_lock_mode",
    "rvx_morphed_swipe_haptic_feedback",
    "rvx_morphed_swipe_lowest_value_enable_auto_brightness",
    "rvx_morphed_swipe_overlay_background_opacity",
    "rvx_morphed_swipe_overlay_progress_brightness_color",
    "rvx_morphed_swipe_overlay_progress_volume_color",
    "rvx_morphed_swipe_overlay_rect_size",
    "rvx_morphed_swipe_overlay_style",
    "rvx_morphed_swipe_overlay_timeout",
    "rvx_morphed_swipe_press_to_engage",
    "rvx_morphed_swipe_save_and_restore_brightness",
    "rvx_morphed_swipe_text_overlay_size",
    "rvx_morphed_swipe_threshold",
    "rvx_morphed_swipe_volume",
    "rvx_morphed_switch_create_with_notifications_button",
)

private val intentKey = setOf(
    "rvx_morphed_settings_key",
)

val intentIcon = intentKey.associateWith { "${it}_icon" }

private val emptyTitles = setOf(
    "rvx_morphed_disable_like_dislike_glow",
    "rvx_morphed_disable_swipe_to_enter_fullscreen_mode_below_the_player",
    "rvx_morphed_disable_swipe_to_enter_fullscreen_mode_in_the_player",
    "rvx_morphed_disable_swipe_to_exit_fullscreen_mode",
    "rvx_morphed_enable_narrow_navigation_buttons",
    "rvx_morphed_fix_swipe_tap_and_hold_speed",
    "rvx_morphed_hide_player_flyout_menu_captions_footer",
    "rvx_morphed_hide_player_flyout_menu_quality_footer",
    "rvx_morphed_hide_stop_ads_button",
    "rvx_morphed_swipe_brightness_distance_dip",
    "rvx_morphed_swipe_volumes_sensitivity",
)

private val emptyTitlesOverlayButtons = setOf(
    "rvx_morphed_overlay_button_external_downloader_queue_manager",
    "rvx_morphed_external_downloader_package_name_video",
    "rvx_morphed_overlay_button_speed_dialog_type",
    "rvx_morphed_overlay_button_play_all_type",
    "rvx_morphed_whitelist_settings",
)