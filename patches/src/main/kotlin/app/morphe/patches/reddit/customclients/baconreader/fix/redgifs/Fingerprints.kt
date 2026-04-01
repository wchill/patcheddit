/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.redgifs

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val getOkHttpClientFingerprint = Fingerprint(
    definingClass = "Lcom/onelouder/baconreader/media/gfycat/RedGifsManager;",
    name = "getOkhttpClient",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "Lokhttp3/OkHttpClient;",
    parameters = listOf(),
)
