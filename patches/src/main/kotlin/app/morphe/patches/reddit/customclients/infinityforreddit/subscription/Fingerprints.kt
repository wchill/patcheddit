package app.morphe.patches.reddit.customclients.infinityforreddit.subscription

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal

internal val billingClientOnServiceConnectedFingerprint = Fingerprint(
    strings = listOf("Billing service connected")
)

internal val startSubscriptionActivityFingerprint = Fingerprint(
    filters = listOf(
        // Intent start flag only used in the subscription activity
        literal(0x10008000)
    )
)