package app.morphe.patches.reddit.ad.comments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val hideCommentAdsFingerprint = Fingerprint(
    filters = listOf(
        string("link"),
        // CommentPageRepository is not returning a link object
        string("is not returning a link object")
    ),
    custom = {
        _, classDef -> classDef.sourceFile == "PostDetailPresenter.kt"
    }
)