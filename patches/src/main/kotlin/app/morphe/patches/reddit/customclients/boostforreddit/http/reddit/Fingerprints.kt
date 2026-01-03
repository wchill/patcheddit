package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val installJrawInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "newOkHttpClient" && method.definingClass == "Lnet/dean/jraw/http/OkHttpAdapter;" }
}

// Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;->O(Lcom/rubenmayayo/reddit/models/reddit/CommentModel;
internal val setCommentBabushkaTextFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    parameters("Lcom/rubenmayayo/reddit/models/reddit/CommentModel")
    strings("\uD83D\uDD12")
}

// Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;->n0(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)V
internal val setSubmissionBabushkaTextFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    parameters("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel")
    strings("\uD83D\uDD12")
}

internal val contributionModelConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PROTECTED, AccessFlags.CONSTRUCTOR)
    returns("V")
    custom { method, _ -> method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
}

internal val contributionModelWriteToParcelFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    custom { method, _ -> method.name == "writeToParcel" && method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
}

internal val submissionModelDeserializeFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel")
    parameters("Lnet/dean/jraw/models/Submission")
}

internal val commentModelDeserializeFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Lcom/rubenmayayo/reddit/models/reddit/CommentModel")
    parameters("Lnet/dean/jraw/models/Comment")
}

internal val stateSubmissionViewGetHeaderFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE)
    strings("copyright_takedown", "reddit")
    custom { method, _ -> method.name == "a" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
}

internal val stateSubmissionViewGetSummaryFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE)
    strings("copyright_takedown", "reddit")
    custom { method, _ -> method.name == "b" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
}

internal val stateSubmissionViewHasValidRemovalReasonFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE)
    strings("reddit")
    custom { method, _ -> method.name == "d" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
}
