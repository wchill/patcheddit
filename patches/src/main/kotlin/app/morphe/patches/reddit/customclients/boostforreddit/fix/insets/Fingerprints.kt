/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (82d70f7).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.insets

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

internal val mainActivityOnResumeFingerprint = BoostApi.MainActivity.onResume.fingerprint()

internal val myApplicationOnCreateFingerprint = BoostApi.MyApplication.onCreate.fingerprint()
