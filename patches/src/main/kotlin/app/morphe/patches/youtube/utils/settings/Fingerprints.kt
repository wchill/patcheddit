package app.morphe.patches.youtube.utils.settings

import app.morphe.patches.youtube.utils.resourceid.appearance
import app.morphe.util.fingerprint.legacyFingerprint
import app.morphe.util.or
import com.android.tools.smali.dexlib2.AccessFlags

internal val clientContextBodyBuilderFingerprint = legacyFingerprint(
    name = "clientContextBodyBuilderFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf("L"),
    strings = listOf(
        "country",
        "\u200e\u200f\u200e\u200e",
    )
)

internal val settingsFragmentStylePrimaryFingerprint = legacyFingerprint(
    name = "settingsFragmentStylePrimaryFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/util/List;",
        "Landroidx/preference/Preference;",
        "Lj${'$'}/util/Optional;",
        "Lj${'$'}/util/Optional;",
    ),
)

internal val settingsFragmentStyleSecondaryFingerprint = legacyFingerprint(
    name = "settingsFragmentStyleSecondaryFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf(
        "Ljava/util/List;",
        "Landroidx/preference/Preference;",
    ),
)

internal val themeSetterSystemFingerprint = legacyFingerprint(
    name = "themeSetterSystemFingerprint",
    returnType = "L",
    literals = listOf(appearance),
)

internal val baseHostActivityOnCreateFingerprint = legacyFingerprint(
    name = "baseHostActivityOnCreateFingerprint",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    customFingerprint = { method, classDef ->
        classDef.endsWith("/BaseHostActivity;") && method.name == "onCreate"
    }
)

internal val youtubeHostActivityOnCreateFingerprint = legacyFingerprint(
    name = "youtubeHostActivityOnCreateFingerprint",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    customFingerprint = { method, classDef ->
        classDef.endsWith("/YouTubeHostActivity;") && method.name == "onCreate"
    }
)

internal val licenseMenuActivityOnCreateFingerprint = legacyFingerprint(
    name = "licenseMenuActivityOnCreateFingerprint",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    customFingerprint = { method, classDef ->
        classDef.endsWith("/LicenseMenuActivity;") && method.name == "onCreate"
    }
)

internal val userInterfaceThemeEnumFingerprint = legacyFingerprint(
    name = "userInterfaceThemeEnumFingerprint",
    returnType = "V",
    strings = listOf(
        "USER_INTERFACE_THEME_UNKNOWN",
        "USER_INTERFACE_THEME_LIGHT",
        "USER_INTERFACE_THEME_DARK",
    ),
    customFingerprint = { method, _ ->
        method.name == "<clinit>"
    }
)