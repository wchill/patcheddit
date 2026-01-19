package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode


internal val getDefaultClientIdFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultClientId" && classDef.type == EXTENSION_CLASS_NAME
    }
)

internal val getDefaultRedirectUriFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultRedirectUri" && classDef.type == EXTENSION_CLASS_NAME
    }
)

internal val getDefaultUserAgentFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultUserAgent" && classDef.type == EXTENSION_CLASS_NAME
    }
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
    filters = listOf(
        methodCall(smali = "Landroid/widget/ImageButton;->setLayoutParams(Landroid/view/ViewGroup\$LayoutParams;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
        methodCall(smali = "Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V"),
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "Tutorial.java"
    }
)

internal val tutorialSaveFingerprint = Fingerprint(
    strings = listOf("redditClientOverride"),
    filters = listOf(
        OpcodeFilter(Opcode.SGET_OBJECT),
        OpcodeFilter(Opcode.INVOKE_INTERFACE),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        string("Tutorial"),
        string("S")
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "Tutorial.java"
    }
)

internal val settingsFragmentShowClientIdFingerprint = Fingerprint(
    filters = listOf(
        methodCall(smali = "Landroidx/appcompat/app/AppCompatActivity;->findViewById(I)Landroid/view/View;"),
        OpcodeFilter(Opcode.MOVE_RESULT_OBJECT),
        checkCast("Landroid/widget/TextView;"),
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("redditClientOverride"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    ),
    custom = { _, classDef ->
        classDef.sourceFile == "SettingsGeneralFragment.java"
    }
)

internal val showClientIdDialogDefaultStringFingerprint = Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("redditClientOverride"),
        string(""),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    ),
    custom = { method, classDef ->
        classDef.sourceFile == "SettingsGeneralFragment.java" && method.name == "showClientIDDialog"
    }
)

internal val showClientIdDialogSetupLayoutFingerprint = Fingerprint(
    filters = listOf(
        methodCall(definingClass = "LinearLayout;", name = "addView"),
        fieldAccess(definingClass = "SettingsGeneralFragment;", name = "context"),
        OpcodeFilter(Opcode.CONST),
        methodCall(definingClass = "Landroidx/appcompat/app/AppCompatActivity;", name = "findViewById")
    ),
    custom = { method, classDef ->
        classDef.sourceFile == "SettingsGeneralFragment.java" && method.name == "showClientIDDialog"
    }
)

internal val showClientIdDialogLoadDefaultClientIdFingerprint = Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string(""),
        string("redditClientOverride"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getString")
    ),
    custom = { method, classDef ->
        classDef.sourceFile == "SettingsGeneralFragment.java" && method.name != "showClientIDDialog" && method.name.contains("showClientIDDialog")
    }
)

// Anonymous lambda (onClick handler) inside showClientIDDialog that saves the client ID override.
internal val showClientIdDialogSaveFingerprint = Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string(""),
        string("redditClientOverride")
    ),
    custom = { method, classDef ->
        classDef.sourceFile == "SettingsGeneralFragment.java" && method.name != "showClientIDDialog" && method.name.contains("showClientIDDialog")
    }
)

internal val jrawOnUserChallengeFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "onUserChallenge" && classDef.type == "Lnet/dean/jraw/http/oauth/OAuthHelper;"
    }
)

internal val jrawNewUrlFingerprint = Fingerprint(
    custom = { method, classDef ->
        if (!classDef.endsWith("JrawUtils;")) return@Fingerprint false
        method.name == "newUrl"
    }
)
