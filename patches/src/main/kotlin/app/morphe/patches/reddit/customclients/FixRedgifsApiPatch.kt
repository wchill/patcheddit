/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch

const val INSTALL_NEW_CLIENT_METHOD = "install(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient;"
const val CREATE_NEW_CLIENT_METHOD = "createClient()Lokhttp3/OkHttpClient;"

fun fixRedgifsApiPatch(
    extensionPatch: Patch<*>,
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(
    name = "Fix Redgifs API",
    default = true
) {
    dependsOn(extensionPatch)

    block()
}
