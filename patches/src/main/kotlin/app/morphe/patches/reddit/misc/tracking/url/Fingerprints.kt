package app.morphe.patches.reddit.misc.tracking.url

import app.morphe.patcher.Fingerprint

internal val shareLinkFormatterFingerprint = Fingerprint(
    custom = { _, classDef ->
        classDef.startsWith("Lcom/reddit/sharing/") && classDef.sourceFile == "UrlUtil.kt"
    }
)
