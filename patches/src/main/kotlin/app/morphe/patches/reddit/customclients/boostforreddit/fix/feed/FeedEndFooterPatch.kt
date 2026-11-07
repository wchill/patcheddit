/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.feed

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.all.misc.PatchCategory

private const val FEED_END_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostFeedEndFooter;"

private val SUBMISSION_FRAGMENT =
    BoostApi.SubmissionRecyclerViewFragment.type

// RedditJRAW.loadSubmissions(Paginator, boolean): loads one submissions page.
private val loadSubmissionsFingerprint = BoostApi.RedditJraw.loadSubmissions.fingerprint()

// SubmissionRecyclerViewFragment.appendSubmissions(ArrayList): adds a further page.
private val appendSubmissionsFingerprint = BoostApi.SubmissionRecyclerViewFragment.appendSubmissions.fingerprint()

// SubmissionRecyclerViewFragment.setSubmissions(ArrayList): loads a feed from the top.
private val setSubmissionsFingerprint = BoostApi.SubmissionRecyclerViewFragment.setSubmissions.fingerprint()

@Suppress("unused")
val feedEndFooterPatch = morpheBytecodePatch(
    name = "Show end of feed",
    description =
        "Shows an \"End of feed\" row with a Refresh button below the last post when a feed has " +
            "no more posts, instead of silently stopping. Home sorted by Best ends after a few " +
            "hundred posts.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        for (name in listOf("onLoadPage", "onAppendPage")) setting.guardHook(FEED_END_EXTENSION_DESCRIPTOR, name)
        loadSubmissionsFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, Lapp/morphe/extension/boost/utils/BoostFeedEndFooter;->onLoadPage(${BoostApi.Paginator.type})V",
        )

        appendSubmissionsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $SUBMISSION_FRAGMENT->mRecyclerView:${BoostApi.EmptyRecyclerView.type}
                invoke-static {p0, v0, p1}, Lapp/morphe/extension/boost/utils/BoostFeedEndFooter;->onAppendPage(Ljava/lang/Object;Landroid/view/View;Ljava/util/List;)V
            """,
        )

        setSubmissionsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $SUBMISSION_FRAGMENT->mRecyclerView:${BoostApi.EmptyRecyclerView.type}
                invoke-static {v0}, Lapp/morphe/extension/boost/utils/BoostFeedEndFooter;->onReplacePage(Landroid/view/View;)V
            """,
        )
    }
}
