/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

public final class MorpheSettings {
    private static final String TAG = "MorpheSettings";
    private static final String ENTRY = "morphe_settings_entry";

    private MorpheSettings() {}

    public static void addListEntry(ListView list) {
        Context context = list.getContext();
        int id = resourceId(context, "id", ENTRY);
        if (Boolean.TRUE.equals(list.getTag(id))) return;
        View entry = LayoutInflater.from(context).inflate(resourceId(context, "layout", ENTRY), list, false);
        entry.setOnClickListener(view -> open(view.getContext()));
        // Keep the clickable child accessible while excluding the footer from native item-click callbacks.
        FrameLayout footer = new FrameLayout(context);
        footer.addView(entry, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list.addFooterView(footer, null, false);
        list.setTag(id, Boolean.TRUE);
    }

    public static void bindEntry(Activity activity) {
        View entry = activity.findViewById(resourceId(activity, "id", ENTRY));
        if (entry == null) throw new IllegalStateException("The native Morphe settings entry is missing.");
        entry.setOnClickListener(view -> open(view.getContext()));
    }

    private static void open(Context context) {
        try {
            context.startActivity(new Intent(context, MorpheSettingsActivity.class));
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.e(TAG, "Could not open Morphe settings.", failure);
            Toast.makeText(context, "Could not open Morphe settings.", Toast.LENGTH_LONG).show();
        }
    }

    static int resourceId(Context context, String type, String name) {
        int id = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (id == 0) throw new IllegalStateException("Missing Morphe settings resource: " + type + "/" + name);
        return id;
    }
}
