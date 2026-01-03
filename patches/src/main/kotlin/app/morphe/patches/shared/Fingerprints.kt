package app.morphe.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import com.android.tools.smali.dexlib2.AccessFlags

internal object CastContextFetchFingerprint : Fingerprint(
    strings = listOf(
        "Error fetching CastContext."
    )
)

internal object PrimeMethodFingerprint : Fingerprint(
    strings = listOf(
        "com.android.vending",
        "com.google.android.GoogleCamera"
    )
)

//
// YouTube / YT Music fingerprints
//

// Flag is present in YT 20.23, but bold icons are missing and forcing them crashes the app.
// 20.31 is the first target with all the bold icons present.
internal object BoldIconsFeatureFlagFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45685201L)
    )
)

