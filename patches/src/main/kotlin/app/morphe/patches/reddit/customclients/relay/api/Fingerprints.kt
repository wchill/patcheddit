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

internal fun baseClientIdFingerprint(str: String) = Fingerprint(
    strings = listOf("dj-xCIZQYiLbEg", str)
)


internal val getLoggedInBearerTokenFingerprint = baseClientIdFingerprint("authorization_code")

internal val getLoggedOutBearerTokenFingerprint = baseClientIdFingerprint("https://oauth.reddit.com/grants/installed_client")

internal val getRefreshTokenFingerprint = baseClientIdFingerprint("refresh_token")

internal val loginActivityClientIdFingerprint = baseClientIdFingerprint("&duration=permanent")

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

// Lreddit/news/oauth/LoginActivity;->onCreate
internal val loginActivityRedirectUriFingerprint = Fingerprint(
    definingClass = "Lreddit/news/oauth/LoginActivity;",
    strings = listOf("dbrady://relay"),
)

// Lreddit/news/oauth/LoginActivity$1;->shouldOverrideUrlLoading
internal val shouldOverrideUrlLoadingRedirectUriFingerprint = Fingerprint(
    strings = listOf("login url: ", "dbrady://relay")
)

internal val redditAccountManagerRedirectUriFingerprint = Fingerprint(
    definingClass = "Lreddit/news/oauth/RedditAccountManager;",
    strings = listOf("dbrady://relay"),
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