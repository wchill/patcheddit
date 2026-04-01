/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.relay.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal val clientIdFingerprint = Fingerprint(
    strings = listOf("dj-xCIZQYiLbEg")
)

internal val redditCheckDisableAPIFingerprint = Fingerprint(
    strings = listOf("Reddit Disabled"),
    filters = listOf(
        OpcodeFilter(Opcode.IF_EQZ)
    )
)

internal val setRemoteConfigFingerprint = Fingerprint(
    parameters = listOf("Lcom/google/firebase/remoteconfig/FirebaseRemoteConfig;"),
    strings = listOf("reddit_oauth_url")
)

internal val redirectUriFingerprint = Fingerprint(
    strings = listOf("dbrady://relay")
)

// Lreddit/news/oauth/dagger/modules/NetworkModule;
internal val networkModuleUserAgentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Lokhttp3/OkHttpClient;",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.NEW_INSTANCE,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT_OBJECT
    )
)