package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val parseRedditVideoNetworkResponseFingerprint = Fingerprint(
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_INSTANCE,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_DIRECT,
        Opcode.CONST_WIDE_32,
    ),
    custom = { methodDef, classDef ->
        classDef.sourceFile == "RedditVideoRequest.java" && methodDef.name == "parseNetworkResponse"
    }
)
