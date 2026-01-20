package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal val intentFilterActivityFingerprint = Fingerprint(
    strings = listOf("path", "amp.reddit.com"),
    filters = listOf(
        string("path"),
        string("amp.reddit.com"),
        methodCall(name = "getIntent"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        methodCall(name = "getDataString"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT)
    ),
    custom = { _, classDef -> classDef.type == "Lreddit/news/IntentFilterActivity;" }
)
