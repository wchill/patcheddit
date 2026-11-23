package app.morphe.patches.reddit.customclients.joey.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.OPEN_SHARE
import app.morphe.patches.reddit.customclients.SHARE_NAV
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

private const val STRING = "Ljava/lang/String;"
private const val OAUTH = "Lnet/dean/jraw/http/oauth/OAuthData;"

internal val fixSLinksPatch = fixSLinksPatch(ExtensionPatches.Joey) {
    compatibleWith(*AppCompatibility.JoeyForReddit)
    execute {
        // Both OpenInApp and link spans invoke this dispatcher.
        Fingerprint(
            definingClass = "Leb/a;",
            name = "F",
            returnType = "V",
            parameters = listOf("Landroid/content/Context;", STRING, STRING, STRING, "Z", "Ljava/lang/Boolean;"),
        ).apply {
            wrapMorpheHook(classDef, method, before = """
                const-string v1, "o.o.joey.Activities.OpenInApp"
                invoke-static {p0, p1, v1}, $SHARE_NAV->$OPEN_SHARE
                move-result v0
                if-eqz v0, :morphe_skip_before
                return-void
            """)
        }
        bindShareLinkAccessToken("""
            invoke-static {}, Ld9/c;->q()Ld9/c;
            move-result-object v0
            if-eqz v0, :morphe_skip_before
            invoke-virtual {v0}, Ld9/c;->v()Lq7/e;
            move-result-object v0
            if-eqz v0, :morphe_skip_before
            invoke-virtual {v0}, Lq7/e;->o()$OAUTH
            move-result-object v0
            if-eqz v0, :morphe_skip_before
            invoke-virtual {v0}, $OAUTH->q()$STRING
            move-result-object v0
            return-object v0
        """)
    }
}
