package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint

internal const val SHARE_NAV = "Lapp/morphe/extension/shared/fixes/slink/ShareLinkNavigation;"
internal const val OPEN_SHARE = "open(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z"

internal fun bindShareLinkAccessToken(instructions: String) {
    Fingerprint(
        definingClass = SHARE_NAV,
        name = "getAccessToken",
        parameters = listOf("Landroid/content/Context;"),
        returnType = "Ljava/lang/String;",
    ).apply {
        // Wrap the null-returning stub to obtain scratch registers without changing its inputs.
        wrapMorpheHook(classDef, method, before = instructions)
    }
}
