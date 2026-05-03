/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/http/reddit/RedditFixAudioInDownloadsInterceptor;"

@Suppress("unused")
val fixAudioMissingInDownloadsPatch = bytecodePatch(
    name = "Fix missing audio in video downloads",
    description = "Fixes audio missing in videos downloaded from v.redd.it.",
    default = true
) {
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests)
    compatibleWith(*AppCompatibility.Boost)

    execute {
        DownloadAudioFingerprint.method.apply {
            // Just need to set an enable flag since HTTP interceptor will already be enabled
            addInstructions(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->enable()V
                    """
            )
        }
    }
}
