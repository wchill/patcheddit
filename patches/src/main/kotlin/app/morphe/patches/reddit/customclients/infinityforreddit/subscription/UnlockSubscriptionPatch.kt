package app.morphe.patches.reddit.customclients.infinityforreddit.subscription

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.infinityforreddit.api.spoofClientPatch
import app.morphe.util.returnEarly

@Suppress("unused")
val unlockSubscriptionPatch = bytecodePatch(
    name = "Unlock subscription",
    description = "Unlocks the subscription feature but requires a custom client ID.",
) {
    dependsOn(spoofClientPatch)

    compatibleWith(
        "ml.docilealligator.infinityforreddit",
        "ml.docilealligator.infinityforreddit.plus",
        "ml.docilealligator.infinityforreddit.patreon"
    )

    execute {
        setOf(
            startSubscriptionActivityFingerprint,
            billingClientOnServiceConnectedFingerprint,
        ).forEach { it.method.returnEarly() }
    }
}
