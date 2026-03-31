/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.slide.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
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

internal fun userAgentFingerprints(versionName: String) = listOf(
    Fingerprint(
        strings = listOf("me.edgan.redditslide/$versionName")
    ),
    Fingerprint(
        strings = listOf("android:me.edgan.RedditSlide:v$versionName")
    )
)

internal val redirectUriFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/",
    strings = listOf("http://www.ccrama.me"),
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

internal val showClientIdDialogSetupLayoutFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment;",
    name = "showClientIDDialog",
    filters = listOf(
        methodCall(definingClass = "Landroid/widget/LinearLayout;", name = "addView"),
        fieldAccess(definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment;", name = "context"),
        OpcodeFilter(Opcode.CONST),
        methodCall(definingClass = "Landroidx/appcompat/app/AppCompatActivity;", name = "findViewById")
    )
)

internal val loadClientIdFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/",
    filters = listOf(
        string("redditClientOverride"),
        methodCall(
            definingClass = "Landroid/content/SharedPreferences;",
            name = "getString",
            // showClientIDDialog has an empty string for the default, so set max distance to 1
            location = InstructionLocation.MatchAfterWithin(1)
        )
    )
)

// Anonymous lambda (onClick handler) inside showClientIDDialog that saves the client ID override.
internal val showClientIdDialogSaveFingerprint = Fingerprint(
    definingClass = "Lme/edgan/redditslide/ui/settings/SettingsGeneralFragment;",
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.SYNTHETIC),
    filters = listOf(
        fieldAccess(definingClass = "Lme/edgan/redditslide/SettingValues;", name = "prefs"),
        string("", location = InstructionLocation.MatchAfterImmediately()),
        string("redditClientOverride", location = InstructionLocation.MatchAfterImmediately())
    )
)

internal val jrawOnUserChallengeFingerprint = Fingerprint(
    definingClass = "Lnet/dean/jraw/http/oauth/OAuthHelper;",
    name = "onUserChallenge"
)

internal val jrawNewUrlFingerprint = Fingerprint(
    definingClass = "Lnet/dean/jraw/util/JrawUtils;",
    name = "newUrl"
)
