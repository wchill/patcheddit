package app.morphe.patches.reddit.customclients.joeyforreddit.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val isAdFreeUserFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    strings = listOf("AD_FREE_USER")
)