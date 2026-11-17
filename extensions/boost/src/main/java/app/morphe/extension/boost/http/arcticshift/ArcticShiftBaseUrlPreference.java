/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.http.arcticshift;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public final class ArcticShiftBaseUrlPreference extends EditTextPreference {
    public ArcticShiftBaseUrlPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean callChangeListener(Object value) {
        try {
            ArcticShiftSettings.normalizeBaseUrl((String) value);
        } catch (IllegalArgumentException invalid) {
            Toast.makeText(getContext(), invalid.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        return super.callChangeListener(value);
    }

    @Override
    public void setText(String value) {
        super.setText(value == null ? "" : value.trim());
    }

    @Override
    public CharSequence getSummary() {
        return ArcticShiftSettings.normalizeBaseUrl(getText())
                + "\nLeave empty to use the default. Restart Boost after changing the URL.";
    }
}
