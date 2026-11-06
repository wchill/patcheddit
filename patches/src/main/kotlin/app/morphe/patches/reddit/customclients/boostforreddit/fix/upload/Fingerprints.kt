/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

internal val formattingBarSetEditTextFingerprint = BoostApi.FormattingBar.setEditText.fingerprint()

internal val remoteConfigStringFingerprint = BoostApi.RemoteConfigHelper.remoteConfigString.fingerprint()

internal val mediaUploaderFactoryFingerprint = BoostApi.MediaUploadFactory.create.fingerprint(
    strings = listOf(
        "imgur_free",
        "imgur_paid",
        "vgy",
        "reddit",
    ),
)
