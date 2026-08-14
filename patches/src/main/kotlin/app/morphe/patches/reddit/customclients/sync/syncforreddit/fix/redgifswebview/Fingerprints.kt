/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifswebview

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// LinkHelper.M(): decides whether to open a Redgifs link in the native media viewer or
// externally in a browser, based on the app's "open internally" setting.
internal val openRedgifsLinkFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "[Lr0/d;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Z",
    ),
    strings = listOf("Opening RedGifs link internally: "),
)

// SettingsSingleton.p(Context): builds a Settings instance by reading each known boolean
// (and other) preference individually via SharedPreferences.getBoolean(key, default) --
// NOT via Gson/reflection. A field added to Settings without a corresponding read here
// never actually gets populated from SharedPreferences; it just stays at its Java default.
internal val loadSettingsFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton\$Settings;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("doh"),
)
