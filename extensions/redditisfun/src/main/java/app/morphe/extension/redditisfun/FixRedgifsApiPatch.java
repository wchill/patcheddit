/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.fixes.redgifs.RedgifsTokenManager;

/**
 * @noinspection unused
 */
public class FixRedgifsApiPatch {
    // RIF's hardcoded RedGifs user agent; RedGifs binds the temporary token to it.
    private static final String USER_AGENT = "org.quantumbadger.redreader/1.25.1";

    public static String getRedgifsToken() {
        try {
            return RedgifsTokenManager.refreshToken(USER_AGENT).getAccessToken();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not fetch RedGifs token", ex);
            return null;
        }
    }
}
