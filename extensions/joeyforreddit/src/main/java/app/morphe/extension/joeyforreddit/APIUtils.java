/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.joeyforreddit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.function.Supplier;

import app.morphe.extension.shared.Utils;

/**
 * @noinspection unused
 */
@SuppressLint("NewApi")
public class APIUtils {
    public static final String CLIENT_ID_PREF_KEY = "morphe_client_id_pref_key";
    public static final String USER_AGENT_PREF_KEY = "morphe_user_agent_pref_key";
    public static final String REDIRECT_URI_PREF_KEY = "morphe_redirect_uri_pref_key";
    public static final String DEFAULT_PREFERENCES_FILE = "morphe_preferences";

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
        // TODO: Check
        return "android:o.o.joey:v" + Utils.getAppVersionName();
    }

    public static String getDefaultRedirectUri() {
        return "https://127.0.0.1:65023/authorize_callback";
    }

    public static String getDefaultClientId() {
        return "";
    }

    private static SharedPreferences getSharedPreferences() {
        return Utils.getContext().getSharedPreferences(DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
