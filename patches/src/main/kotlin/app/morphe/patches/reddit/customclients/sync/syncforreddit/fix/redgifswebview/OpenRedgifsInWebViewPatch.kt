/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifswebview

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HELPER_CLASS = "Lapp/morphe/extension/syncforreddit/RedgifsWebViewHelper;"

@Suppress("unused")
val openRedgifsInWebView = bytecodePatch(
    name = "Open Redgifs links in WebView on failure",
    description = "When a Redgifs video fails to load in the native player" +
        " (\"Error connecting to Redgifs\"), automatically falls back to an in-app WebView" +
        " instead of showing the error. The native/API player is always tried first; the" +
        " WebView only opens as a fallback on failure. Redgifs pages in the WebView also" +
        " try to auto-accept the cookie consent banner and keep it accepted between opens.",
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    dependsOn(ExtensionPatches.Sync)

    execute {
        // Hook the failure listener of the Redgifs playback flow instead of the link
        // opener: the native/API player always runs first, and only an actual failure
        // (dead endpoint, 404 on a malformed ID, bot detection, ...) opens the WebView.
        // Unconditional -- no settings gate. On fallback we finish the player and skip
        // its error handling entirely: no error flash before the WebView appears, and
        // Back from the WebView returns to the page that opened the player instead of
        // a dead error screen.
        redgifsErrorListenerFingerprint.method.apply {
            addInstructions(
                0,
                """
                iget-object v0, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment${'$'}g0;->b:Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment;
                invoke-virtual { v0 }, Landroidx/fragment/app/Fragment;->B0()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                if-eqz v0, :noFallback
                iget-object v1, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment${'$'}g0;->a:Ljava/lang/String;
                invoke-static { v0, v1 }, Lcom/laurencedawson/reddit_sync/ui/activities/WebViewActivity;->K0(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;
                move-result-object v1
                invoke-virtual { v0, v1 }, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                invoke-virtual { v0 }, Landroid/app/Activity;->finish()V
                return-void
                :noFallback
                """
            )
        }

        // Auto-accept the CookieYes consent banner on redgifs pages loaded in the
        // in-app WebView (all logic lives in the extension helper; no extra
        // registers needed here since onPageFinished's params are the arguments).
        webViewClientOnPageFinishedFingerprint.method.apply {
            addInstructions(
                0,
                """
                invoke-static { p1, p2 }, $HELPER_CLASS->onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
                """
            )
        }

        // Sync wipes all cookies on every fresh WebView open. Replace the wipe
        // with a helper call that skips it for redgifs URLs, so the consent the
        // auto-click above accepts actually persists. Single-instruction ops
        // only -- no control flow inserted into the target method.
        webViewFragmentOnViewCreatedFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "removeAllCookie" &&
                    reference.definingClass == "Landroid/webkit/CookieManager;"
            }
            addInstructions(
                index,
                """
                iget-object v0, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/WebViewFragment;->r0:Ljava/lang/String;
                """
            )
            replaceInstruction(
                index + 1,
                "invoke-static { p1, v0 }, $HELPER_CLASS->removeAllCookieUnlessRedgifs(Landroid/webkit/CookieManager;Ljava/lang/String;)V"
            )
        }
    }
}
