/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.api

import app.morphe.patcher.Match
import app.morphe.patcher.StringComparisonType
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.all.misc.string.replaceStringPatch
import app.morphe.patches.reddit.customclients.overrideOAuthReturn
import app.morphe.patches.reddit.customclients.overrideOAuthValue

val spoofClientPatch = spoofClientPatch(ExtensionPatches.BaconReader) { _, _, _ ->
    dependsOn(
        ExtensionPatches.BaconReader,
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com", comparison = StringComparisonType.CONTAINS)
    )

    compatibleWith(*AppCompatibility.BaconReader)

    execute {
        fun Match.patch(getter: String) {
            val clientIdIndex = stringMatches.first().index
            method.overrideOAuthValue(clientIdIndex, getter)
        }

        // Patch client id in authorization url.
        GetAuthorizationUrlFingerprint.match().patch("getClientIdParameter")

        // Patch client id for access token request.
        RequestTokenFingerprint.match().patch("getClientId")

        // Patch redirect uri in authorization url. Unlike other clients, the app bakes the
        // query parameter name into the string, so it has to be kept.
        GetAuthorizeUrlFingerprint.match().patch("getRedirectUriParameter")

        // Patch the bare redirect uri, used to recognize the callback and to request the token.
        AuthUrlFingerprint.matchAll().forEach { match -> match.patch("getRedirectUri") }

        GetRestClientUserAgentFingerprint.overrideOAuthReturn("getUserAgent")
        GetRedditUserAgentFingerprint.overrideOAuthReturn("getUserAgent")
    }
}
