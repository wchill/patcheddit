/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifswebview

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val SETTINGS_FIELD_NAME = "redgifsWebview"
private const val HELPER_CLASS = "Lapp/morphe/extension/syncforreddit/RedgifsWebViewHelper;"

// Adds a new boolean field to SettingsSingleton$Settings, the app's settings holder --
// AND makes it actually load from SharedPreferences. Settings is NOT Gson/reflection
// backed: SettingsSingleton.p(Context) populates a new Settings instance by calling
// SharedPreferences.getBoolean(key, default) individually, once per known field. A field
// that isn't also read there never gets populated -- it silently stays at its Java
// default (false) forever, regardless of what the user toggles in the UI. (Learned this
// the hard way: the checkbox worked and toggled fine, but had no runtime effect at all,
// because this half of the wiring was missing.)
internal val addRedgifsWebviewSettingField = bytecodePatch {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        classDefForEach { classDef ->
            if (classDef.sourceFile != "SettingsSingleton.java" || !classDef.type.endsWith("\$Settings;")) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).fields.add(
                ImmutableField(
                    classDef.type,
                    SETTINGS_FIELD_NAME,
                    "Z",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    null,
                ).toMutable()
            )
        }

        loadSettingsFingerprint.method.apply {
            val dohIndex = indexOfFirstInstructionOrThrow {
                val reference = (this as? ReferenceInstruction)?.reference as? FieldReference
                reference?.name == "doh"
            }
            val settingsClass = (getInstruction<ReferenceInstruction>(dohIndex).reference as FieldReference).definingClass
            val settingsReg = getInstruction<TwoRegisterInstruction>(dohIndex).registerB
            // settingsReg is the live Settings instance being built; the SharedPreferences
            // instance is loaded once at the top of the method and reused for every field's
            // read via the same register (v0) the "doh" read (and every other field) uses here.
            addInstructions(
                dohIndex + 1,
                """
                const-string v4, "$SETTINGS_FIELD_NAME"
                const/4 v5, 0x0
                invoke-interface { v0, v4, v5 }, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v4
                iput-boolean v4, v$settingsReg, $settingsClass->$SETTINGS_FIELD_NAME:Z
                """
            )
        }
    }
}

// Adds a checkbox preference wired to the new field, in the same "Other" security category
// as the existing "DNS over HTTPS" checkbox.
internal val addRedgifsWebviewSettingUi = resourcePatch {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        document("res/xml/cat_security.xml").use { document ->
            val preference = document.createElement(
                "com.laurencedawson.reddit_sync.ui.preferences.defaults.SyncCheckBoxPreference"
            ).apply {
                setAttribute("android:icon", "@drawable/outline_open_in_browser_24")
                setAttribute("android:title", "Open Redgifs links in browser view")
                setAttribute("android:key", SETTINGS_FIELD_NAME)
                setAttribute(
                    "android:summary",
                    "If a Redgifs video fails to load in the native player" +
                        " (\"Error connecting to Redgifs\"), automatically open it in an" +
                        " in-app browser view as a fallback instead of showing the error."
                )
                setAttribute("android:defaultValue", "false")
            }

            val dohPreference = document.getElementsByTagName(
                "com.laurencedawson.reddit_sync.ui.preferences.defaults.SyncCheckBoxPreference"
            ).let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .first { it.getAttribute("android:key") == "doh" }
            }

            dohPreference.parentNode.insertBefore(preference, dohPreference.nextSibling)
        }
    }
}

@Suppress("unused")
val openRedgifsInWebView = bytecodePatch(
    name = "Open Redgifs links in WebView",
    description = "Adds a setting (Settings > Security > \"Open Redgifs links in browser view\")" +
        " to fall back to an in-app WebView when Redgifs videos fail to load in the native" +
        " player (\"Error connecting to Redgifs\"). Off by default. The native/API player is" +
        " always tried first; the WebView only opens as a fallback on failure. Redgifs pages" +
        " in the WebView also auto-accept the cookie consent banner and keep it accepted" +
        " between opens.",
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    dependsOn(addRedgifsWebviewSettingField, addRedgifsWebviewSettingUi, ExtensionPatches.Sync)

    execute {
        // Hook the failure listener of the Redgifs playback flow instead of the link
        // opener: the native/API player always runs first, and only an actual failure
        // (dead endpoint, 404 on a malformed ID, bot detection, ...) opens the WebView.
        // On fallback we finish the player and skip its error handling entirely:
        // no error flash before the WebView appears, and Back from the WebView returns
        // to the page that opened the player instead of a dead error screen.
        redgifsErrorListenerFingerprint.method.apply {
            addInstructions(
                0,
                """
                invoke-static { }, Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton;->d()Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton;
                move-result-object v0
                invoke-virtual { v0 }, Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton;->j()Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton${'$'}Settings;
                move-result-object v0
                iget-boolean v0, v0, Lcom/laurencedawson/reddit_sync/singleton/SettingsSingleton${'$'}Settings;->$SETTINGS_FIELD_NAME:Z
                if-eqz v0, :noFallback
                iget-object v0, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment${'$'}g0;->b:Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment;
                invoke-virtual { v0 }, Landroidx/fragment/app/Fragment;->B0()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                if-eqz v0, :noFallback
                iget-object v1, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment${'$'}g0;->a:Ljava/lang/String;
                invoke-static { v0, v1 }, Lcom/laurencedawson/reddit_sync/ui/activities/WebViewActivity;->K0(Landroid/content/Context;Ljava/lang/String;)Landroid/content/Intent;
                move-result-object v1
                invoke-virtual { v0, v1 }, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                invoke-virtual { v0 }, Landroid/app/Activity;->finish()V
                return-void
                :noFallback
                """
            )
        }

        // Auto-accept the CookieYes consent banner on redgifs pages loaded in the
        // in-app WebView (all logic lives in the extension helper; no extra
        // registers needed here since onPageFinished's params are the arguments).
        webViewClientOnPageFinishedFingerprint.method.apply {
            addInstructions(
                0,
                """
                invoke-static { p1, p2 }, $HELPER_CLASS->onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
                """
            )
        }

        // Sync wipes all cookies on every fresh WebView open. Replace the wipe
        // with a helper call that skips it for redgifs URLs, so the consent the
        // auto-click above accepts actually persists. Single-instruction ops
        // only -- no control flow inserted into the target method.
        webViewFragmentOnViewCreatedFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "removeAllCookie" &&
                    reference.definingClass == "Landroid/webkit/CookieManager;"
            }
            addInstructions(
                index,
                """
                iget-object v0, p0, Lcom/laurencedawson/reddit_sync/ui/fragments/WebViewFragment;->r0:Ljava/lang/String;
                """
            )
            replaceInstruction(
                index + 1,
                "invoke-static { p1, v0 }, $HELPER_CLASS->removeAllCookieUnlessRedgifs(Landroid/webkit/CookieManager;Ljava/lang/String;)V"
            )
        }
    }
}
