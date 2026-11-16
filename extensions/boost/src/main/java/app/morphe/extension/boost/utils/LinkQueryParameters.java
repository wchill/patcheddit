/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.rubenmayayo.reddit.ui.customviews.menu.MenuOption;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.boost.api.LinkMenu;
import app.morphe.extension.shared.settings.PatchSettings;

public final class LinkQueryParameters {
    private static final int COPY_LINK_ID = 3;
    private static final String CHECKBOX_TAG = "morphe_strip_query_parameters";
    private static final Map<LinkMenu, QueryOption> menus = new WeakHashMap<>();

    private LinkQueryParameters() {}

    public static boolean isEnabled() {
        return false;
    }

    static boolean hasQueryParameters(String url) {
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        return query >= 0 && query + 1 < (fragment < 0 ? url.length() : fragment);
    }

    public static void addMenuOption(LinkMenu menu, List<MenuOption> options) {
        menus.remove(menu);
        if (!isEnabled() || !hasQueryParameters(menu.url)) return;
        QueryOption toggle = new QueryOption();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id == COPY_LINK_ID && !options.get(index).divider) {
                options.add(index + 1, toggle);
                menus.put(menu, toggle);
                return;
            }
        }
        throw new IllegalStateException("Boost link menu is missing Copy link");
    }

    public static String actionUrl(LinkMenu menu, String url) {
        QueryOption option = menus.get(menu);
        return isEnabled() && option != null && option.checked
                ? SettingsUtils.stripQueryParameters(url) : url;
    }

    public static boolean onClick(LinkMenu menu, MenuOption option) {
        if (option instanceof QueryOption) {
            QueryOption toggle = (QueryOption) option;
            toggle.checked = !toggle.checked;
            CheckBox view = toggle.view == null ? null : toggle.view.get();
            if (view != null) view.setChecked(toggle.checked);
            return true;
        }
        if (!SettingsUtils.isArchiveMenuEnabled()) return false;
        String prefix = SettingsUtils.getArchiveBaseUrl(option.id);
        if (prefix == null) return false;
        SettingsUtils.openArchiveLink(menu.context, menu.url, prefix);
        if (menu.dialog != null) menu.dialog.dismiss();
        return true;
    }

    public static void bindOption(View row, MenuOption option) {
        QueryCheckBox checkBox = row.findViewWithTag(CHECKBOX_TAG);
        if (!(option instanceof QueryOption)) {
            if (checkBox != null) checkBox.bind(null);
            return;
        }
        int iconId = row.getResources().getIdentifier(
                "menu_option_icon", "id", row.getContext().getPackageName());
        ImageView icon = row.findViewById(iconId);
        if (!(row instanceof RelativeLayout) || icon == null) {
            throw new IllegalStateException("Unsupported Boost link menu row");
        }
        if (checkBox == null) {
            checkBox = new QueryCheckBox(row);
            checkBox.setLayoutParams(new RelativeLayout.LayoutParams(
                    (RelativeLayout.LayoutParams) icon.getLayoutParams()));
            ((ViewGroup) row).addView(checkBox);
        }
        icon.setVisibility(View.INVISIBLE);
        checkBox.bind((QueryOption) option);
    }

    private static final class QueryOption extends MenuOption {
        boolean checked;
        WeakReference<CheckBox> view;

        QueryOption() {
            id = 108;
            title = "Strip query parameters";
            iconResource = android.R.drawable.checkbox_off_background;
            checked = PatchSettings.isEnabled("morphe_boost_strip_query_parameters_default", false);
        }
    }

    private static final class QueryCheckBox extends CheckBox {
        private QueryOption option;

        QueryCheckBox(View row) {
            super(row.getContext());
            setTag(CHECKBOX_TAG);
            setContentDescription("Strip query parameters");
            setPadding(0, 0, 0, 0);
            setMinWidth(0);
            setMinHeight(0);
        }

        void bind(QueryOption next) {
            setOnCheckedChangeListener(null);
            if (option != null) option.view = null;
            option = next;
            setVisibility(next == null ? View.GONE : View.VISIBLE);
            if (next == null) return;
            next.view = new WeakReference<>(this);
            setChecked(next.checked);
            setOnCheckedChangeListener((button, checked) -> option.checked = checked);
        }
    }
}
