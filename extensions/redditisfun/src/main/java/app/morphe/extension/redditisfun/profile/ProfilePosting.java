/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Utils;

public final class ProfilePosting {
    public interface Form {
        View morpheSubmitView();
        void morpheSelectDestination(String subreddit);
    }

    private ProfilePosting() {}

    public static String currentUsername() {
        throw new IllegalStateException("Native account getter was not bound");
    }

    private static String username() {
        String name = currentUsername();
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    private static int id(String name) {
        Context context = Utils.getContext();
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    public static void bind(Form form) {
        View button = form.morpheSubmitView().findViewById(id("patcheddit_my_profile"));
        button.setOnClickListener(view -> {
            String name = username();
            if (name == null) {
                refresh(form);
                return;
            }
            form.morpheSelectDestination("u_" + name);
            InputMethodManager keyboard = (InputMethodManager) view.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
        });
        refresh(form);
    }

    public static void refresh(Form form) {
        View root = form.morpheSubmitView();
        if (root == null) return;
        View button = root.findViewById(id("patcheddit_my_profile"));
        boolean visible = username() != null;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        View picker = ((ViewGroup) button.getParent()).getChildAt(0);
        LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) picker.getLayoutParams();
        layout.width = visible ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        layout.weight = visible ? 1 : 0;
        picker.setLayoutParams(layout);
    }

    public static void prepare(Menu menu, boolean ownProfile) {
        menu.findItem(id("patcheddit_profile_submit")).setVisible(ownProfile && username() != null);
    }

    public static boolean open(Activity activity, MenuItem item, String viewedUsername) {
        if (item.getItemId() != id("patcheddit_profile_submit")) return false;
        String name = username();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && name != null && name.equalsIgnoreCase(viewedUsername)) {
            activity.startActivity(new Intent().setClassName(activity,
                    "com.andrewshu.android.reddit.submit.SubmitActivity").putExtra("subreddit", "u_" + name));
        }
        return true;
    }
}
