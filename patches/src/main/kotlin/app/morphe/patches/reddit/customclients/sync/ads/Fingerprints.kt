package app.morphe.patches.reddit.customclients.sync.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object IsAdsEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    strings = listOf("SyncIapHelper")
)