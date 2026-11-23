package app.morphe.patches.reddit.customclients.redditisfun.fix.slink

import app.morphe.patcher.Fingerprint
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

internal val fixSLinksPatch = fixSLinksPatch(ExtensionPatches.RIF) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(shareViewFilters(setOf(MAIN, ".MainActivity"),
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
                invoke-static {p0}, $SHARE_NAV->openActivity(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :morphe_skip_before
                return-void
            """)
        }
        val freeRouter = Fingerprint(definingClass = "Ll3/g;", name = "q", returnType = "V",
            parameters = listOf(STRING, STRING, "Ln3/d;", STRING, STRING, "Z",
                "Lcom/andrewshu/android/reddit/ads/WhitelistStatus;",
                "Landroidx/fragment/app/FragmentManager;", CONTEXT, "Lu1/b;"))
        val platinumRouter = Fingerprint(definingClass = "Lm3/g;", name = "q", returnType = "V",
            parameters = listOf(STRING, STRING, "Lo3/d;", STRING, STRING, "Z", "Lt1/f;",
                "Landroidx/fragment/app/FragmentManager;", CONTEXT, "Lv1/b;"))
        val router = listOf(freeRouter, platinumRouter).single { it.matchOrNull() != null }
        wrapMorpheHook(router.classDef, router.method, before = """
            const-string v1, "$MAIN"
            invoke-static {p8, p0, v1}, $SHARE_NAV->$OPEN_SHARE
            move-result v0
            if-eqz v0, :morphe_skip_before
            return-void
        """)
        val preferences = if (router === freeRouter) "Lk4/v;" else "Ll4/v;"
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
