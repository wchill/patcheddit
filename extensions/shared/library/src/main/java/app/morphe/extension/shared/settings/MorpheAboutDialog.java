/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import app.morphe.extension.shared.Utils;

final class MorpheAboutDialog {
    private enum Page { ABOUT, CREDITS, LICENSES, NOTICE }

    private static final String TAG = "PatchedditAbout";
    private static final String REPOSITORY = "https://github.com/wchill/patcheddit";
    private static final int BLUE = 0xFF1E5AA8;
    private static final int TEAL = 0xFF00AFAE;

    private final Activity activity;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String installed = version();
    private final int foreground;
    private final int background;
    private final int accent;
    private TextView statusTitle;
    private TextView statusBody;
    private LinearLayout statusCard;
    private Future<?> request;
    private boolean closed;
    private AlertDialog dialog;
    private FrameLayout pages;
    private Page page = Page.ABOUT;
    private int generation;
    private String latestVersion;
    private List<PatchedditCredits.Contributor> contributors;
    private List<PatchedditCredits.Notice> notices;
    private PatchedditCredits.Notice selectedNotice;

    private MorpheAboutDialog(Activity activity, ExecutorService worker) {
        this.activity = activity;
        this.worker = worker;
        TypedArray colors = activity.obtainStyledAttributes(new int[]{
                android.R.attr.textColorPrimary, android.R.attr.colorBackgroundFloating});
        try {
            foreground = colors.getColor(0, Color.BLACK);
            background = colors.getColor(1, Color.WHITE);
        } finally {
            colors.recycle();
        }
        accent = Color.red(background) + Color.green(background) + Color.blue(background) < 384 ? TEAL : BLUE;
    }

    static String version() {
        String version = Utils.getPatchesReleaseVersion();
        return version == null || version.isEmpty() ? "Unknown" : version;
    }

    static AlertDialog show(Activity activity, ExecutorService worker) {
        MorpheAboutDialog content = new MorpheAboutDialog(activity, worker);
        AlertDialog dialog = new AlertDialog(activity) {
            @Override
            public void onBackPressed() {
                if (content.page == Page.ABOUT) super.onBackPressed();
                else content.back();
            }
        };
        content.dialog = dialog;
        content.pages = new FrameLayout(activity);
        dialog.setView(content.pages, 0, 0, 0, 0);
        dialog.setOnDismissListener(ignored -> {
            content.closed = true;
            if (content.request != null) content.request.cancel(true);
            content.main.removeCallbacksAndMessages(null);
        });
        dialog.show();
        content.navigate(Page.ABOUT);
        return dialog;
    }

    private void navigate(Page target) {
        generation++;
        if (request != null) request.cancel(true);
        main.removeCallbacksAndMessages(null);
        page = target;
        pages.removeAllViews();
        switch (target) {
            case ABOUT:
                pages.addView(layout());
                checkForUpdates();
                break;
            case CREDITS:
                pages.addView(credits());
                break;
            case LICENSES:
                pages.addView(licenses());
                break;
            case NOTICE:
                LinearLayout content = pageContent(selectedNotice.name);
                TextView text = text(selectedNotice.text, 13, foreground, false);
                text.setGravity(Gravity.START);
                text.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(activity);
                scroll.addView(text);
                content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
                pages.addView(content, new FrameLayout.LayoutParams(-1, -1));
                break;
        }
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(dp(560),
                    Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.9f));
            window.setLayout(target == Page.NOTICE ? ViewGroup.LayoutParams.MATCH_PARENT : width,
                    target == Page.NOTICE ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void back() {
        navigate(page == Page.NOTICE ? Page.LICENSES : page == Page.LICENSES ? Page.CREDITS : Page.ABOUT);
    }

    private LinearLayout pageContent(String title) {
        LinearLayout content = column();
        content.setPadding(dp(26), dp(24), dp(26), dp(26));
        content.setBackground(shape(background, 28, false));
        content.setClipToOutline(true);
        TextView back = text(string("morphe_about_back"), 14, accent, true);
        back.setGravity(Gravity.START);
        back.setPadding(0, dp(8), 0, dp(16));
        back.setOnClickListener(ignored -> back());
        content.addView(back);
        TextView heading = text(title, 18, foreground, true);
        heading.setPadding(0, 0, 0, dp(20));
        content.addView(heading);
        return content;
    }

    private View layout() {
        LinearLayout content = column();
        content.setPadding(dp(26), dp(34), dp(26), dp(26));
        content.setBackground(shape(background, 28, false));
        content.setClipToOutline(true);

        FrameLayout logo = new FrameLayout(activity);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{BLUE, TEAL});
        gradient.setCornerRadius(dp(24));
        logo.setBackground(gradient);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(MorpheSettings.resourceId(activity, "drawable", "morphe_settings_icon"));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(BLUE));
        icon.setBackground(shape(0xFFEEEEEE, 18, false));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER);
        logo.addView(icon, imageParams);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(logo, logoParams);

        TextView name = text("Patcheddit", 22, foreground, true);
        name.setPadding(0, dp(14), 0, 0);
        content.addView(name);
        content.addView(appVersion());

        statusCard = card();
        statusTitle = text("", 13, accent, true);
        statusBody = text("", 12, muted(), false);
        statusBody.setPadding(0, dp(3), 0, 0);
        statusCard.addView(statusTitle);
        statusCard.addView(statusBody);
        addCard(content, statusCard);
        if (isPreRelease()) {
            LinearLayout development = card();
            development.addView(text(string("morphe_about_prerelease_title"), 13, accent, true));
            TextView note = text(string("morphe_about_prerelease_summary"), 12, muted(), false);
            note.setPadding(0, dp(3), 0, 0);
            development.addView(note);
            addCard(content, development);
        }

        TextView linksTitle = text(string("morphe_about_links"), 15, foreground, true);
        linksTitle.setPadding(0, dp(20), 0, dp(12));
        content.addView(linksTitle);
        LinearLayout links = column();
        links.setBackground(shape(0x1A808080, 18, true));
        links.setClipToOutline(true);
        link(links, "morphe_about_github", "morphe_about_github_summary",
                "code", REPOSITORY);
        link(links, "morphe_about_releases", "morphe_about_releases_summary",
                "download", REPOSITORY + "/releases");
        link(links, "morphe_about_issues", "morphe_about_issues_summary",
                "error", REPOSITORY + "/issues");
        link(links, "morphe_about_reddit", "morphe_about_reddit_summary",
                "forum", "https://www.reddit.com/r/Patcheddit/");
        row(links, string("morphe_about_credits"), string("morphe_about_credits_summary"),
                "group", () -> navigate(Page.CREDITS));
        content.addView(links);
        return scroll(content);
    }

    private View scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }

    private View credits() {
        LinearLayout content = pageContent(string("morphe_about_credits"));
        LinearLayout entries = group();
        content.addView(entries);
        TextView state = text(string("morphe_credits_loading"), 13, muted(), false);
        state.setPadding(dp(14), dp(14), dp(14), dp(14));
        entries.addView(state);
        LinearLayout links = group();
        link(links, "morphe_credits_github", "morphe_credits_github_summary",
                "group", PatchedditCredits.CONTRIBUTORS);
        row(links, string("morphe_about_licenses"), string("morphe_about_licenses_summary"),
                "description", () -> navigate(Page.LICENSES));
        addCard(content, links);
        if (contributors != null) {
            displayContributors(entries);
        } else {
            int current = generation;
            request = worker.submit(() -> {
                try {
                    List<PatchedditCredits.Contributor> result = PatchedditCredits.contributors();
                    main.post(() -> {
                        if (!active(current)) return;
                        contributors = result;
                        displayContributors(entries);
                    });
                } catch (IOException | SecurityException failure) {
                    Log.w(TAG, "Could not load GitHub contributors.", failure);
                    main.post(() -> {
                        if (!active(current)) return;
                        state.setText(string("morphe_credits_failed"));
                        state.setOnClickListener(ignored -> navigate(Page.CREDITS));
                    });
                }
            });
        }
        return scroll(content);
    }

    private void displayContributors(LinearLayout entries) {
        entries.removeAllViews();
        if (contributors.isEmpty()) {
            entries.addView(text(string("morphe_credits_empty"), 13, muted(), false));
        }
        for (PatchedditCredits.Contributor contributor : contributors) {
            row(entries, contributor.login, string("morphe_credits_count", contributor.contributions),
                    "group", () -> open(contributor.profileUrl()));
        }
    }

    private View licenses() {
        LinearLayout content = pageContent(string("morphe_about_licenses"));
        TextView description = text(string("morphe_about_licenses_summary"), 13, muted(), false);
        description.setPadding(0, 0, 0, dp(14));
        content.addView(description);
        LinearLayout entries = group();
        content.addView(entries);
        TextView state = text(string("morphe_licenses_loading"), 13, muted(), false);
        entries.addView(state);
        if (notices != null) {
            displayNotices(entries);
        } else {
            int current = generation;
            request = worker.submit(() -> {
                try {
                    List<PatchedditCredits.Notice> result = PatchedditCredits.notices(
                            activity.getResources().openRawResource(
                                    MorpheSettings.resourceId(activity, "raw", "patcheddit_licenses")));
                    main.post(() -> {
                        if (!active(current)) return;
                        notices = result;
                        displayNotices(entries);
                    });
                } catch (IOException | android.content.res.Resources.NotFoundException | IllegalStateException failure) {
                    Log.e(TAG, "Could not read bundled license notices.", failure);
                    main.post(() -> {
                        if (!active(current)) return;
                        state.setText(string("morphe_licenses_failed"));
                        state.setOnClickListener(ignored -> navigate(Page.LICENSES));
                    });
                }
            });
        }
        return scroll(content);
    }

    private void displayNotices(LinearLayout entries) {
        entries.removeAllViews();
        for (PatchedditCredits.Notice notice : notices) {
            row(entries, notice.name, null, "description", () -> {
                selectedNotice = notice;
                navigate(Page.NOTICE);
            });
        }
    }

    private void checkForUpdates() {
        statusCard.setOnClickListener(null);
        statusTitle.setText(string("morphe_about_checking"));
        statusBody.setText(string("morphe_about_installed", installed));
        if (latestVersion != null) {
            displayVersion();
            return;
        }
        int current = generation;
        request = worker.submit(() -> {
            String latest;
            try {
                latest = PatchedditRelease.latest(installed);
                PatchedditRelease.compare(latest, installed);
                main.post(() -> {
                    if (!active(current)) return;
                    latestVersion = latest;
                    displayVersion();
                });
            } catch (IOException | IllegalArgumentException | SecurityException failure) {
                Log.w(TAG, "Could not check the latest Patcheddit release.", failure);
                main.post(() -> {
                    if (!active(current)) return;
                    statusTitle.setText(string("morphe_about_check_failed"));
                    statusBody.setText(string("morphe_about_check_retry", installed));
                    statusCard.setOnClickListener(ignored -> checkForUpdates());
                });
            }
        });
    }

    private void displayVersion() {
        boolean update = PatchedditRelease.compare(latestVersion, installed) > 0;
        statusTitle.setText(string(update ? "morphe_about_update" : "morphe_about_current"));
        statusBody.setText(string("morphe_about_versions", installed, latestVersion));
        if (update) statusCard.setOnClickListener(ignored -> open(REPOSITORY + "/releases"));
    }

    private boolean active(int current) {
        return current == generation && !closed && !activity.isFinishing() && !activity.isDestroyed();
    }

    private boolean isPreRelease() {
        try {
            return PatchedditRelease.isPreRelease(installed);
        } catch (IllegalArgumentException failure) {
            Log.w(TAG, "Unrecognized installed Patcheddit version.", failure);
            return false;
        }
    }

    private void link(LinearLayout group, String title, String subtitle, String iconName, String url) {
        row(group, string(title), string(subtitle), iconName, () -> open(url));
    }

    private void row(LinearLayout group, String title, String subtitle, String iconName, Runnable action) {
        if (group.getChildCount() != 0) {
            View separator = new View(activity);
            separator.setBackgroundColor(0x33808080);
            LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(1));
            divider.setMarginStart(dp(16));
            divider.setMarginEnd(dp(16));
            group.addView(separator, divider);
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(new RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33808080), null,
                new ColorDrawable(Color.WHITE)));
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(MorpheSettingsStyle.icon(activity, iconName));
        icon.setPadding(0, 0, 0, 0);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(12), 0);
        TextView name = text(title, 15, foreground, true);
        name.setGravity(Gravity.START);
        labels.addView(name);
        if (subtitle != null) {
            TextView description = text(subtitle, 12, muted(), false);
            description.setGravity(Gravity.START);
            labels.addView(description);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(MorpheSettingsStyle.icon(activity, "chevron"));
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        chevron.setAlpha(0.55f);
        chevron.setPadding(0, 0, 0, 0);
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setOnClickListener(ignored -> action.run());
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(shape(0x1A808080, 14, true));
        return card;
    }

    private LinearLayout group() {
        LinearLayout group = column();
        group.setBackground(shape(0x1A808080, 18, true));
        group.setClipToOutline(true);
        return group;
    }

    private void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        parent.addView(card, params);
    }

    private GradientDrawable shape(int color, int radius, boolean stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (stroke) shape.setStroke(dp(1), 0x29808080);
        return shape;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView appVersion() {
        String label;
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            label = string("morphe_about_app", activity.getApplicationInfo()
                    .loadLabel(activity.getPackageManager()), info.versionName);
        } catch (PackageManager.NameNotFoundException failure) {
            Log.w(TAG, "Could not read the patched app version.", failure);
            label = string("morphe_about_app_unknown");
        }
        TextView app = text(label, 12, muted(), false);
        app.setPadding(0, dp(4), 0, 0);
        return app;
    }

    private void open(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException failure) {
            Log.w(TAG, "Could not open Patcheddit link.", failure);
            Toast.makeText(activity, string("morphe_about_open_failed", url), Toast.LENGTH_LONG).show();
        }
    }

    private String string(String name, Object... args) {
        return activity.getString(MorpheSettings.resourceId(activity, "string", name), args);
    }

    private int muted() {
        return (foreground & 0x00FFFFFF) | 0xB3000000;
    }

    private int dp(int value) {
        return dp(activity, value);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
