package app.morphe.patches.reddit.customclients.infinityforreddit.subscription

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.string

internal val billingClientOnServiceConnectedFingerprint = Fingerprint(
    filters = listOf(
        string("Billing service connected")
    )
)

internal val startSubscriptionActivityFingerprint = Fingerprint(
    filters = listOf(
        // Intent start flag only used in the subscription activity
        literal(0x10008000)
    )
)