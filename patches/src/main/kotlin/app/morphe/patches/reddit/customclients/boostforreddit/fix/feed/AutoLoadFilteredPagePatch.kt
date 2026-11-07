/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.feed

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.patches.all.misc.PatchCategory

private const val FILTERED_PAGE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostFilteredPageAutoLoad;"

// Native "Load next" Snackbar action: calls Callbacks.m0() (next page).

// ThingFragment.showFilteredNextPageSnackbar(View): shown when content-type
// filters removed every post on a page.
private val filteredNextPageSnackbarFingerprint = BoostApi.ThingFragment.filteredNextPageSnackbar.fingerprint()

@Suppress("unused")
val autoLoadFilteredPagePatch = morpheBytecodePatch(
    name = "Auto-load next page after filtered results",
    description =
        "Loads the next page automatically when content filters hide every post on a page, " +
            "instead of leaving the feed stuck behind a \"Results were filtered\" Snackbar.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.FEED,
) { setting ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        setting.guardHook(FILTERED_PAGE_EXTENSION_DESCRIPTOR, "onPageFiltered")
        filteredNextPageSnackbarFingerprint.method.apply {
            // q1 also shows "Review filter". Only replace its Load next branch.
            val nextPageIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.SGET_OBJECT &&
                    getReference<FieldReference>()?.toString() ==
                    BoostApi.Snackbar.styleAttributes.toString()
            }
            addInstructionsAtControlFlowLabel(
                nextPageIndex,
                """
                    check-cast p0, ${BoostApi.SubmissionFragment.type}
                    new-instance v0, ${BoostApi.SubmissionRemoveClickListener.type}
                    invoke-direct {v0, p0}, ${BoostApi.SubmissionRemoveClickListener.construct}
                    invoke-static {p1, v0}, Lapp/morphe/extension/boost/utils/BoostFilteredPageAutoLoad;->onPageFiltered(Landroid/view/View;Landroid/view/View${'$'}OnClickListener;)Z
                    move-result v0
                    if-eqz v0, :morphe_show_native_snackbar
                    return-void
                    :morphe_show_native_snackbar
                    nop
                """,
            )
        }
    }
}
