/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

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
