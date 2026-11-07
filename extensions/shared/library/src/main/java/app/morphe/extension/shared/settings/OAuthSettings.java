/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Utils;

public final class OAuthSettings {
    public static final String ENABLED = "morphe_patch_spoof_client";
    public static final String CLIENT_ID = "morphe_client_id_pref_key";
    public static final String REDIRECT_URI = "morphe_redirect_uri_pref_key";
    public static final String USER_AGENT = "morphe_user_agent_pref_key";
    private static final String BOOST_CLIENT_ID = "pref_dev_reddit_client_id";
    private static final String BOOST_REDIRECT_URI = "pref_dev_reddit_redirect_url";
    private static final String BOOST_USER_AGENT = "pref_dev_reddit_user_agent";
    private static boolean migrated;

    private OAuthSettings() {}

    // Replaced with the selected patch options.
    public static String getDefaultClientId() { return ""; }
    public static String getDefaultRedirectUri() { return ""; }
    public static String getDefaultUserAgent() { return ""; }

    public static String getClientId(String original) {
        return enabled() ? getConfiguredClientId() : original;
    }

    public static String getRedirectUri(String original) {
        return enabled() ? getConfiguredRedirectUri() : original;
    }

    public static String getUserAgent(String original) {
        return enabled() ? getConfiguredUserAgent() : original;
    }

    public static String getConfiguredClientId() {
        return configuredValue(CLIENT_ID, getDefaultClientId());
    }

    public static String getConfiguredRedirectUri() {
        return configuredValue(REDIRECT_URI, getDefaultRedirectUri());
    }

    public static String getConfiguredUserAgent() {
        return configuredValue(USER_AGENT, getDefaultUserAgent());
    }

    public static String getClientIdParameter(String original) {
        return enabled() ? "client_id=" + getConfiguredClientId() : original;
    }

    public static String getRedirectUriParameter(String original) {
        return enabled() ? "redirect_uri=" + getConfiguredRedirectUri() : original;
    }

    public static String getBasicAuthClientId(String original) {
        return enabled() ? getConfiguredClientId() + ":" : original;
    }

    public static String getBasicAuthorization(String original) {
        return enabled() ? getConfiguredBasicAuthorization() : original;
    }

    public static String getConfiguredBasicAuthorization() {
        return "Basic " + Base64.encodeToString(
                (getConfiguredClientId() + ":").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String getAuthorizationUrl(String original) {
        return enabled() ? replaceClientId(original, getConfiguredClientId()) : original;
    }

    public static String getImgurImageApi(String original) {
        return enabled() ? "https://api.imgur.com/3/image" : original;
    }

    public static String replaceClientId(String authorizationUrl, String clientId) {
        Matcher matcher = Pattern.compile("(?<=client_id=)[^&]*").matcher(authorizationUrl);
        if (!matcher.find()) throw new IllegalArgumentException("The authorization URL has no client_id parameter.");
        return matcher.replaceAll(Matcher.quoteReplacement(clientId));
    }

    private static boolean enabled() {
        return PatchSettings.isEnabled(ENABLED, true);
    }

    private static String configuredValue(String key, String fallback) {
        if (!Utils.isContextSet()) return fallback;
        Context context = Utils.getContext();
        if (REDIRECT_URI.equals(key) && context.getPackageName().startsWith("ml.docilealligator.infinityforreddit")) {
            return fallback;
        }
        migrateLegacyPreferences(context);
        String saved = PatchSettings.preferences(context).getString(key, "");
        return saved == null || saved.trim().isEmpty() ? fallback : saved.trim();
    }

    public static String[] preferenceKeys(Context context) {
        return "com.rubenmayayo.reddit".equals(context.getPackageName())
                ? new String[] { BOOST_CLIENT_ID, BOOST_REDIRECT_URI, BOOST_USER_AGENT }
                : new String[] { CLIENT_ID, REDIRECT_URI, USER_AGENT };
    }

    public static String defaultValue(String key) {
        switch (key) {
            case CLIENT_ID:
            case BOOST_CLIENT_ID: return getDefaultClientId();
            case REDIRECT_URI:
            case BOOST_REDIRECT_URI: return getDefaultRedirectUri();
            case USER_AGENT:
            case BOOST_USER_AGENT: return getDefaultUserAgent();
            default: throw new IllegalArgumentException("Unknown OAuth preference: " + key);
        }
    }

    public static String summary(String key, String value) {
        return value == null || value.trim().isEmpty()
                ? "Using patch default: " + defaultValue(key) + "\nLeave empty to keep this default."
                : "Override: " + value.trim();
    }

    public static String validationError(String key, String value) {
        defaultValue(key);
        String trimmed = value.trim();
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return "OAuth values must not contain line breaks.";
        }
        if (trimmed.isEmpty()) return null;
        switch (key) {
            case CLIENT_ID:
            case BOOST_CLIENT_ID:
                return value.matches("[A-Za-z0-9_-]{22}") ? null
                        : "The client ID must contain exactly 22 letters, digits, underscores or hyphens.";
            case REDIRECT_URI:
            case BOOST_REDIRECT_URI:
                try {
                    URI uri = new URI(value);
                    return uri.getScheme() != null
                            && (uri.getRawAuthority() == null || !uri.getRawAuthority().contains("@")) ? null
                            : "The redirect URI must include a scheme and must not contain user information.";
                } catch (URISyntaxException invalid) {
                    return "Enter a valid redirect URI.";
                }
            case USER_AGENT:
            case BOOST_USER_AGENT:
                for (int index = 0; index < value.length(); index++) {
                    char character = value.charAt(index);
                    if (character != '\t' && (character < ' ' || character > '~')) {
                        return "The user agent must contain printable ASCII characters.";
                    }
                }
                return null;
            default: throw new IllegalArgumentException("Unknown OAuth preference: " + key);
        }
    }

    public static synchronized void migrateLegacyPreferences(Context context) {
        if (migrated) return;
        String packageName = context.getPackageName();
        String legacyName;
        if (packageName.startsWith("ml.docilealligator.infinityforreddit")) {
            legacyName = "ml.docilealligator.infinityforreddit_preferences";
        } else if (packageName.startsWith("o.o.joey")) {
            legacyName = "morphe_preferences";
        } else {
            migrated = true;
            return;
        }
        SharedPreferences current = PatchSettings.preferences(context);
        SharedPreferences legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = current.edit();
        for (String key : new String[] { CLIENT_ID, REDIRECT_URI, USER_AGENT }) {
            if (!current.contains(key) && legacy.contains(key)) {
                editor.putString(key, legacy.getString(key, ""));
            }
        }
        editor.apply();
        migrated = true;
    }
}
