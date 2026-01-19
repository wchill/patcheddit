package app.morphe.patches.reddit.customclients.continuum.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val getDefaultUserAgentFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultUserAgent" && classDef.type == EXTENSION_CLASS_NAME
    }
)

internal val getDefaultRedirectUriFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultRedirectUri" && classDef.type == EXTENSION_CLASS_NAME
    }
)

internal val getDefaultClientIdFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultClientId" && classDef.type == EXTENSION_CLASS_NAME
    }
)

internal val apiKeysOnCreatePreferencesFingerprint = Fingerprint(
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_DIRECT,
        Opcode.RETURN_VOID
    ),
    custom = { method, classDef ->
        method.name == "onCreatePreferences" && classDef.sourceFile == "APIKeysPreferenceFragment.java"
    }
)
