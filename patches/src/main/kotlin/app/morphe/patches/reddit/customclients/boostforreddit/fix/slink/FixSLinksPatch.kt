package app.morphe.patches.reddit.customclients.boostforreddit.fix.slink

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.hookWrapperActivity
import app.morphe.patches.reddit.customclients.redditWrapperViewFilters
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/FixSLinksPatch;"
private const val LINK_ACTIVITY = "com.rubenmayayo.reddit.ui.activities.DeepLinkingActivity"

@Suppress("unused")
val fixSlinksPatch = fixSLinksPatch(
    extensionPatch = ExtensionPatches.Boost,
) {
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(redditWrapperViewFilters(LINK_ACTIVITY))

    execute {
        hookWrapperActivity(LINK_ACTIVITY, handler = "$EXTENSION_CLASS_DESCRIPTOR->openExternalLink")
        // region Patch navigation handler.

        HandleNavigationFingerprint.wrapHook(
            before = """
                invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                move-result v0
                if-eqz v0, :morphe_native_navigation
                return v0
                :morphe_native_navigation
            """,
        )

        // endregion

        bindShareLinkAccessToken("""
            invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->getActiveAccessToken()Ljava/lang/String;
            move-result-object v0
            return-object v0
        """)
    }
}
