/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory

internal const val INSTALL_NEW_CLIENT_METHOD =
    $$"install(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient;"
internal const val CREATE_NEW_CLIENT_METHOD = "createClient()Lokhttp3/OkHttpClient;"
internal val redgifsSetting = MorphePatchSetting("morphe_patch_fix_redgifs_api")

fun fixRedgifsApiPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = morpheBytecodePatch(
    name = "Fix Redgifs API",
    description = "Fetches a fresh Redgifs API token so Redgifs videos load.",
    default = true,
    extensionPatch = extensionPatch,
    category = PatchCategory.MEDIA,
) { setting ->
    dependsOn(
        extensionPatch,
        bytecodePatch {
            execute {
                setting.returnEnabled(Fingerprint(
                    definingClass = "Lapp/morphe/extension/shared/fixes/redgifs/BaseFixRedgifsApiPatch;",
                    name = "isPatchIncluded",
                ).method)
            }
        }
    )

    block()
}
