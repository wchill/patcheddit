/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import java.io.File;

import app.morphe.extension.shared.Utils;

public class CacheUtils {
    public static File getHttpCacheDir() {
        return new File(Utils.getContext().getCacheDir(), "undelete_http_cache");
    }
}
