package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val parseRedditVideoNetworkResponseFingerprint = Fingerprint(
    name = "parseNetworkResponse",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_INSTANCE,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_DIRECT,
        Opcode.CONST_WIDE_32,
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "RedditVideoRequest.java"
    }
)
