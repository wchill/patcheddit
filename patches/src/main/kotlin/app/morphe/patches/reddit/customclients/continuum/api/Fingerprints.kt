/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.continuum.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val getDefaultUserAgentFingerprint = Fingerprint(
    definingClass = EXTENSION_CLASS_NAME,
    name = "getDefaultUserAgent"
)

internal val getDefaultRedirectUriFingerprint = Fingerprint(
    definingClass = EXTENSION_CLASS_NAME,
    name = "getDefaultRedirectUri"
)

internal val getDefaultClientIdFingerprint = Fingerprint(
    definingClass = EXTENSION_CLASS_NAME,
    name = "getDefaultClientId"
)

internal fun userAgentFingerprint(versionName: String) = Fingerprint(
    strings = listOf("android:org.cygnusx1.continuum:$versionName (by /u/edgan)")
)

internal val redirectUriFingerprint = Fingerprint(
    definingClass = "Lml/docilealligator",
    strings = listOf("continuum://localhost")
)

internal val apiKeysOnCreatePreferencesFingerprint = Fingerprint(
    definingClass = "Lml/docilealligator/infinityforreddit/settings/APIKeysPreferenceFragment",
    name = "onCreatePreferences",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_DIRECT,
        Opcode.RETURN_VOID
    )
)
