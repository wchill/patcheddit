package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption

/**
 * Base class for patches that spoof the Reddit client.
 *
 * @param redirectUri The redirect URI of the Reddit OAuth client.
 * @param block The patch block. It is called with the client ID option.
 */
fun spoofClientPatch(
    redirectUri: String,
    block: BytecodePatchBuilder.(Option<String>) -> Unit = {},
) = bytecodePatch(
    name = "Spoof client",
    description = "Restores functionality of the app by using custom client ID.",
) {
    block(
        stringOption(
            "client-id",
            null,
            null,
            "OAuth client ID",
            "The Reddit OAuth client ID. " +
                "You can get your client ID from https://www.reddit.com/prefs/apps. " +
                "The application type has to be \"Installed app\" " +
                "and the redirect URI has to be set to \"$redirectUri\".",
            true,
        ),
    )
}

/**
 * Base class for patches that spoof the Reddit client.
 *
 * @param block The patch block. It is called with the client ID option and redirect URI option.
 */
fun spoofClientPatch(
    block: BytecodePatchBuilder.(
        clientIdOption: Option<String>,
        redirectUriOption: Option<String>,
    ) -> Unit,
) = bytecodePatch(
    name = "Spoof client",
    description = "Restores functionality of the app by using custom client ID.",
) {
    block(
        stringOption(
            "client-id",
            null,
            null,
            "OAuth client ID",
            "The Reddit OAuth client ID. " +
                "You can get your client ID from https://www.reddit.com/prefs/apps. " +
                "The application type has to be \"Installed app\" and the redirect " +
                "URI has to match the value provided for the \"Redirect URI\" option.",
            true,
        ),
        stringOption(
            "redirect-uri",
            "http://127.0.0.1:8080",
            null,
            "Redirect URI",
            "The Reddit OAuth redirect URI. Should be a valid URI.",
            true,
        ),
    )
}

/**
 * Base class for patches that spoof the Reddit user agent.
 *
 * @param block The patch block. It is called with the user agent option.
 */
fun spoofUserAgentPatch(
    block: BytecodePatchBuilder.(
        userAgentOption: Option<String>,
    ) -> Unit,
) = bytecodePatch(
    name = "Spoof user agent",
    description = "Restores functionality of the app by using custom user agent.",
) {
    block(
        stringOption(
        "user-agent",
        null,
        null,
        "User agent",
        "The app's user agent. User agent should be in the format " +
                "\"<platform>:<app id>:<version> (by /u/<username>)\".",
        true,
        )
    )
}
