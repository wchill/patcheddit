/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.patches.reddit.customclients.redgifsSetting
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val previews = Fingerprint(
    definingClass = "Lreddit/news/oauth/reddit/model/links/RedditLink;",
    parameters = listOf("Lokhttp3/HttpUrl;", "Lreddit/news/previews/MediaUrlFetcher;", "Ljava/util/ArrayList;"),
    returnType = "Ljava/util/ArrayList;",
)

private val fetchPreviews = Fingerprint(
    definingClass = "Lreddit/news/oauth/reddit/model/links/RedditLink;",
    name = "fetchMediaPreviews",
    parameters = listOf("Lreddit/news/previews/MediaUrlFetcher;", "Lrx/Scheduler;"),
    returnType = "V",
)

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(ExtensionPatches.Relay) {
    compatibleWith(*AppCompatibility.Relay)
    execute {
        // Both initial URL selection and post-processing can substitute a silent Reddit preview.
        for (fingerprint in listOf(fetchPreviews, previews)) {
            redgifsSetting.patchMethod(fingerprint) {
                val reads = checkNotNull(implementation).instructions.withIndex().filter {
                    it.value.opcode == Opcode.IGET_BOOLEAN &&
                        it.value.getReference<FieldReference>()?.definingClass ==
                        "Lreddit/news/remoteconfig/RemoteConfigManageBase;"
                }
                require(reads.size == 1) { "Unsupported Relay Redgifs preview flag" }
                val index = reads.single().index
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(index, "const/4 v$register, 0x1")
            }
        }
    }
}
