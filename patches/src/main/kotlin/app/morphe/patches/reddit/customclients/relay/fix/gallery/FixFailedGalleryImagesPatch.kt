/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.gallery

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixFailedGalleryImagesPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val GALLERY_MEDIA = "Lapp/morphe/extension/relay/media/GalleryMedia;"
private const val PREVIEW = "Lreddit/news/oauth/reddit/model/links/MediaPreview;"
private const val GET_STRING = "Lorg/json/JSONObject;->getString(Ljava/lang/String;)Ljava/lang/String;"

@Suppress("unused")
val fixFailedGalleryImagesPatch = fixFailedGalleryImagesPatch(
    extensionPatch = ExtensionPatches.Relay,
    compatible = AppCompatibility.Relay,
) {
    val parser = Fingerprint(
        definingClass = "Lreddit/news/data/DataStory;",
        parameters = listOf("Lorg/json/JSONObject;", "Landroid/os/Handler;",
            "Lreddit/news/oauth/reddit/model/RedditAccount;", "Landroid/content/SharedPreferences;"),
        returnType = "V",
        strings = listOf("gallery_data", "media_metadata", "mp4"),
    ).method
    val instructions = parser.implementation!!.instructions.toList()
    val start = instructions.indexOfFirst { it.getReference<StringReference>()?.string == "gallery_data" }
    val mp4Key = instructions.take(start).last { it.getReference<StringReference>()?.string == "mp4" }
    val mp4Register = (mp4Key as OneRegisterInstruction).registerA
    // The two gallery branches build one preview each, with or without resolution thumbnails.
    val previews = instructions.withIndex().drop(start).filter {
        val target = it.value.getReference<MethodReference>()
        it.value.opcode == Opcode.INVOKE_DIRECT_RANGE && target?.definingClass == PREVIEW && target.name == "<init>"
    }.take(2)
    check(previews.size == 2) { "Missing Relay gallery preview constructors" }
    val sources = instructions.withIndex().filter { (index, instruction) ->
        index in start until previews.last().index && instruction is FiveRegisterInstruction &&
            instruction.opcode == Opcode.INVOKE_VIRTUAL && instruction.registerD == mp4Register &&
            instruction.getReference<MethodReference>()?.toString() == GET_STRING
    }
    check(sources.size == 2 && sources[0].index < previews[0].index && sources[1].index > previews[0].index) {
        "Missing Relay gallery MP4 source reads"
    }
    for ((index, instruction) in sources) {
        val source = (instruction as FiveRegisterInstruction).registerC
        parser.replaceInstruction(index, "invoke-static {v$source, v$mp4Register}, " +
            "$GALLERY_MEDIA->animationSource(Lorg/json/JSONObject;Ljava/lang/String;)Ljava/lang/String;")
    }
    for ((index, instruction) in previews.asReversed()) {
        val receiver = (instruction as RegisterRangeInstruction).startRegister
        parser.addInstruction(index + 1, "invoke-static/range {v$receiver .. v$receiver}, $GALLERY_MEDIA->useGifViewer($PREVIEW)V")
    }
}
