/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.boost.api.LinkMenu;
import com.rubenmayayo.reddit.ui.customviews.menu.MenuOption;

public final class ImageSearchMenu {
    private static final int SUBMENU_ID = 5, FIRST_ENGINE_ID = 201;
    // Add or reorder engines here; menu IDs and click handling follow this list.
    static final Engine[] ENGINES = {
            new Engine("Google", "https://lens.google.com/uploadbyurl?url=[url]"),
            new Engine("Bing", "https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:[url]"),
            new Engine("Yandex", "https://yandex.com/images/search?rpt=imageview&url=[url]"),
            new Engine("TinEye", "https://www.tineye.com/search?url=[url]"),
    };

    private ImageSearchMenu() {}

    public static boolean isEnabled() { return false; }

    static final class Engine {
        final String title, template;

        Engine(String title, String template) {
            if (!template.contains("[url]")) throw new IllegalArgumentException("Image search template needs [url]");
            this.title = title;
            this.template = template;
        }

        String url(String image) {
            try {
                return template.replace("[url]", URLEncoder.encode(image, "UTF-8"));
            } catch (UnsupportedEncodingException impossible) {
                throw new AssertionError("UTF-8 is unavailable", impossible);
            }
        }
    }

    public static void replaceOptions(List<MenuOption> options) {
        if (!isEnabled()) return;
        for (MenuOption submenu : options) {
            if (submenu.id != SUBMENU_ID) continue;
            if (submenu.children == null) throw new IllegalStateException("Boost image search submenu is missing engines");
            ArrayList<MenuOption> engines = new ArrayList<>(ENGINES.length);
            for (int index = 0; index < ENGINES.length; index++) {
                MenuOption option = new MenuOption();
                option.id = FIRST_ENGINE_ID + index;
                option.title = ENGINES[index].title;
                option.iconResource = submenu.iconResource;
                engines.add(option);
            }
            // Keep Boost's localized submenu title, icon, and image-only visibility.
            submenu.children = engines;
            return;
        }
    }

    public static boolean onClick(LinkMenu menu, MenuOption option) {
        int index = option.id - FIRST_ENGINE_ID;
        if (!isEnabled() || index < 0 || index >= ENGINES.length) return false;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ENGINES[index].url(menu.url)));
        if (!(menu.context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.context.startActivity(intent);
        if (menu.dialog != null) menu.dialog.dismiss();
        return true;
    }
}
