package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.Fingerprint

internal val buildUserAgentFingerprint = Fingerprint(
    strings = listOf("%s:%s:%s (by /u/%s)")
)

internal val getClientIdFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("Credentials;")) return@Fingerprint false

        method.name == "getClientId"
    }
)

internal val loginActivityOnCreateFingerprint = Fingerprint(
    strings = listOf("http://rubenmayayo.com"),
    custom = { method, classDef ->
        if (!classDef.endsWith("LoginActivity;")) return@Fingerprint false

        method.name == "onCreate"
    }
)

internal val loginActivityAShouldOverrideUrlLoadingFingerprint = Fingerprint(
    strings = listOf("http://rubenmayayo.com"),
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
