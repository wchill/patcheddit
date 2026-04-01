package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val userEndpointFingerprints = listOf(
    Fingerprint(
        strings = listOf("https://oauth.reddit.com/u/"),
        custom = { _, classDef ->
            classDef.sourceFile in setOf(
                "OAuthFriendRequest.java",
                "OAuthUnfriendRequest.java",
            )
        }
    ),
    Fingerprint(
        strings = listOf("u/"),
        custom = { _, classDef ->
            classDef.sourceFile in setOf(
                "OAuthUserInfoRequest.java",
                "OAuthUserIdRequest.java",
                "OAuthSubredditInfoRequest.java",
            )
        }
    )
)
