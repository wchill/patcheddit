package app.morphe.patches.reddit.customclients.boostforreddit.fix.slink

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val getOAuthAccessTokenFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/String",
    strings = listOf("access_token"),
    custom = { method, _ -> method.definingClass == "Lnet/dean/jraw/http/oauth/OAuthData;" }
)

internal val handleNavigationFingerprint = Fingerprint(
    strings = listOf(
        "android.intent.action.SEARCH",
        "subscription",
        "sort",
        "period",
        "boostforreddit.com/themes",
    )
)