package app.morphe.patches.reddit.customclients.joey.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object IsAdFreeUserFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    returnType = "Z",
    strings = listOf("AD_FREE_USER")
)

internal object AreAdsDisabledFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/Activities/BaseActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = emptyList(),
    returnType = "Z",
    strings = listOf("inside areAdsDisabled"),
)
