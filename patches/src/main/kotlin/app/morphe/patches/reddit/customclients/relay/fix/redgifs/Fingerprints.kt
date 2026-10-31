package app.morphe.patches.reddit.customclients.relay.fix.redgifs

import app.morphe.patcher.Fingerprint

/**
 * The map stage of `RedditLink#fetchMediaPreviews`, which post-processes the previews Relay built
 * for a link. It runs on the pipeline's background scheduler, so resolving a url from here does
 * not block the main thread.
 *
 * Matched on its signature rather than its name: it is a synthetic lambda, so the name is not
 * dependable, whereas this parameter list is unique within the class.
 */
internal val postProcessMediaPreviewsFingerprint = Fingerprint(
    definingClass = "Lreddit/news/oauth/reddit/model/links/RedditLink;",
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf(
        "Lokhttp3/HttpUrl;",
        "Lreddit/news/previews/MediaUrlFetcher;",
        "Ljava/util/ArrayList;",
    ),
)
