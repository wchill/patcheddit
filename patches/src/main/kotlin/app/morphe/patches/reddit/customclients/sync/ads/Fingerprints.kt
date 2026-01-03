package app.morphe.patches.reddit.customclients.sync.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal val isAdsEnabledFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    filters = listOf(string("SyncIapHelper"))
)