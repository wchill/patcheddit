/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patches.all.misc.PatchCategory

private const val UNIFIED_MATERIAL_NAV_PATCH_MARKER =
    "MORPHE_BOOST_UNIFIED_MATERIAL_BOTTOM_NAV_V771"

private const val INBOX_PROFILE_UNDERLAY_MARKER =
    "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74"

private const val INBOX_PROFILE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boost/utils/BoostSearchBottomNavigation;"

private val messagesActivityUnderlayLifecycleFingerprint = BoostApi.MessagesActivity.onResume.fingerprint()

private val userActivityUnderlayLifecycleFingerprint = BoostApi.UserActivity.onCreate.fingerprint()

@Suppress("unused")
val boostInboxProfileSystemUnderlayPatch = morpheBytecodePatch(
    name = "Standardize Boost Inbox and Profile bottom navigation",
    description = "Shows the same five-tab bottom navigation in Inbox and Profile.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.NAVIGATION,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)
    dependsOn(ExtensionPatches.Boost)

    execute {
        check(
            UNIFIED_MATERIAL_NAV_PATCH_MARKER.startsWith(
                "MORPHE_BOOST_UNIFIED_MATERIAL_"
            )
        )

        check(
            INBOX_PROFILE_UNDERLAY_MARKER ==
                "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74"
        )

        messagesActivityUnderlayLifecycleFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0}, $INBOX_PROFILE_EXTENSION_DESCRIPTOR->standardizeInbox(Landroid/app/Activity;)V",
        )
        userActivityUnderlayLifecycleFingerprint.wrapHook(
            setting = setting,
            after = "invoke-static {p0}, $INBOX_PROFILE_EXTENSION_DESCRIPTOR->standardizeProfile(Landroid/app/Activity;)V",
        )
    }
}
