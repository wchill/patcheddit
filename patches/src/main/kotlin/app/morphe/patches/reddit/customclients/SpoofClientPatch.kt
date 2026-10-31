/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.all.misc.PatchCategory
import java.util.Base64

private const val REDREADER_CLIENT_ID_BASE64 = "eUgwYVRuSkV0NnFVZ0duODM1QjR2Zw=="
private const val REDREADER_REDIRECT_URI = "redreader://rr_oauth_redir"
private const val REDREADER_USER_AGENT = "org.quantumbadger.redreader/1.27"

internal fun applyDefaultClientId(option: Option<String>) {
    if (option.value.isNullOrBlank()) {
        option.value = Base64.getDecoder().decode(REDREADER_CLIENT_ID_BASE64).toString(Charsets.UTF_8)
    }
}

/**
 * Base class for patches that spoof the Reddit client.
 *
 * @param block The patch block. It receives client ID, redirect URI and user agent options.
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
    category(PatchCategory.LOGIN)
    val clientIdOption = stringOption(
        "client-id",
        null,
        null,
        "OAuth client ID",
        "The Reddit OAuth client ID. Leave empty to use the RedReader 1.27 default. " +
                "Custom values must contain exactly 22 letters, digits, underscores or hyphens.",
        false,
        validator = { value ->
            value.isNullOrBlank() || value.matches(Regex("^[\\w-]{22}$"))
        }
    )
    val redirectUriOption = stringOption(
        "redirect-uri",
        REDREADER_REDIRECT_URI,
        null,
        "Redirect URI",
        "The Reddit OAuth redirect URI. Refer to the README for more " +
                "information on what to put here. Default value is RedReader 1.27's redirect URI.",
        true,
    )
    val userAgentOption = stringOption(
        "user-agent",
        REDREADER_USER_AGENT,
        null,
        "User agent",
        "The app's user agent. Refer to the README for more information " +
                "on what to put here. Default value is RedReader 1.27's user agent.",
        true
    )

    // Resolve before client-specific dependencies without exposing the ID in option metadata.
    dependsOn(bytecodePatch(default = false) {
        execute {
            applyDefaultClientId(clientIdOption)
        }
    })

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
