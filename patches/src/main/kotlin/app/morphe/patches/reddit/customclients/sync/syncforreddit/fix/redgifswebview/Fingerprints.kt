/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifswebview

import app.morphe.patcher.Fingerprint

// ImageViewerFragment$g0.onErrorResponse(VolleyError): the single failure funnel for
// Sync's Redgifs playback flow. The OAuth token request, the /info request, and the
// actual /v2/gifs/<id> request all deliver errors to this listener, which then shows
// "Error connecting to Redgifs" (error code 40). Its `a` field holds the original
// redgifs.com URL that was being loaded -- exactly what a WebView fallback needs.
// Class names are not obfuscated in Sync v23.06.30 and the method is a Volley
// interface override, so an exact class+name+signature match is stable.
internal val redgifsErrorListenerFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment\$g0;",
    name = "onErrorResponse",
    parameters = listOf("Lcom/android/volley/VolleyError;"),
    returnType = "V",
)

// WebViewFragment$a.onPageFinished(WebView, String): the in-app browser's
// WebViewClient page-finished callback. Used to auto-accept the CookieYes
// consent banner on redgifs pages.
internal val webViewClientOnPageFinishedFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/fragments/WebViewFragment\$a;",
    name = "onPageFinished",
    parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
    returnType = "V",
)

// WebViewFragment.o2(View, Bundle) = onViewCreated: sets up the WebView and, on
// a fresh open, clears history/form/cache and wipes ALL cookies via
// CookieManager.removeAllCookie() before loadUrl. Patched to skip the wipe for
// redgifs URLs so the CookieYes consent cookie persists between opens.
internal val webViewFragmentOnViewCreatedFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/fragments/WebViewFragment;",
    name = "o2",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    returnType = "V",
)
