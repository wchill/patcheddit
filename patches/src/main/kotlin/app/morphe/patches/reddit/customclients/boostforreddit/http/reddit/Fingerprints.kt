/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val installJrawInterceptorFingerprint = Fingerprint(
    definingClass = "Lnet/dean/jraw/http/OkHttpAdapter;",
    name = "newOkHttpClient",
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
    definingClass = "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.CONSTRUCTOR),
    returnType = "V",
)

internal val contributionModelWriteToParcelFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;",
    name = "writeToParcel",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
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
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;",
    name = "a",
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("copyright_takedown", "reddit"),
)

internal val stateSubmissionViewGetSummaryFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;",
    name = "b",
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("copyright_takedown", "reddit"),
)

internal val stateSubmissionViewHasValidRemovalReasonFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/StateSubmissionView;",
    name = "d",
    accessFlags = listOf(AccessFlags.PRIVATE),
    strings = listOf("reddit"),
)
