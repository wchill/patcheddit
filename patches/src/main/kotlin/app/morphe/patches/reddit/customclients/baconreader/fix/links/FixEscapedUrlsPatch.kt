/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.links

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ESCAPED_URLS_PATCH
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixEscapedUrlsPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

private const val REST_CLIENT = "Lcom/onelouder/baconreader/connectivity/RestClient;"

@Suppress("unused")
val fixEscapedUrlsPatch = fixEscapedUrlsPatch(
    ExtensionPatches.BaconReader, AppCompatibility.BaconReader,
) { setting ->
    Fingerprint(
        definingClass = REST_CLIENT,
        name = "executeRequest",
        returnType = "V",
        parameters = listOf("Landroid/content/Context;", "Lorg/apache/http/client/methods/HttpUriRequest;"),
    ).wrapMorpheHook(
        setting = setting,
        after = """
            iget-object v0, p0, $REST_CLIENT->response:Ljava/lang/String;
            iget-object v1, p0, $REST_CLIENT->url:Ljava/lang/String;
            iget v2, p0, $REST_CLIENT->responseCode:I
            iget-object p1, p0, $REST_CLIENT->contentType:Ljava/lang/String;
            invoke-static {v0, v1, v2, p1}, $ESCAPED_URLS_PATCH->repairApacheResponse(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
            move-result-object v0
            iput-object v0, p0, $REST_CLIENT->response:Ljava/lang/String;
        """.trimIndent(),
    )
}
