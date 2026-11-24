/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.joey;

import app.morphe.extension.shared.fixes.redgifs.BaseFixRedgifsApiPatch;

public final class FixRedgifsApiPatch extends BaseFixRedgifsApiPatch {
    @Override
    public String getDefaultUserAgent() {
        return "Joey";
    }

    @Override
    protected boolean preferAudioSources() {
        return true;
    }
}
