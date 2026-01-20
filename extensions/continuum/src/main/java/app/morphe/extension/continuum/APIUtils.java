package app.morphe.extension.continuum;

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
    public static final String USER_AGENT_PREF_KEY = "morphe_user_agent_pref_key";
    public static final String REDIRECT_URI_PREF_KEY = "morphe_redirect_uri_pref_key";
    public static final String DEFAULT_PREFERENCES_FILE = "ml.docilealligator.infinityforreddit_preferences";

    private APIUtils() {
        // Prevent instantiation
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
        return "android:org.cygnusx1.continuum:" + Utils.getAppVersionName() + " (by /u/edgan)";
    }

    public static String getDefaultRedirectUri() {
        return "continuum://localhost";
    }

    public static String getDefaultClientId() {
        return "Ro2J-y8bN412oS6BeaIj0A";
    }

    public static void setupRedirectUriPreference(CustomFontPreferenceFragmentCompat fragment) {
        EditTextPreference redirectUriPref = fragment.findPreference(REDIRECT_URI_PREF_KEY);
        if (redirectUriPref != null) {
            setupPreference(redirectUriPref, APIUtils::getDefaultRedirectUri, "Tap to set redirect URI");
        }
    }

    public static void setupUserAgentPreference(CustomFontPreferenceFragmentCompat fragment) {
        EditTextPreference userAgentPref = fragment.findPreference(USER_AGENT_PREF_KEY);
        if (userAgentPref != null) {
            setupPreference(userAgentPref, APIUtils::getDefaultUserAgent, "Tap to set user agent");
        }
    }

    private static void setupPreference(EditTextPreference preference, Supplier<String> defaultValueSupplier, String hintText) {
        preference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            editText.setSingleLine(true);

            // Get current and default values
            String currentValue = preference.getText();
            String defaultValue = defaultValueSupplier.get();

            // Clear the text field only if the current value is the default value
            if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                editText.setText("");
            }
        });

        // Set a summary provider that hides the default value but shows custom ones
        preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) pref -> {
            String currentValue = pref.getText();
            String defaultValue = defaultValueSupplier.get();
            if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                // TODO: Localize this string
                return hintText;
            } else {
                return currentValue;
            }
        });
    }

    private static SharedPreferences getSharedPreferences() {
        return Utils.getContext().getSharedPreferences(DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
