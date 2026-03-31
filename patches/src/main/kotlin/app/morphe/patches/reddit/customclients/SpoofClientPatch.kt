/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption

/**
 * Base class for patches that spoof the Reddit client.
 *
 * @param block The patch block. It is called with the client ID option and redirect URI option.
 */
fun spoofClientPatch(
    name: String,
    description: String,
    block: BytecodePatchBuilder.(
        clientIdOption: Option<String>,
        redirectUriOption: Option<String>,
        userAgentOption: Option<String>
    ) -> Unit,
) = bytecodePatch(
    name = name,
    description = description,
    default = true
) {
    val clientIdOption = stringOption(
        "client-id",
        "yH0aTnJEt6qUgGn835B4vg",
        null,
        "OAuth client ID",
        "The Reddit OAuth client ID. Refer to Patcheddit documentation for more " +
                "information on what to put here.",
        true,
        validator = { value ->
            if (value.isNullOrBlank()) {
                return@stringOption false
            }
            if (!value.matches(Regex("^[a-zA-Z0-9]{22}\$"))) {
                return@stringOption false
            }
            true
        }
    )
    val redirectUriOption = stringOption(
        "redirect-uri",
        "redreader://rr_oauth_redir",
        null,
        "Redirect URI",
        "The Reddit OAuth redirect URI. Refer to Patcheddit documentation for more " +
                "information on what to put here. Default value is RedReader's redirect URI.",
        true,
    )
    val userAgentOption = stringOption(
        "user-agent",
        "org.quantumbadger.redreader/1.25.1",
        null,
        "User agent",
        "The app's user agent. Refer to Patcheddit documentation for more information " +
                "on what to put here. Default value is RedReader's user agent.",
        true
    )

    block(
        clientIdOption, redirectUriOption, userAgentOption
    )
}

fun spoofClientPatch(
    block: BytecodePatchBuilder.(
        clientIdOption: Option<String>,
        redirectUriOption: Option<String>,
        userAgentOption: Option<String>
    ) -> Unit,
) = spoofClientPatch(
    name = "Spoof client",
    description = "Restores functionality of the app by using custom client ID.",
    block = block,
)
