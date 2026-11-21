/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.COMMENT_VIDEO_LINKS
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixCommentVideoLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val fixCommentVideoLinksPatch =
    fixCommentVideoLinksPatch(ExtensionPatches.Boost, AppCompatibility.Boost) {
        val getter = Fingerprint(
            parameters = emptyList(),
            returnType = "Ljava/lang/String;",
            strings = listOf("https://v.redd.it/", "/DASHPlaylist.mpd"),
        )
        val owner = getter.classDef
        val parent = mutableClassDefBy(checkNotNull(owner.superclass))
        val source = parent.methods.single {
            it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/String;")
        }.implementation!!.instructions.mapNotNull {
            if (it.opcode == Opcode.IPUT_OBJECT) (it as? ReferenceInstruction)?.reference as? FieldReference else null
        }.single { it.type == "Ljava/lang/Object;" }
        getter.wrapMorpheHook(before = """
            iget-object v0, p0, $source
            check-cast v0, Ljava/lang/String;
            invoke-static {v0}, $COMMENT_VIDEO_LINKS->dashUrl(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :morphe_native_video
            return-object v0
            :morphe_native_video
            nop
        """)
    }
