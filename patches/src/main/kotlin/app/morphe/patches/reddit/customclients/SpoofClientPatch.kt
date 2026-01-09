package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.PatchException
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
        userAgentOption: Option<String>
    ) -> Unit,
) = bytecodePatch(
    name = "Spoof client",
    description = "Restores functionality of the app by using custom client ID.",
) {
    val clientIdOption = stringOption(
        "client-id",
        null,
        null,
        "OAuth client ID",
        "The Reddit OAuth client ID. Refer to Patcheddit documentation for " +
                "more information on what to put here. An empty value keeps the app's " +
                "existing client ID.",
        false,
    )
    val redirectUriOption = stringOption(
        "redirect-uri",
        null,
        null,
        "Redirect URI",
        "The Reddit OAuth redirect URI. Refer to Patcheddit documentation for " +
                "more information on what to put here. An empty value keeps the app's " +
                "existing redirect URI.",
        false,
    )
    val userAgentOption = stringOption(
        "user-agent",
        null,
        null,
        "User agent",
        "The app's user agent. Refer to Patcheddit documentation for " +
                "more information on what to put here. An empty value keeps the app's existing " +
                "user agent.",
        false
    )

    block(
        clientIdOption, redirectUriOption, userAgentOption
    )
}
