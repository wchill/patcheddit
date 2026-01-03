package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.thumbnail

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val customImageViewLoadFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf("Ljava/lang/String;", "Z", "Z", "I", "I"),
    custom = { _, classDef ->
        classDef.endsWith("CustomImageView;")
    }
)
