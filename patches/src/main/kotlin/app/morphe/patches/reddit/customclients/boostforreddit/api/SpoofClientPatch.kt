/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.spoofClientSetting
import com.android.tools.smali.dexlib2.formatter.DexFormatter

const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/http/HttpUtils;"

val spoofClientPatch = spoofClientPatch(
    name = "Spoof client",
    description = "Sets default Reddit API credentials that can be overridden in Boost's API settings.",
    extensionPatch = ExtensionPatches.Boost,
) { clientIdOption, redirectUriOption, userAgentOption ->
    dependsOn(ExtensionPatches.Boost)
    compatibleWith(*AppCompatibility.Boost)

    val clientId by clientIdOption
    val redirectUri by redirectUriOption
    val userAgent by userAgentOption

    execute {
        val defaults = mapOf(
            "pref_dev_reddit_client_id" to clientId!!,
            "pref_dev_reddit_redirect_url" to redirectUri!!,
            "pref_dev_reddit_user_agent" to userAgent!!,
        )
        // Keep native normalization and saved values shared by API setup, login and token refresh.
        GetApiPreferenceFingerprint.wrapHook(
            setting = spoofClientSetting,
            after = buildString {
                appendLine("if-nez v0, :morphe_api_preference_return")
                defaults.entries.forEachIndexed { index, (key, value) ->
                    appendLine(
                        """
                            const-string v1, "$key"
                            invoke-virtual {v1, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                            move-result v1
                            if-eqz v1, :morphe_api_preference_next_$index
                            const-string v0, ${DexFormatter.INSTANCE.getQuotedString(value)}
                            goto :morphe_api_preference_return
                            :morphe_api_preference_next_$index
                        """,
                    )
                }
                appendLine(":morphe_api_preference_return")
            },
        )

        // Keep support for custom redirect URI schemes without overriding the configured URI.
        JrawNewUrlFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static       { p0 }, $JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR->createUrl(Ljava/lang/String;)Ljava/net/URL;
                    move-result-object  v0
                    return-object v0
                """
            )
        }
    }
}
