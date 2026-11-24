package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.SHARE_NAV
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.redditWrapperViewFilters
import app.morphe.patches.reddit.customclients.wrapMorpheHook

private const val ACTIVITY = "ml.docilealligator.infinityforreddit.activities.LinkResolverActivity"

internal val infinityShareLinks = fixSLinksPatch(ExtensionPatches.Infinity) {
    compatibleWith(*AppCompatibility.Infinity)
    dependsOn(redditWrapperViewFilters(ACTIVITY))
    execute {
        Fingerprint(
            definingClass = "L${ACTIVITY.replace('.', '/')};",
            returnType = "V", parameters = listOf("Landroid/net/Uri;"),
            strings = listOf("click.redditmail.com", "redgifs.com"),
        ).wrapMorpheHook(before = """
            invoke-static {p0, p1}, $SHARE_NAV->openInfinity(Landroid/app/Activity;Landroid/net/Uri;)Z
            move-result v0
            if-eqz v0, :morphe_skip_before
            return-void
        """)
        bindShareLinkAccessToken("""
            const-string v0, "ml.docilealligator.infinityforreddit.current_account"
            const/4 v1, 0x0
            invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            const-string v1, "access_token"
            const/4 v2, 0x0
            invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
            return-object v0
        """)
    }
}
