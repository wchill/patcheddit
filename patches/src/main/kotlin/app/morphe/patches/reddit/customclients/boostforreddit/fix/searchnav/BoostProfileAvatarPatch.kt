/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook

internal val boostProfileAvatarPatch = bytecodePatch(default = false) {
    dependsOn(ExtensionPatches.Boost)

    execute {
        BoostApi.DrawerActivity.setProfileAvatar.fingerprint().wrapHook(
            after = """
                invoke-static {p0, p1}, Lapp/morphe/extension/boost/utils/BoostSearchBottomNavigation;->syncProfileAvatar(Landroid/app/Activity;Landroid/graphics/drawable/Drawable;)V
            """,
        )
    }
}
