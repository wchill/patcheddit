package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Should usually match: o5/i0.a()
internal val userPremiumFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf(),
    custom = { _, classDef ->
        // Expect the class to have exactly one static field of type HashSet.
        val sfIter = classDef.staticFields.iterator()
        sfIter.hasNext()
                && sfIter.next().type == "Ljava/util/HashSet;"
                && !sfIter.hasNext()
    }
)
