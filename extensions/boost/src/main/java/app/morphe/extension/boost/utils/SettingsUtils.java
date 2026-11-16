/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boost.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.rubenmayayo.reddit.ui.customviews.menu.MenuOption;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.settings.PatchSettings;

/**
 * @noinspection unused
 */
public class SettingsUtils {
    private static final Map<Integer, ArchiveService> archiveServices = constructArchiveServiceMap(
            List.of(
                new ArchiveService("https://web.archive.org/web/", "Wayback Machine", "morphe_boost_archive_wayback"),
                new ArchiveService("https://archive.is/", "Archive.is", "morphe_boost_archive_archive_is"),
                new ArchiveService("https://wallhop.io/", "Wallhop", "morphe_boost_archive_wallhop"),
                new ArchiveService("https://smry.ai/", "smry.ai", "morphe_boost_archive_smry"),
                new ArchiveService("https://www.removepaywall.com/search?url=", "RemovePaywall", "morphe_boost_archive_removepaywall"),
                new ArchiveService("https://freedium-mirror.cfd/", "Freedium", "morphe_boost_archive_freedium"),
                new ArchiveService("https://unwall.app/", "Unwall", "morphe_boost_archive_unwall")
            ), 101);

    static final class ArchiveService {
        final String baseUrl;
        final String title;
        final String preferenceKey;

        ArchiveService(String baseUrl, String title, String preferenceKey) {
            this.baseUrl = baseUrl;
            this.title = title;
            this.preferenceKey = preferenceKey;
        }
    }

    private static Map<Integer, ArchiveService> constructArchiveServiceMap(List<ArchiveService> services, int startIndex) {
        Map<Integer, ArchiveService> result = new LinkedHashMap<>();
        for (int index = 0; index < services.size(); index++) {
            result.put(startIndex + index, services.get(index));
        }
        return result;
    }

    static Map<Integer, ArchiveService> enabledArchiveServices(Predicate<String> enabled) {
        Map<Integer, ArchiveService> result = new LinkedHashMap<>();
        archiveServices.forEach((id, service) -> {
            if (enabled.test(service.preferenceKey)) result.put(id, service);
        });
        return result;
    }

    public static String getArchiveBaseUrl(int menuOptionId) {
        ArchiveService service = archiveServices.get(menuOptionId);
        return service != null && PatchSettings.isEnabled(service.preferenceKey, true) ? service.baseUrl : null;
    }

    public static void openArchiveLink(Context context, String url, String archivePrefix) {
        if (PatchSettings.isEnabled("morphe_boost_archive_strip_query_parameters", true)) {
            url = stripQueryParameters(url);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(archivePrefix + url));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void addMenuOptions(List<MenuOption> options) {
        Map<Integer, ArchiveService> enabled = enabledArchiveServices(key -> PatchSettings.isEnabled(key, true));

        MenuOption divider = new MenuOption();
        divider.divider = true;
        options.add(divider);

        int drawableId = ResourceUtils.getDrawableIdentifier("ic_library_24dp");
        ArrayList<MenuOption> sources = new ArrayList<>(enabled.size());

        enabled.forEach((menuOptionId, service) -> {
            MenuOption option = new MenuOption();
            option.id = menuOptionId;
            option.title = service.title;
            option.iconResource = drawableId;
            sources.add(option);
        });

        MenuOption submenu = new MenuOption();
        submenu.title = enabled.isEmpty() ? "All archivers disabled" : "Open with";
        submenu.enabled = !enabled.isEmpty();
        submenu.iconResource = drawableId;
        submenu.children = sources;
        options.add(submenu);
    }

    static String stripQueryParameters(String url) {
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        if (query < 0 || fragment >= 0 && query > fragment) return url;
        return url.substring(0, query) + (fragment < 0 ? "" : url.substring(fragment));
    }
}
