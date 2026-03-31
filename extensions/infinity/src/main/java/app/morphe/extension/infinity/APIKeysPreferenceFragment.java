/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Based off of https://github.com/cygnusx-1-org/continuum/blob/39e886dfa4d97d02c6a61e0e509dd4ba9a408dc5/app/src/main/java/ml/docilealligator/infinityforreddit/settings/APIKeysPreferenceFragment.java
 */

package app.morphe.extension.infinity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;

public class APIKeysPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    private static final String TAG = "APIKeysPrefFragment";
    private static final int CLIENT_ID_LENGTH = 22;

    SharedPreferences mSharedPreferences;

    public APIKeysPreferenceFragment() {}

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mSharedPreferences = Utils.getContext().getSharedPreferences(APIUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);

        //setSharedPreferencesName(APIUtils.DEFAULT_PREFERENCES_FILE);
        setPreferencesFromResourceWrapper(ResourceUtils.getXmlIdentifier("api_keys_preferences"), rootKey);

        setupClientIdPreference();
        setupRedirectUriPreference();
        setupUserAgentPreference();
    }

    /**
     * Restarts the application by clearing the task stack and launching the main activity.
     * Also kills the current process to ensure a clean restart.
     * Shows a toast message as a fallback if restarting via Intent fails.
     *
     * @param context Context used to get application context, package manager, and show toasts.
     */
    private static void triggerAppRestart(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            // Use application context for getPackageManager and getPackageName
            Intent intent = appContext.getPackageManager().getLaunchIntentForPackage(appContext.getPackageName());
            if (intent != null) {
                // Clear the activity stack and start the launch activity as a new task.
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appContext.startActivity(intent);
                Log.i(TAG, "Triggering app restart via Intent.");

                // Request the current process be killed. System will eventually restart it.
                // This ensures that services and other components are also restarted.
                android.os.Process.killProcess(android.os.Process.myPid());
                // System.exit(0); // Alternative to killProcess, might be slightly cleaner in some cases
            } else {
                Log.e(TAG, "Could not get launch intent for package to trigger restart.");
                // Use application context for Toast as well
                Toast.makeText(appContext, "Please restart the app manually.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error triggering app restart", e);
            // Use application context for Toast
            Toast.makeText(context.getApplicationContext(), "Please restart the app manually.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupClientIdPreference() {
        EditTextPreference clientIdPref = findPreferenceWrapper(APIUtils.CLIENT_ID_PREF_KEY);
        if (clientIdPref != null) {
            setupPreference(clientIdPref, "Tap to set client ID");
        }
    }

    private void setupRedirectUriPreference() {
        EditTextPreference redirectUriPref = findPreferenceWrapper(APIUtils.REDIRECT_URI_PREF_KEY);
        if (redirectUriPref != null) {
            setupPreference(redirectUriPref, "Tap to set redirect URI");
        }
    }

    private void setupUserAgentPreference() {
        EditTextPreference userAgentPref = findPreferenceWrapper(APIUtils.USER_AGENT_PREF_KEY);
        if (userAgentPref != null) {
            setupPreference(userAgentPref, "Tap to set user agent");
        }
    }

    private void setupPreference(EditTextPreference preference, String hintText) {
        preference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            editText.setSingleLine(true);

            // Get current and default values
            String currentValue = preference.getText();

            if (currentValue == null || currentValue.isEmpty()) {
                editText.setText("");
            }
        });

        // Set a summary provider that hides the default value but shows custom ones
        preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) pref -> {
            String currentValue = pref.getText();
            if (currentValue == null || currentValue.isEmpty()) {
                // TODO: Localize this string
                return hintText;
            } else {
                return currentValue;
            }
        });

        preference.setOnPreferenceChangeListener(((pref, newValue) -> {
            String value = (String) newValue;

            // Final validation check (redundant due to button state, but safe)
            if (value == null || value.length() != CLIENT_ID_LENGTH) {
                return false; // Should not happen if button logic is correct
            }

            // Manually save the preference value *before* restarting
            // Get the specific SharedPreferences instance used by the PreferenceManager
            SharedPreferences prefs = Utils.getContext().getSharedPreferences(APIUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(APIUtils.CLIENT_ID_PREF_KEY, value);
            boolean success = editor.commit(); // Use commit() for synchronous saving

            if (success) {
                Log.i(TAG, "Client ID manually saved successfully.");
                // Update the summary provider manually since we return false
                preference.setSummaryProvider(pref.getSummaryProvider()); // Re-set to trigger update
                triggerAppRestart(requireContext()); // Use the helper
            } else {
                Log.e(TAG, "Failed to save Client ID manually.");
                Toast.makeText(getContext(), "Error saving Client ID.", Toast.LENGTH_SHORT).show();
                // Don't restart if save failed
            }

            // Return false because we handled the saving manually (or attempted to)
            return false;
        }));

        // Add dialog click listener to reset the current EditText reference when dialog is canceled
        preference.setOnPreferenceClickListener(pref -> {
            // This will be called before the dialog appears
            // We'll set the EditText reference in setOnBindEditTextListener
            return false; // Return false to allow normal processing
        });
    }

    /*
    private void setSharedPreferencesName(@SuppressWarnings("SameParameterValue") String name) {
        PreferenceManager preferenceManager = getPreferenceManager();
        // Use default shared preferences file for client ID
        preferenceManager.setSharedPreferencesName(name);
    }

     */

    private void setPreferencesFromResourceWrapper(int preferencesResId, String rootKey) {
        setPreferencesFromResource(preferencesResId, rootKey);
    }

    private <T extends Preference> T findPreferenceWrapper(String key) {
        return findPreference(key);
    }
}
