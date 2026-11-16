/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.shared.fixes.login;

import android.annotation.SuppressLint;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.Map;

@SuppressWarnings("unused")
public class ModifyWebViewPatch {
    @SuppressLint("SetJavaScriptEnabled")
    public static void loadUrl(WebView webView, String url, Map<String, String> additionalHeaders) {
        if (url.contains("reddit.com/login") || url.contains("reddit.com/api/v1/authorize")) {
            WebSettings webSettings = webView.getSettings();
            webSettings.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/147.0.0.0 Mobile Safari/537.36"
            );
            webSettings.setJavaScriptEnabled(true);
        }
        webView.loadUrl(url, additionalHeaders);
    }

    public static void loadUrl(WebView webView, String url) {
        loadUrl(webView, url, Map.of());
    }

    public static void onPageFinished(WebView webView, String url) {
        if (!url.contains("reddit.com/login") && !url.contains("reddit.com/api/v1/authorize")) {
            return;
        }

        webView.evaluateJavascript(
            "(function(){" +
            "    document.addEventListener('DOMContentLoaded', function(e) {" +
            "        document.querySelector('#data-protection-consent-dialog > rpl-modal-card > button.button-secondary')" +
            "        .click()" +
            "    });" +
            "})()",
            null
        );

        webView.evaluateJavascript(
            "(function() {" +
            "  document.addEventListener('submit', function(e) {" +
            "    if (e.submitter && e.submitter.name === 'authorize') {" +
            "      e.submitter.value = 'Allow';" +
            "    }" +
            "  }, true);" +
            "})();",
            null
        );
    }
}
