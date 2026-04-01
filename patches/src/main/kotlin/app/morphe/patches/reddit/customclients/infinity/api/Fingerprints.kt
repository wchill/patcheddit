/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
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

internal val clientIdFingerprint = Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = "Lml/docilealligator/infinityforreddit/utils/APIUtils;",
            name = "getId",
            parameters = emptyList(),
            returnType = "Ljava/lang/String;"
        )
    )
)

internal fun userAgentFingerprint(versionName: String) = Fingerprint(
    strings = listOf("android:ml.docilealligator.infinityforreddit:$versionName (by /u/Hostilenemy)")
)

internal val redirectUriFingerprint = Fingerprint(
    definingClass = "Lml/docilealligator",
    strings = listOf("infinity://localhost")
)

internal val settingsActivityOnBackStackChangedFingerprint = Fingerprint(
    definingClass = "Lml/docilealligator/infinityforreddit/activities/SettingsActivity;",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INSTANCE_OF,
        Opcode.IF_EQZ,
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.GOTO
    )
)

internal val billingClientOnServiceConnectedFingerprint = Fingerprint(
    strings = listOf("Billing service connected")
)

internal val infinityStartSubscriptionActivityFingerprint = Fingerprint(
    definingClass = "Lml/docilealligator/infinityforreddit/Infinity;",
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Intent;",
            name = "<init>",
            opcode = Opcode.INVOKE_DIRECT
        ),
        literal(0x10008000)
    )
)
