/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patches.reddit.customclients.continuum.api.EXTENSION_CLASS_NAME
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

internal val tutorialLoadDefaultFingerprint = Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("redditClientOverride"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "Tutorial.java"
    }
)

internal val tutorialFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/Activities/Tutorial\$Personalize;",
    filters = listOf(
        methodCall(smali = "Landroid/widget/ImageButton;->setLayoutParams(Landroid/view/ViewGroup\$LayoutParams;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
    )
)

internal val tutorialSaveFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/Activities/Tutorial\$Personalize;",
    strings = listOf("redditClientOverride"),
    filters = listOf(
        OpcodeFilter(Opcode.SGET_OBJECT),
        OpcodeFilter(Opcode.INVOKE_INTERFACE),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        string("Tutorial"),
        string("S")
    )
)

internal val settingsFragmentShowClientIdFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment",
    filters = listOf(
        methodCall(smali = "Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        checkCast("Landroid/widget/TextView;"),
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("redditClientOverride"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    )
)

internal val showClientIdDialogDefaultStringFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment",
    name = "showClientIdDialog",
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("redditClientOverride"),
        string(""),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    )
)

internal val showClientIdDialogSetupLayoutFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment",
    name = "showClientIdDialog",
    filters = listOf(
        methodCall(definingClass = "LinearLayout;", name = "addView"),
        fieldAccess(definingClass = "SettingsGeneralFragment;", name = "context"),
        OpcodeFilter(Opcode.CONST),
        methodCall(definingClass = "Landroidx/appcompat/app/AppCompatActivity;", name = "findViewById")
    )
)

internal val showClientIdDialogLoadDefaultClientIdFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment",
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string(""),
        string("redditClientOverride"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    ),
    custom = { method, _ ->
        method.name != "showClientIDDialog" && method.name.contains("showClientIDDialog")
    }
)

// Anonymous lambda (onClick handler) inside showClientIDDialog that saves the client ID override.
internal val showClientIdDialogSaveFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment",
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string(""),
        string("redditClientOverride")
    ),
    custom = { method, _ ->
        method.name != "showClientIDDialog" && method.name.contains("showClientIDDialog")
    }
)

internal val jrawOnUserChallengeFingerprint = Fingerprint(
    definingClass = "Lnet/dean/jraw/http/oauth/OAuthHelper;",
    name = "onUserChallenge"
)

internal val jrawNewUrlFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("JrawUtils;")) return@Fingerprint false
        method.name == "newUrl"
    }
)
