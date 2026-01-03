package app.morphe.patches.reddit.customclients.joeyforreddit.ads

import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.fingerprint

internal val isAdFreeUserFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("Z")
    strings("AD_FREE_USER")
}