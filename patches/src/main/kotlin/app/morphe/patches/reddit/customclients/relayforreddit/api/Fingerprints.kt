package app.morphe.patches.reddit.customclients.relayforreddit.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal fun baseClientIdFingerprint(str: String) = Fingerprint(
    filters = listOf(string("dj-xCIZQYiLbEg"), string(str))
)


internal val getLoggedInBearerTokenFingerprint = baseClientIdFingerprint("authorization_code")

internal val getLoggedOutBearerTokenFingerprint = baseClientIdFingerprint("https://oauth.reddit.com/grants/installed_client")

internal val getRefreshTokenFingerprint = baseClientIdFingerprint("refresh_token")

internal val loginActivityClientIdFingerprint = baseClientIdFingerprint("&duration=permanent")

internal val redditCheckDisableAPIFingerprint = Fingerprint(
    filters = listOf(
        OpcodeFilter(Opcode.IF_EQZ),
        string("Reddit Disabled")
    )
)

internal val setRemoteConfigFingerprint = Fingerprint(
    parameters = listOf("Lcom/google/firebase/remoteconfig/FirebaseRemoteConfig;"),
    filters = listOf(string("reddit_oauth_url"))
)