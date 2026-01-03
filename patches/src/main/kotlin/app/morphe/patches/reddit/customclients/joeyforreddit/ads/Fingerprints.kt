package app.morphe.patches.reddit.customclients.joeyforreddit.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal val isAdFreeUserFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    filters = listOf(string("AD_FREE_USER"))
)