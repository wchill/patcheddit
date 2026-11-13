/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.upload;

import android.content.Context;
import android.util.AttributeSet;

import android.preference.ListPreference;

public final class EditorImageHostPreference extends ListPreference {
    public EditorImageHostPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEntries(new CharSequence[]{"Imgur Free", "ImgBB"});
        setEntryValues(new CharSequence[]{
                ExternalImageUploadSettings.PROVIDER_IMGUR,
                ExternalImageUploadSettings.PROVIDER_IMGBB
        });
        setDefaultValue(ExternalImageUploadSettings.PROVIDER_IMGUR);
    }

    @Override
    public void setValue(String value) {
        super.setValue(ExternalImageUploadSettings.normalizeProvider(value));
        notifyDependencyChange(shouldDisableDependents());
    }

    @Override
    public CharSequence getSummary() {
        return ExternalImageUploadSettings.PROVIDER_IMGBB.equals(getValue())
                ? "ImgBB - personal API key required"
                : "Imgur Free - default";
    }

    @Override
    public boolean shouldDisableDependents() {
        return !ExternalImageUploadSettings.PROVIDER_IMGBB.equals(getValue())
                || super.shouldDisableDependents();
    }
}
