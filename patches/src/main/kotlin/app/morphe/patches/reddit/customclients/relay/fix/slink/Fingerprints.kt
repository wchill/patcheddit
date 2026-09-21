package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal val intentFilterActivityFingerprint = Fingerprint(
    definingClass = "Lreddit/news/IntentFilterActivity;",
    strings = listOf("path", "amp.reddit.com"),
    filters = listOf(
        string("path"),
        string("amp.reddit.com"),
        methodCall(name = "getIntent"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        methodCall(name = "getDataString"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT)
    ),
)

/**
 * `UrlLinkClickManager#c(String, FragmentActivity)`, which every in-app link tap is routed
 * through. `b(String, Fragment)` delegates here, so hooking this covers both entry points.
 *
 * Without this, an /s/ link tapped inside the app reaches the subreddit predicate, which matches
 * on the leading `r` path segment and opens a subreddit named after the /s/ slug.
 */
internal val urlLinkClickManagerFingerprint = Fingerprint(
    definingClass = "Lreddit/news/utils/UrlLinkClickManager;",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Landroidx/fragment/app/FragmentActivity;"),
    strings = listOf(
        "data:",
        "SpoilerDialog",
        "MultiRedditFragment",
        "AccountFragment",
        "SearchFragment",
        "SubredditFragment",
    ),
)

/**
 * `WebAndCommentsFragment#c(String, boolean)`, the link handler used by the post and comment
 * view. It duplicates [urlLinkClickManagerFingerprint]'s routing rather than delegating to it,
 * so it needs its own hook or /s/ links tapped in a comment still resolve to a subreddit.
 *
 * The imgur strings are what keep this from matching `UrlLinkClickManager` itself, whose string
 * set is otherwise near-identical.
 */
internal val webAndCommentsFragmentLinkFingerprint = Fingerprint(
    definingClass = "Lreddit/news/WebAndCommentsFragment;",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"),
    strings = listOf(
        "data:",
        "SpoilerDialog",
        "imgur.com",
        "MultiRedditFragment",
        "AccountFragment",
        "SubredditFragment",
    ),
)

internal val refreshTokenAccessFilter = fieldAccess(
    definingClass = "Lreddit/news/oauth/reddit/model/RedditAccessToken;",
    name = "refreshToken",
    opcode = Opcode.IPUT_OBJECT,
)

internal val refreshTokenAccessFingerprint = Fingerprint(
    filters = listOf(
        refreshTokenAccessFilter
    ),
    custom = { _, classDef ->
        !classDef.type.startsWith("Lapp/morphe/")
    }
)
