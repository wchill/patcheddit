/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly

fun fixRAllPatch(
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    additional: () -> Unit = {},
) = patch(
    description = "Loads /r/all through Reddit's .json endpoint so the feed shows posts.",
    default = true,
) {
    compatibleWith(*compatible)

    dependsOn(
        extensionPatch,
    )
    execute {
        Fingerprint(
            definingClass = "Lapp/morphe/extension/shared/fixes/feed/RAllPatch;",
            name = "isPatchIncluded",
        ).method.returnEarly(true)
        additional()
    }
}

internal val baconReaderRAllPatch = fixRAllPatch(ExtensionPatches.BaconReader, AppCompatibility.BaconReader) {
    val uriRequest = "Lorg/apache/http/client/methods/HttpUriRequest;"
    val requestBase = "Lorg/apache/http/client/methods/HttpRequestBase;"
    Fingerprint(
        definingClass = "Lcom/onelouder/baconreader/connectivity/RestClient;",
        name = "executeRequest",
        parameters = listOf("Landroid/content/Context;", uriRequest),
        returnType = "V",
    ).wrapMorpheHook(
        before = """
            instance-of v0, p2, $requestBase
            if-eqz v0, :morphe_skip_before
            move-object v0, p2
            check-cast v0, $requestBase
            invoke-virtual {v0}, $requestBase->getMethod()Ljava/lang/String;
            move-result-object v1
            invoke-virtual {v0}, $requestBase->getURI()Ljava/net/URI;
            move-result-object v2
            invoke-static {v1, v2}, Lapp/morphe/extension/shared/fixes/feed/RAllPatch;->rewriteLegacyListing(Ljava/lang/String;Ljava/net/URI;)Ljava/net/URI;
            move-result-object v1
            if-eqz v1, :morphe_skip_before
            invoke-virtual {v0, v1}, $requestBase->setURI(Ljava/net/URI;)V
        """,
    )
}

internal val joeyRAllPatch = fixRAllPatch(ExtensionPatches.Joey, AppCompatibility.JoeyForReddit)
internal val relayRAllPatch = fixRAllPatch(ExtensionPatches.Relay, AppCompatibility.Relay)
internal val rifRAllPatch = fixRAllPatch(ExtensionPatches.RIF, AppCompatibility.RedditIsFun)
internal val syncRAllPatch = fixRAllPatch(ExtensionPatches.Sync, AppCompatibility.SyncForReddit)
