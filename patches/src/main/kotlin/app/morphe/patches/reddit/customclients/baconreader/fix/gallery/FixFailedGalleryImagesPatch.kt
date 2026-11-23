/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.baconreader.fix.gallery

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

private const val REST_CLIENT = "Lcom/onelouder/baconreader/connectivity/RestClient;"
private const val GALLERY_REPAIR = "Lapp/morphe/extension/shared/fixes/api/FailedGalleryMetadataPatch;"

@Suppress("unused")
val fixFailedGalleryImagesPatch = fixFailedGalleryImagesPatch(
    extensionPatch = ExtensionPatches.BaconReader,
    compatible = AppCompatibility.BaconReader,
) {
    // Apache requests bypass the interceptor; repair the consumed response.
    // The wrapper supplies v0-v2; p1 is scratch after the native call.
    Fingerprint(
        definingClass = REST_CLIENT,
        name = "executeRequest",
        returnType = "V",
        parameters = listOf("Landroid/content/Context;", "Lorg/apache/http/client/methods/HttpUriRequest;"),
    ).wrapMorpheHook(
        after = """
            iget-object v0, p0, $REST_CLIENT->response:Ljava/lang/String;
            iget-object v1, p0, $REST_CLIENT->url:Ljava/lang/String;
            iget v2, p0, $REST_CLIENT->responseCode:I
            iget-object p1, p0, $REST_CLIENT->contentType:Ljava/lang/String;
            invoke-static {v0, v1, v2, p1}, $GALLERY_REPAIR->repairApacheResponse(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
            move-result-object v0
            iput-object v0, p0, $REST_CLIENT->response:Ljava/lang/String;
        """.trimIndent(),
    )
}
