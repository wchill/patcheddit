package app.morphe.patches.scoresapp.ads

import app.morphe.patcher.Fingerprint

// The ad loader. Its first call is the static "are ads removed?" getter, which reads the
// ads-removed StateFlow<Boolean> from the settings repository. The ad model classes keep
// their names, so the parameter list is a stable anchor across the obfuscated builds.
internal val loadAdFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "L",
        "Lcom/scoresapp/domain/model/ads/AdPosition;",
        "Lcom/scoresapp/domain/model/ads/AdScreen;",
        "Ljava/util/Set;",
        "Ljava/util/Set;",
        "L",
    ),
)

// The billing manager. Its constructor builds a MutableStateFlow and wraps it with
// asStateFlow(). The patch borrows those two (obfuscated) helpers to build its own flow.
internal val billingManagerFingerprint = Fingerprint(
    strings = listOf("Billing client connected"),
)
