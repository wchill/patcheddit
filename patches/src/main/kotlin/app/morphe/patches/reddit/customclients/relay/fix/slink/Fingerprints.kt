package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal object IntentFilterActivityFingerprint : Fingerprint(
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

internal val refreshTokenAccessFilter = fieldAccess(
    definingClass = "Lreddit/news/oauth/reddit/model/RedditAccessToken;",
    name = "refreshToken",
    opcode = Opcode.IPUT_OBJECT,
)

internal object RefreshTokenAccessFingerprint : Fingerprint(
    filters = listOf(
        refreshTokenAccessFilter
    ),
    custom = { _, classDef ->
        !classDef.type.startsWith("Lapp/morphe/")
    }
)
