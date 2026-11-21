/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.links

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RELATIVE_MARKDOWN_LINKS
import app.morphe.patches.reddit.customclients.fixRelativeMarkdownLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val fixRelativeMarkdownLinksPatch =
    fixRelativeMarkdownLinksPatch(ExtensionPatches.Sync, AppCompatibility.SyncForReddit) {
        val targets = buildList { classDefForEach(::add) }.flatMap { owner ->
            owner.methods.filter { method ->
                AccessFlags.STATIC.isSet(method.accessFlags) && method.returnType == "V"
                        && method.parameterTypes.size == 3 && method.parameterTypes.last() == "Ljava/lang/String;"
                        && method.implementation?.instructions?.any {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                            "http://www.ccil.org/~cowan/tagsoup/properties/schema"
                } == true
            }
        }
        val target = targets.singleOrNull() ?: error("Unsupported or ambiguous Sync HTML parser.")
        wrapMorpheHook(mutableClassDefBy(target.definingClass), target, before = """
            invoke-static/range {p2 .. p2}, $RELATIVE_MARKDOWN_LINKS->repairHtml(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p2
        """)
    }
