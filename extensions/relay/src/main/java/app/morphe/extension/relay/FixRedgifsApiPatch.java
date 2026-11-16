/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay;

import app.morphe.extension.shared.fixes.redgifs.BaseFixRedgifsApiPatch;

public final class FixRedgifsApiPatch extends BaseFixRedgifsApiPatch {
    @Override
    public String getDefaultUserAgent() {
        return "Relay";
    }
}
