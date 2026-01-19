package app.morphe.extension.slide;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.google.android.material.textfield.TextInputLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * @noinspection unused
 */
@SuppressLint("NewApi")
public class APIUtils {
    public static final String CLIENT_ID_PREF_KEY = "redditClientOverride";
    public static final String USER_AGENT_PREF_KEY = "morphe_user_agent_pref_key";
    public static final String REDIRECT_URI_PREF_KEY = "morphe_redirect_uri_pref_key";
    public static final String DEFAULT_PREFERENCES_FILE = "SETTINGS";

    private static EditText redirectUriInput;
    private static EditText userAgentInput;

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
        return Utils.getContext().getPackageName() + "/" + Utils.getAppVersionName();
    }

    public static String getDefaultRedirectUri() {
        return "http://www.ccrama.me";
    }

    public static String getDefaultClientId() {
        return "KI2Nl9A_ouG9Qw";
    }

    // FIXME: This method adds the EditText fields in reverse order in settings fragment for some reason.
    public static void modifyDialog(LinearLayout dialogContainer) {
        // At this point, the input row for the client ID should have already been added.
        SharedPreferences prefs = getSharedPreferences();
        String savedRedirectUri = prefs.getString(REDIRECT_URI_PREF_KEY, getDefaultRedirectUri());
        String savedUserAgent = prefs.getString(USER_AGENT_PREF_KEY, getDefaultUserAgent());

        Context context = dialogContainer.getContext();

        redirectUriInput = createEditText(context, savedRedirectUri, "Enter redirect URI");
        userAgentInput = createEditText(context, savedUserAgent, "Enter user agent");

        dialogContainer.addView(redirectUriInput);
        dialogContainer.addView(userAgentInput);
    }

    public static void persistSettings() {
        SharedPreferences.Editor editor = getSharedPreferences().edit();

        if (redirectUriInput != null && userAgentInput != null) {
            String redirectUriText = redirectUriInput.getText().toString().trim();
            if (redirectUriText.isEmpty()) {
                editor.remove(REDIRECT_URI_PREF_KEY);
            } else {
                editor.putString(REDIRECT_URI_PREF_KEY, redirectUriText);
            }

            String userAgentText = userAgentInput.getText().toString().trim();
            if (userAgentText.isEmpty()) {
                editor.remove(USER_AGENT_PREF_KEY);
            } else {
                editor.putString(USER_AGENT_PREF_KEY, userAgentText);
            }
            editor.commit();
        } else {
            Logger.printException(() -> "APIUtils.persistSettings called before inputs were initialized!");
        }

        redirectUriInput = null;
        userAgentInput = null;
    }

    private static EditText createEditText(Context contextThemeWrapper, String savedValue, String hintText) {
        // Declare EditText here and make it final
        final EditText input = new EditText(contextThemeWrapper);
        input.setText(savedValue);
        input.setHint(hintText);
        input.setSingleLine(true);  // Make input single line
        return input;
    }

    private static SharedPreferences getSharedPreferences() {
        return Utils.getContext().getSharedPreferences(DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
