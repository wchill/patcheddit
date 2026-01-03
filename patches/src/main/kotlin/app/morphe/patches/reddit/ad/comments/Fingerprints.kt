package app.morphe.patches.reddit.ad.comments

import app.morphe.patcher.Fingerprint

internal val hideCommentAdsFingerprint = Fingerprint(
    strings = listOf(
        "link",
        // CommentPageRepository is not returning a link object
        "is not returning a link object"
    ),
    custom = {
        _, classDef -> classDef.sourceFile == "PostDetailPresenter.kt"
    }
)