/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.json.JSONException;
import org.json.JSONObject;

import app.morphe.extension.shared.Utils;

public final class MatureContentLock {
    public static final String KEY = "pref_patcheddit_mature_content_lock";
    private static final Set<String> OFF = Set.of(
            "pref_nsfw", "pref_load_nsfw_images", "pref_search_include_over_18",
            "pref_search_show_random_nsfw", "pref_recent_posts_save_nsfw", "pref_drawer_show_nsfw_switch",
            "pref_drawer_show_blur_switch",
            "prefsv1_over_18", "prefsv1_search_include_over_18", "PREFETCH_NSFW",
            "SAVE_NSFW_HISTORY", "REMEMBER_NSFW_SUBREDDITS",
            "new_filters_content_nsfw", "content_nsfw_search", "actions_random_nsfw",
            "load_nsfw", "presentNSFW", "show_nsfw", "showNSFWPosts", "storeNSFWHistory", "PREF_SHOW_NSFW_PREVIEW",
            "PREF_QUICK_NSFW_TOGGLE", "NSFWContent", "NSFWThumbnails", "nsfw", "only_nsfw",
            "autoplay_nsfw_videos");
    private static final Set<String> ON = Set.of(
            "USE_ACCOUNT_OVER18_SETTING", "nsfw_preference", "disable_nsfw_forever");
    private static final Set<String> RELATED = Set.of(
            "pref_blur_nsfw_images",
            "prefsv1_no_profanity", "prefsv1_label_nsfw", "selective_nsfw_previews_preference",
            "nsfw_previews_preference", "nsfw_recents", "nsfw_mute", "PREF_BLUR_NSFW_PREVIEW",
            "blur_nsfw", "mute_nsfw_video", "save_nsfw_media_in_different_folder", "nsfw_download_location");
    private static final Map<View, Float> BACON_ROW_ALPHA = new WeakHashMap<>();

    private MatureContentLock() {}

    public static boolean isLocked() {
        return Utils.isContextSet() && isLocked(Utils.getContext());
    }

    public static boolean isLocked(Context context) {
        return PatchSettings.preferences(context).getBoolean(KEY, false);
    }

    public static void enable(Context context) throws IOException {
        SharedPreferences preferences = PatchSettings.preferences(context);
        SharedPreferences.Editor editor = preferences.edit().putBoolean(KEY, true);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() instanceof Boolean) {
                if (forcesOff(key)) editor.putBoolean(key, false);
                else if (ON.contains(key)) editor.putBoolean(key, true);
            }
        }
        if (!editor.commit()) {
            throw new IOException("Could not save the mature content lock.");
        }
    }

    public static String exportBoostSetting(String json) throws JSONException {
        return isLocked() && json != null ? new JSONObject(json).put(KEY, true).toString() : json;
    }

    public static void restoreBoostSetting(Context context, boolean locked) throws IOException {
        if (locked || isLocked(context)) enable(context);
    }

    private static boolean forcesOff(String key) {
        return OFF.contains(key) || key.startsWith("allow_over18_")
                || (key.endsWith("_nsfw") && Utils.isContextSet()
                && Utils.getContext().getPackageName().startsWith("ml.docilealligator.infinityforreddit"));
    }

    public static boolean disablesPreference(String key) {
        return key != null && (forcesOff(key) || ON.contains(key) || RELATED.contains(key)) && isLocked();
    }

    public static boolean checked(String key, boolean value) {
        if (key != null && (forcesOff(key) || ON.contains(key)) && isLocked()) {
            return ON.contains(key);
        }
        return value;
    }

    public static boolean readBoolean(SharedPreferences preferences, String key, boolean fallback) {
        if (key != null && (forcesOff(key) || ON.contains(key)) && isLocked()) {
            return ON.contains(key);
        }
        return preferences.getBoolean(key, fallback);
    }

    public static boolean disablesBaconRow(Enum<?> setting) {
        return isLocked() && setting != null
                && ("PRESENT_NSFW".equals(setting.name()) || "LOAD_NSFW".equals(setting.name()));
    }

    public static void styleBaconRow(View row, Enum<?> setting) {
        if (row == null) return;
        if (disablesBaconRow(setting)) {
            BACON_ROW_ALPHA.putIfAbsent(row, row.getAlpha());
            row.setAlpha(0.5f);
        } else {
            Float previous = BACON_ROW_ALPHA.remove(row);
            if (previous != null) row.setAlpha(previous);
        }
    }

    public static void lockBoostSearch(View root) {
        if (isLocked()) {
            disableView(root, "include_nsfw_container", true);
            disableView(root, "filter_nsfw", true);
        }
    }

    public static void lockJoeyFilters(android.app.Activity activity) {
        if (!isLocked()) return;
        View root = activity.getWindow().getDecorView();
        for (String name : new String[]{
                "show_NSFW_switch_container", "show_NSFW_switch", "NSFW_preview_container",
                "show_NSFW_preview_switch", "nsfw_history_switch", "quick_nsfw_toggle_switch_container",
                "quick_nsfw_toggle_switch", "quick_nsfw_toggler_switch", "nsfw_comment_profile_pic_container",
                "nsfw_comment_profile_pic_switch"
        }) {
            disableView(root, name, true);
        }
        disableView(root, "blur_NSFW_container", false);
        disableView(root, "blur_NSFW_switch", false);
    }

    private static void disableView(View root, String name, boolean uncheck) {
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        if (id != 0) disableView(root.findViewById(id), uncheck);
    }

    private static void disableView(View view, boolean uncheck) {
        if (view == null) return;
        if (view instanceof CompoundButton && uncheck) {
            CompoundButton button = (CompoundButton) view;
            button.setOnCheckedChangeListener(null);
            button.setChecked(false);
        }
        view.setEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) disableView(group.getChildAt(i), uncheck);
        }
    }

    @SuppressWarnings("deprecation")
    public static void lockNativeFragment(android.preference.PreferenceFragment fragment) {
        if (isLocked()) lockNativePreferences(fragment.getPreferenceScreen());
    }

    @SuppressWarnings("deprecation")
    public static void lockNativeActivity(android.preference.PreferenceActivity activity) {
        if (isLocked()) lockNativePreferences(activity.getPreferenceScreen());
    }

    @SuppressWarnings("deprecation")
    public static void lockNativePreferences(android.preference.Preference preference) {
        if (preference == null) return;
        String key = preference.getKey();
        if (disablesPreference(key)) {
            if (preference instanceof android.preference.TwoStatePreference) {
                android.preference.TwoStatePreference toggle = (android.preference.TwoStatePreference) preference;
                boolean value = checked(key, toggle.isChecked());
                if (value != toggle.isChecked()) toggle.setChecked(value);
            }
            preference.setEnabled(false);
        }
        if (preference instanceof android.preference.PreferenceGroup) {
            android.preference.PreferenceGroup group = (android.preference.PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) lockNativePreferences(group.getPreference(i));
        }
    }
}
