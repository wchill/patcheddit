/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.infinity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import java.util.function.Supplier;

import app.morphe.extension.shared.Utils;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;

/**
 * @noinspection unused
 */
@SuppressLint("NewApi")
public class APIUtils {
    public static final String CLIENT_ID_PREF_KEY = "morphe_client_id_pref_key";
    public static final String USER_AGENT_PREF_KEY = "morphe_user_agent_pref_key";
    public static final String REDIRECT_URI_PREF_KEY = "morphe_redirect_uri_pref_key";
    public static final String DEFAULT_PREFERENCES_FILE = "ml.docilealligator.infinityforreddit_preferences";

    private APIUtils() {
        // Prevent instantiation
    }

    public static String getClientId() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getString(CLIENT_ID_PREF_KEY, getDefaultClientId());
    }
    public static String getUserAgent() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getString(USER_AGENT_PREF_KEY, getDefaultUserAgent());
    }

    public static String getRedirectUri() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getString(REDIRECT_URI_PREF_KEY, getDefaultRedirectUri());
    }

    public static String getDefaultUserAgent() {
        return "android:ml.docilealligator.infinityforreddit:" + Utils.getAppVersionName() + " (by /u/Hostilenemy)";
    }

    public static String getDefaultRedirectUri() {
        return "infinity://localhost";
    }

    public static String getDefaultClientId() {
        return "";
    }

    private static SharedPreferences getSharedPreferences() {
        return Utils.getContext().getSharedPreferences(DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
