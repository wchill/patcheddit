package app.morphe.patches.reddit.customclients.relay.fix.sidebar

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

@Suppress("unused")
val fixSidebarPatch = bytecodePatch(
    name = "Fix sidebar",
    description = "Fixes the subreddit sidebar failing to load.",
    default = true,
) {
    compatibleWith(*RelayCompatible)

    execute {
        // Both counters are display-only, so a missing one should read as zero rather than
        // discard a sidebar that was fetched successfully.
        sideBarResponseFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow(jsonGetIntFilter).forEach { index ->
                val instruction = getInstruction(index) as FiveRegisterInstruction

                replaceInstruction(
                    index,
                    "invoke-virtual { v${instruction.registerC}, v${instruction.registerD} }, " +
                        "Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I",
                )
            }
        }
    }
}
