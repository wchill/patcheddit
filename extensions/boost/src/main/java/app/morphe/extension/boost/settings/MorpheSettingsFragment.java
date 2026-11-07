/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.settings;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import app.morphe.extension.shared.settings.OAuthSettings;

public class MorpheSettingsFragment extends PreferenceFragmentCompat {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_TOP_LEVEL_SETTINGS_ISSUE106_V1";

    private static final String RESOURCE_NAME = "morphe_boost_settings_skeleton";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";

    protected String resourceName() {
        return RESOURCE_NAME;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(resourceId("xml", resourceName()), rootKey);
        bindPatchPreferences(getPreferenceScreen());
        bindOAuthPreferences();
    }

    private void bindPatchPreferences(PreferenceGroup group) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference entry = group.getPreference(index);
            if (entry instanceof PreferenceGroup) bindPatchPreferences((PreferenceGroup) entry);
            String key = entry.getKey();
            if (entry instanceof CheckBoxPreference && key != null && (key.startsWith("morphe_patch_")
                    || key.equals("morphe_boost_prefer_high_refresh_rate")
                    || key.equals("morphe_boost_reddit_undelete_enabled")
                    || key.equals("morphe_boost_imgur_undelete_enabled"))) {
                entry.setOnPreferenceChangeListener((preference, value) -> {
                    Toast.makeText(requireContext(), "Restart the app to apply the patch change.",
                            Toast.LENGTH_LONG).show();
                    return true;
                });
            }
        }
    }

    private void bindOAuthPreferences() {
        String[] keys = OAuthSettings.preferenceKeys(requireContext());
        if (findPreference(keys[0]) == null) return;
        for (String key : keys) {
            Preference entry = findPreference(key);
            if (!(entry instanceof EditTextPreference)) {
                throw new IllegalStateException("Missing Morphe OAuth editor: " + key);
            }
            EditTextPreference editor = (EditTextPreference) entry;
            editor.setSummary(OAuthSettings.summary(key, editor.getText()));
            editor.setOnPreferenceChangeListener((preference, value) -> {
                String text = (String) value;
                String error = OAuthSettings.validationError(key, text);
                if (error != null) {
                    Log.w("MorpheSettings", "Rejected an invalid OAuth setting.");
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    return false;
                }
                preference.setSummary(OAuthSettings.summary(key, text));
                Toast.makeText(requireContext(), "Restart the app and sign in again after changing OAuth values.",
                        Toast.LENGTH_LONG).show();
                return true;
            });
        }
    }

    protected final int resourceId(String type, String name) {
        Context context = requireContext();
        Resources resources = context.getResources();

        int resourceId = resources.getIdentifier(
                name,
                type,
                context.getPackageName()
        );

        if (resourceId == 0) {
            resourceId = resources.getIdentifier(
                    name,
                    type,
                    BOOST_PACKAGE
            );
        }

        if (resourceId == 0) {
            throw new IllegalStateException(
                    "Missing Morphe settings resource: " + type + "/" + name
            );
        }

        return resourceId;
    }

}
