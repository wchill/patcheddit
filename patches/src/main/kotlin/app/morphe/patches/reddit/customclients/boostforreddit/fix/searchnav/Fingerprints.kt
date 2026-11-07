/*
 * Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import com.android.tools.smali.dexlib2.Opcode

internal val searchAbstractActivityOnCreateFingerprint = BoostApi.SearchAbstractActivity.onCreate.fingerprint()

internal val drawerMaterialBottomNavigationFingerprint = BoostApi.DrawerActivity.setupBottomNavigation.fingerprint()

internal val drawerMaterialBottomNavigationSelectionFingerprint = Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = "Lcom/google/android/material/navigation/NavigationBarView;",
            name = "setSelectedItemId",
            parameters = listOf("I"),
            returnType = "V",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)

internal val mainActivityOnResumeFingerprint = BoostApi.MainActivity.onResume.fingerprint()

internal val subredditActivityOnCreateFingerprint = BoostApi.SubredditActivity.onCreate.fingerprint()

internal val drawerBottomNavigationVisibilityFingerprint = BoostApi.DrawerActivity.setBottomNavigationVisible.fingerprint()
internal val inboxCountUpdateFingerprint = BoostApi.DrawerActivity.updateInboxCount.fingerprint()

internal val emptyRecyclerViewBottomNavigationPaddingFingerprint = BoostApi.EmptyRecyclerView.emptyRecyclerViewBottomNavigationPadding.fingerprint()
