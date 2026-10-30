/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/redditisfun/FixRedgifsApiPatch;"

val fixRedgifsApiPatch = bytecodePatch(
    name = "Fix Redgifs API",
    description = "Fix RedGifs videos and gifs not loading.",
    default = true,
) {
    dependsOn(ExtensionPatches.RIF)
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        // RIF requests a token from the removed /v2/oauth/client endpoint. Return a valid
        // RedGifs temporary token from the extension instead.
        RedgifsTokenFingerprint.method.addInstructions(
            0,
            """
                invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->getRedgifsToken()Ljava/lang/String;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
