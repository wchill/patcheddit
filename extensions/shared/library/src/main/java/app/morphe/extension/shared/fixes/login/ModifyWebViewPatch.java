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

import app.morphe.extension.shared.Logger;

public class ModifyWebViewPatch {
    @SuppressLint("SetJavaScriptEnabled")
    public static void loadUrl(WebView webView, String url, Map<String, String> additionalHeaders) {
        if (!url.contains("reddit.com/login") && !url.contains("reddit.com/api/v1/authorize")) {
            webView.loadUrl(url, additionalHeaders);
            return;
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/147.0.0.0 Mobile Safari/537.36"
        );
        webSettings.setJavaScriptEnabled(true);
        webView.loadUrl(url, additionalHeaders);
        webView.evaluateJavascript(
            "function(){" +
                "document.addEventListener('DOMContentLoaded',(e)=>{" +
                    "document.querySelector('#data-protection-consent-dialog > rpl-modal-card > button.button-secondary')" +
                    ".click()" +
                "});" +
            "}()",
            (result) -> {}
        );
    }

    public static void loadUrl(WebView webView, String url) {
        loadUrl(webView, url, Map.of());
    }
}
