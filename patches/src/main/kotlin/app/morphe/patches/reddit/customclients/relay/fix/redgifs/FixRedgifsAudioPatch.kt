package app.morphe.patches.reddit.customclients.relay.fix.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.relay.RelayCompatible
import app.morphe.patches.reddit.customclients.relay.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/RedgifsUrlResolver;"

private const val REWRITE_METHOD = "rewriteRedgifsMedia(" +
    "Ljava/lang/Object;" +
    "Ljava/util/List;" +
    ")V"

/**
 * Redgifs videos play with no sound, because Relay swaps in Reddit's own preview video whenever
 * the `redgifs_api_enabled` remote config flag is false - which it always is, since it defaults
 * to false and the Spoof client patch stops remote config from loading. Reddit's preview has no
 * audio track, so there is nothing to unmute.
 *
 * The post's media url is rewritten to the real Redgifs media url after Relay has built its
 * previews, in the pipeline stage that runs off the main thread.
 */
@Suppress("unused")
val fixRedgifsAudioPatch = bytecodePatch(
    name = "Fix Redgifs audio",
    description = "Fixes Redgifs videos playing without sound.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*RelayCompatible)

    execute {
        // p1 is the post's url, p3 the previews Relay built. No spare register is needed.
        postProcessMediaPreviewsFingerprint.method.addInstruction(
            0,
            "invoke-static { p1, p3 }, $EXTENSION_CLASS_DESCRIPTOR->$REWRITE_METHOD",
        )
    }
}
