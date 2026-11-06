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
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.all.misc.PatchCategory

internal const val INSTALL_NEW_CLIENT_METHOD =
    $$"install(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient;"
internal const val CREATE_NEW_CLIENT_METHOD = "createClient()Lokhttp3/OkHttpClient;"

fun fixRedgifsApiPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(
    name = "Fix Redgifs API",
    description = "Fetches a fresh Redgifs API token so Redgifs videos load.",
    default = true
) {
    category(PatchCategory.MEDIA)
    dependsOn(
        extensionPatch,
        bytecodePatch {
            execute {
                Fingerprint(
                    definingClass = "Lapp/morphe/extension/shared/fixes/redgifs/BaseFixRedgifsApiPatch;",
                    name = "isPatchIncluded",
                ).method.returnEarly(true)
            }
        }
    )

    block()
}
