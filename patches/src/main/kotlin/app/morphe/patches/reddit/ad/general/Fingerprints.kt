package app.morphe.patches.reddit.ad.general

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal val adPostFingerprint = Fingerprint(
    returnType = "V",
    filters = listOf(
        string("children")
    ),
    custom = { _, classDef -> classDef.endsWith("Listing;") }
)

internal val newAdPostFingerprint = Fingerprint(
    filters = listOf(
        OpcodeFilter(Opcode.INVOKE_VIRTUAL),
        string("chain"),
        string("feedElement")
    ),
    custom = { _, classDef -> classDef.sourceFile == "AdElementConverter.kt" }
)
