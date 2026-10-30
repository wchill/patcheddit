package app.morphe.patches.reddit.customclients.relay.fix.sidebar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * `JSONObject#getInt` throws when the key is absent, unlike `optInt` which falls back to zero.
 */
internal val jsonGetIntFilter = methodCall(
    definingClass = "Lorg/json/JSONObject;",
    name = "getInt",
)

/**
 * Handles the response of the subreddit `about` request that backs the sidebar.
 *
 * Reddit no longer returns `accounts_active`, so reading it throws and the whole handler falls
 * into its catch block, reporting "Failed to load sidebar" without rendering the sidebar that had
 * already been fetched successfully.
 */
internal val sideBarResponseFingerprint = Fingerprint(
    strings = listOf(
        "subscribers",
        "accounts_active",
        "description_html",
        "Failed to load sidebar",
    ),
)
