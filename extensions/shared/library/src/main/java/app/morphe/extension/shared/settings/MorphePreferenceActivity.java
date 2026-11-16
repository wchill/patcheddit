/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

@SuppressWarnings("deprecation")
public abstract class MorphePreferenceActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle state) {
        MorpheSettingsStyle.applyTheme(this);
        super.onCreate(state);
        ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        MorpheSettingsStyle.applyWindow(this);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) MorpheSettingsStyle.applyWindow(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
