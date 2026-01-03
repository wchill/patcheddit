package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal val buildUserAgentFingerprint = Fingerprint(
    filters = listOf(string("%s:%s:%s (by /u/%s)"))
)

internal val getClientIdFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("Credentials;")) return@Fingerprint false

        method.name == "getClientId"
    }
)

internal val loginActivityOnCreateFingerprint = Fingerprint(
    filters = listOf(string("http://rubenmayayo.com")),
    custom = { method, classDef ->
        if (!classDef.endsWith("LoginActivity;")) return@Fingerprint false

        method.name == "onCreate"
    }
)

internal val loginActivityAShouldOverrideUrlLoadingFingerprint = Fingerprint(
    filters = listOf(string("http://rubenmayayo.com")),
    custom = { method, classDef ->
        if (!classDef.endsWith("LoginActivity${'$'}a;")) return@Fingerprint false

        method.name == "shouldOverrideUrlLoading"
    }
)

internal val jrawNewUrlFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("JrawUtils;")) return@Fingerprint false
        method.name == "newUrl"
    }
)
