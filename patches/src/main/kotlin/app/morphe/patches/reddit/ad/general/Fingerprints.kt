package app.morphe.patches.reddit.ad.general

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import com.android.tools.smali.dexlib2.Opcode

internal val adPostFingerprint = Fingerprint(
    returnType = "V",
    strings = listOf(
        "children"
    ),
    custom = { _, classDef -> classDef.endsWith("Listing;") }
)

internal val newAdPostFingerprint = Fingerprint(
    strings = listOf(
        "chain",
        "feedElement"
    ),
    filters = listOf(
        OpcodeFilter(Opcode.INVOKE_VIRTUAL),
    ),
    custom = { _, classDef -> classDef.sourceFile == "AdElementConverter.kt" }
)
