package app.morphe.patches.reddit.customclients.boostforreddit.fix.slink

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

internal val HandleNavigationFingerprint = BoostApi.Navigation.handleNavigation.fingerprint(
    strings = listOf(
        "android.intent.action.SEARCH",
        "subscription",
        "sort",
        "period",
        "boostforreddit.com/themes",
    ),
)