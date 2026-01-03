package app.morphe.patches.reddit.misc.tracking.url

import app.morphe.patcher.fingerprint

internal val shareLinkFormatterFingerprint = fingerprint {
    custom { _, classDef ->
        classDef.startsWith("Lcom/reddit/sharing/") && classDef.sourceFile == "UrlUtil.kt"
    }
}