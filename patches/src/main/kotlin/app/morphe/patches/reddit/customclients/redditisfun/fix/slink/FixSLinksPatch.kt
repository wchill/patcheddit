package app.morphe.patches.reddit.customclients.redditisfun.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.OPEN_SHARE
import app.morphe.patches.reddit.customclients.SHARE_NAV
import app.morphe.patches.reddit.customclients.ShareViewFilter
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.shareViewFilters
import app.morphe.patches.reddit.customclients.wrapMorpheHook

private const val MAIN = "com.andrewshu.android.reddit.MainActivity"
private const val STRING = "Ljava/lang/String;"
private const val CONTEXT = "Landroid/content/Context;"
private const val ACCOUNT = "Landroid/accounts/Account;"
private const val OBJECT = "Ljava/lang/Object;"
private const val URI = "Landroid/net/Uri;"
private const val WEB_VIEW = "Landroid/webkit/WebView;"
private const val FRAGMENTS = "Landroidx/fragment/app/FragmentManager;"
private const val MEDIA = "Lapp/morphe/extension/redditisfun/media/OpenMediaLinks;"

internal val fixSLinksPatch = fixSLinksPatch(ExtensionPatches.RIF) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(shareViewFilters(setOf(MAIN, ".MainActivity"),
        *listOf("reddit.com", "www.reddit.com", "np.reddit.com").flatMap { host ->
            listOf(ShareViewFilter(host, "path", "/media"), ShareViewFilter(host, "path", "/media/"))
        }.toTypedArray(),
        ShareViewFilter("matrix.redditspace.com", "pathPrefix", "/_matrix/media/"),
        ShareViewFilter("np.reddit.com", "pathPrefix", "/u/"),
        ShareViewFilter("np.reddit.com", "pathPrefix", "/user/"),
    ))
    execute {
        Fingerprint(
            definingClass = "Lcom/andrewshu/android/reddit/MainActivity;",
            name = "l1",
            parameters = emptyList(),
            returnType = "V",
            strings = listOf("reddit url type %s"),
        ).apply {
            wrapMorpheHook(classDef, method, before = """
                invoke-static {p0}, $MEDIA->openActivity(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :morphe_media_opened
                invoke-static {p0}, $SHARE_NAV->openActivity(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :morphe_skip_before
                :morphe_media_opened
                return-void
            """)
        }
        val freeRouter = Fingerprint(definingClass = "Ll3/g;", name = "q", returnType = "V",
            parameters = listOf(STRING, STRING, "Ln3/d;", STRING, STRING, "Z",
                "Lcom/andrewshu/android/reddit/ads/WhitelistStatus;",
                FRAGMENTS, CONTEXT, "Lu1/b;"))
        val platinumRouter = Fingerprint(definingClass = "Lm3/g;", name = "q", returnType = "V",
            parameters = listOf(STRING, STRING, "Lo3/d;", STRING, STRING, "Z", "Lt1/f;",
                FRAGMENTS, CONTEXT, "Lv1/b;"))
        val router = listOf(freeRouter, platinumRouter).single { it.matchOrNull() != null }
        val free = router === freeRouter
        wrapMorpheHook(router.classDef, router.method, before = """
            invoke-static/range {p0 .. p9}, $MEDIA->openRouter($STRING$STRING$OBJECT$STRING${STRING}Z$OBJECT$OBJECT$CONTEXT$OBJECT)Z
            move-result v0
            if-nez v0, :morphe_media_opened
            const-string v1, "$MAIN"
            invoke-static {p8, p0, v1}, $SHARE_NAV->$OPEN_SHARE
            move-result v0
            if-eqz v0, :morphe_skip_before
            :morphe_media_opened
            return-void
        """)

        val whitelist = router.method.parameterTypes[6].toString()
        val source = router.method.parameterTypes[9].toString()
        val browser = Fingerprint(
            definingClass = router.method.definingClass,
            name = "r",
            returnType = "V",
            parameters = listOf(URI, STRING, STRING, STRING, "Z", whitelist, FRAGMENTS, CONTEXT, source),
            strings = listOf("com.andrewshu.android.reddit.EXTRA_BROWSER_DISPLAY_URL"),
        ).method
        Fingerprint(
            definingClass = MEDIA,
            name = "openNative",
            returnType = "V",
            parameters = listOf(URI, STRING, STRING, STRING, "Z", OBJECT, OBJECT, CONTEXT, OBJECT),
        ).apply {
            wrapMorpheHook(classDef, method, before = """
                check-cast p5, $whitelist
                check-cast p6, $FRAGMENTS
                check-cast p8, $source
                invoke-static/range {p0 .. p8}, ${browser.definingClass}->${browser.name}($URI$STRING$STRING${STRING}Z$whitelist$FRAGMENTS$CONTEXT$source)V
                return-void
            """)
        }

        // Stock WebView client calls the Reddit classifier directly rather than router q().
        val webClient = if (free) "Lv1/z;" else "Lw1/z;"
        Fingerprint(
            definingClass = webClient,
            name = "shouldOverrideUrlLoading",
            returnType = "Z",
            parameters = listOf(WEB_VIEW, STRING),
            strings = listOf("http", "spotify"),
        ).method.apply {
            // Edit the virtual method in place: the login patch also edits this WebViewClient's
            // virtual method list, which can restore the original entry if it is replaced.
            check(checkNotNull(implementation).registerCount in 4..16)
            addInstructionsWithLabels(0, """
                invoke-virtual {p0}, $webClient->a()$CONTEXT
                move-result-object v0
                invoke-static {v0, p1, p2}, $MEDIA->openWebView($CONTEXT$WEB_VIEW$STRING)Z
                move-result v0
                if-eqz v0, :original
                return v0
            """, ExternalLabel("original", getInstruction(0)))
        }
        val preferences = if (free) "Lk4/v;" else "Ll4/v;"
        bindShareLinkAccessToken("""
            invoke-static {}, $preferences->C()$preferences
            move-result-object v0
            invoke-virtual {v0}, $preferences->g()$ACCOUNT
            move-result-object v1
            if-eqz v1, :morphe_guest_token
            invoke-static {p0, v1}, $SHARE_NAV->getRifCachedToken($CONTEXT$ACCOUNT)$STRING
            move-result-object v0
            return-object v0
            :morphe_guest_token
            invoke-virtual {v0}, $preferences->A()$STRING
            move-result-object v0
            return-object v0
        """)
    }
}
