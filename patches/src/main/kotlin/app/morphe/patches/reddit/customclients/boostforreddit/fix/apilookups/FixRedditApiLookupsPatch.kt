/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.apilookups

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.fixRedditApiLookupsPatch

@Suppress("unused")
val fixRedditApiLookups = fixRedditApiLookupsPatch(
    extensionPatch = interceptHttpRequests,
    compatible = AppCompatibility.Boost,
)
