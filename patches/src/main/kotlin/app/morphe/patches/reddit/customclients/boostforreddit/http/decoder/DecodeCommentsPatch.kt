/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.decoder

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

@Suppress("unused")
val decodeCommentsPatch = bytecodePatch(
    name = "Decode NATO and binary comments",
    description = "Adds the decoded text of NATO phonetic spelling and 8-bit binary below the comment " +
        "paragraph it appears in, with SimpCity and social media search links.",
    // Off by default: number words count as code words, so ordinary comments can match.
    default = false
) {
    category(PatchCategory.COMMENTS)
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests)
    compatibleWith(*AppCompatibility.Boost)

    val subreddits by stringOption(
        "subreddits",
        null,
        null,
        "Subreddits",
        "Comma-separated subreddits to decode comments in, for example \"pics, r/funny\". " +
            "Leave empty to decode comments in every subreddit.",
        false,
    )

    execute {
        EnableCommentDecoderFingerprint.method.returnEarly(true)
        GetAllowedSubredditsFingerprint.method.returnEarly(subreddits.orEmpty())
    }
}
