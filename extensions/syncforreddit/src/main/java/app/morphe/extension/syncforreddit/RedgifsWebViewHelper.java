package app.morphe.extension.syncforreddit;

import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebView;

/**
 * Helpers for the "Open Redgifs links in WebView" patch: auto-accept the
 * CookieYes consent banner on redgifs pages and keep the consent cookie
 * alive across WebView opens.
 */
public class RedgifsWebViewHelper {

    // CookieYes accept button: current markup ([data-cky-tag="accept-button"])
    // with the legacy class (.cky-btn-accept) as fallback. The banner is built
    // asynchronously after page-finished, so poll briefly instead of firing once.
    private static final String ACCEPT_JS =
            "(function(){var n=0,t=setInterval(function(){"
            + "var b=document.querySelector('[data-cky-tag=\"accept-button\"]')"
            + "||document.querySelector('.cky-btn-accept');"
            + "if(b){b.click();clearInterval(t);}"
            + "if(++n>20)clearInterval(t);},250);})();";

    /**
     * Called from WebViewFragment$a.onPageFinished: auto-click the CookieYes
     * accept button on redgifs pages. Clicking sets CookieYes's own consent
     * cookie, which then persists (see shouldKeepCookies), so the banner --
     * and this click -- only ever happen once.
     */
    public static void onPageFinished(WebView view, String url) {
        if (!isRedgifs(url)) return;
        view.evaluateJavascript(ACCEPT_JS, null);
    }

    /**
     * Sync's WebViewFragment wipes ALL cookies on every fresh WebView open
     * (CookieManager.removeAllCookie() before loadUrl). Called in place of the
     * wipe: skips it for redgifs URLs so the CookieYes consent cookie survives
     * between opens, and performs it unchanged for everything else.
     */
    public static void removeAllCookieUnlessRedgifs(CookieManager cookieManager, String url) {
        if (!shouldKeepCookies(url)) {
            cookieManager.removeAllCookie();
        }
    }

    public static boolean shouldKeepCookies(String url) {
        return isRedgifs(url);
    }

    private static boolean isRedgifs(String url) {
        if (url == null) return false;
        String host = Uri.parse(url).getHost();
        return host != null && (host.equals("redgifs.com") || host.endsWith(".redgifs.com"));
    }
}
