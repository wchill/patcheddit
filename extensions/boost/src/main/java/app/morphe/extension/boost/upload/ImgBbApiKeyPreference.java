/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.upload;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import android.preference.EditTextPreference;

public final class ImgBbApiKeyPreference extends EditTextPreference {
    public ImgBbApiKeyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(String value) {
        super.setText(value == null ? null : value.trim());
    }

    @Override
    public CharSequence getSummary() {
        return TextUtils.isEmpty(getText())
                ? "Required when using ImgBB. Leave empty to clear the saved key."
                : "Configured";
    }
}
