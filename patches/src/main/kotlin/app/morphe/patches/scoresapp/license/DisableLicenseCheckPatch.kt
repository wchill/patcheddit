package app.morphe.patches.scoresapp.license

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.scoresapp.ScoresAppCompatible
import app.morphe.util.returnEarly

// Google's pairip license check. The Application and a ContentProvider both call it on
// startup. A re-signed app fails it and shows a "Get this app from Play" paywall.
internal val checkLicenseFingerprint = Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "checkLicense",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

val disableLicenseCheckPatch = bytecodePatch(
    name = "Disable Play license check",
    description = "Lets the patched app start without the Google Play license check.",
    default = true
) {
    compatibleWith(*ScoresAppCompatible)

    execute {
        checkLicenseFingerprint.method.returnEarly()
    }
}
