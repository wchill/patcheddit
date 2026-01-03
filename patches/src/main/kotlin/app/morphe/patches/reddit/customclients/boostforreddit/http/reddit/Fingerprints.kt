package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val installJrawInterceptorFingerprint = Fingerprint(
    custom = { method, _ -> method.name == "newOkHttpClient" && method.definingClass == "Lnet/dean/jraw/http/OkHttpAdapter;" }
)

// Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;->O(Lcom/rubenmayayo/reddit/models/reddit/CommentModel;
internal val setCommentBabushkaTextFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lcom/rubenmayayo/reddit/models/reddit/CommentModel"),
    strings = listOf("\uD83D\uDD12")
)

// Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;->n0(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)V
internal val setSubmissionBabushkaTextFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel"),
    strings = listOf("\uD83D\uDD12")
)

internal val contributionModelConstructorFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    custom = { method, _ -> method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
)

internal val contributionModelWriteToParcelFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    custom = { method, _ -> method.name == "writeToParcel" && method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
)

internal val submissionModelDeserializeFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel",
    parameters = listOf("Lnet/dean/jraw/models/Submission")
)

internal val commentModelDeserializeFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/rubenmayayo/reddit/models/reddit/CommentModel",
    parameters = listOf("Lnet/dean/jraw/models/Comment")
)

internal val stateSubmissionViewGetHeaderFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("copyright_takedown", "reddit"),
    custom = { method, _ -> method.name == "a" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
)

internal val stateSubmissionViewGetSummaryFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("copyright_takedown", "reddit"),
    custom = { method, _ -> method.name == "b" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
)

internal val stateSubmissionViewHasValidRemovalReasonFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("reddit"),
    custom = { method, _ -> method.name == "d" && method.definingClass == "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;" }
)
