package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.util.returnEarly

val spoofClientPatch = spoofClientPatch(redirectUri = "http://www.ccrama.me") { clientIdOption ->
    compatibleWith("me.ccrama.redditslide")

    val clientId by clientIdOption

    execute {
        getClientIdFingerprint.method.returnEarly(clientId!!)
    }
}
