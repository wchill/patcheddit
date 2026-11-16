/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.fix.redgifs

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(ExtensionPatches.Joey) {
    compatibleWith(*AppCompatibility.JoeyForReddit)
}
