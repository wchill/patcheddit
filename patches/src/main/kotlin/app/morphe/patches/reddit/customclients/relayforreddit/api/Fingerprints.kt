package app.morphe.patches.reddit.customclients.relayforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
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