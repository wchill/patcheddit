package app.morphe.patches.reddit.customclients.baconreader.fix.redgifs

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags


internal val getOkHttpClientFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "Lokhttp3/OkHttpClient;",
    parameters = listOf(),
    custom = {
        method, classDef -> classDef.type == "Lcom/onelouder/baconreader/media/gfycat/RedGifsManager;" && method.name == "getOkhttpClient"
    }
)
