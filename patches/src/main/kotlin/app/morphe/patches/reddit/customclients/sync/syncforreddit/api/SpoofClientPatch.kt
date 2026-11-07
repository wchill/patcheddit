/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.StringComparisonType
import app.morphe.patches.all.misc.string.replaceStringPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch
import app.morphe.patches.reddit.customclients.overrideOAuthReturn
import app.morphe.patches.reddit.customclients.overrideOAuthStrings

val spoofClientPatch = spoofClientPatch(ExtensionPatches.Sync) { _, _, _ ->
    dependsOn(
        ExtensionPatches.Sync,
        disablePiracyDetectionPatch,
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com", comparison = StringComparisonType.CONTAINS)
    )

    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // region Patch client id.
        GetBearerTokenFingerprint.overrideOAuthReturn("getBasicAuthorization")
        GetAuthorizationStringFingerprint.match().overrideOAuthStrings("getAuthorizationUrl")
        // endregion

        // region Patch redirect URI.
        GetRedirectUriFingerprint.overrideOAuthReturn("getRedirectUri")
        // endregion

        // region Patch user agent.
        GetUserAgentFingerprint.overrideOAuthReturn("getUserAgent")
        // endregion

        // region Patch Imgur API URL.

        ImgurImageAPIFingerprint.match().overrideOAuthStrings("getImgurImageApi")

        // endregion
    }
}
