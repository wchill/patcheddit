package app.morphe.patches.reddit.customclients.boostforreddit.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal val getOAuthAccessTokenFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/String",
    filters = listOf(string("access_token")),
    custom = { method, _ -> method.definingClass == "Lnet/dean/jraw/http/oauth/OAuthData;" }
)

internal val handleNavigationFingerprint = Fingerprint(
    filters = listOf(
        string("android.intent.action.SEARCH"),
        string("subscription"),
        string("sort"),
        string("period"),
        string("boostforreddit.com/themes"),
    )
)