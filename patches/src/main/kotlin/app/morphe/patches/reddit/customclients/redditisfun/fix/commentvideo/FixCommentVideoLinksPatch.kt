/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.commentvideo

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.currentMethod
import app.morphe.patches.reddit.customclients.fixCommentVideoLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

@Suppress("unused")
val fixCommentVideoLinksPatch =
    fixCommentVideoLinksPatch(ExtensionPatches.RIF, AppCompatibility.RedditIsFun) {
        val targets = resolveRifCommentVideoTargets(buildList { classDefForEach(::add) })
        val extension = mutableClassDefBy(RIF_VIDEO_HOOK)
        val factory = extension.methods.single {
            it.name == "createVideo" && it.parameterTypes == listOf("Ljava/lang/String;", "Ljava/lang/String;")
                    && it.returnType == "Ljava/lang/Object;"
        }
        val bound = ImmutableMethod(
            factory.definingClass, factory.name, factory.parameters, factory.returnType, factory.accessFlags,
            factory.annotations, factory.hiddenApiRestrictions, MutableMethodImplementation(4),
        ).toMutable().apply {
            addInstructions(0, """
                new-instance v0, $VIDEO
                invoke-direct {v0}, $VIDEO-><init>()V
                invoke-virtual {v0, p0}, ${targets.dashSetter}
                invoke-virtual {v0, p1}, ${targets.hlsSetter}
                const/4 v1, 0x0
                invoke-virtual {v0, v1}, ${targets.gifSetter}
                return-object v0
            """)
        }
        check(extension.methods.remove(factory))
        check(extension.methods.add(bound))
        for (callback in targets.callbacks) {
            val owner = mutableClassDefBy(callback.method.definingClass)
            if (callback.sourceField == SOURCE_FIELD) {
                owner.fields.add(ImmutableField(
                    owner.type, SOURCE_FIELD, "Landroid/net/Uri;", AccessFlags.PRIVATE.value,
                    null, emptySet(), emptySet(),
                ).toMutable())
                currentMethod(owner, callback.constructor).addInstructions(1,
                    "iput-object p1, p0, ${owner.type}->$SOURCE_FIELD:Landroid/net/Uri;")
            }
            wrapMorpheHook(owner, callback.method, before = """
                iget-object v0, p0, ${owner.type}->${callback.sourceField}:Landroid/net/Uri;
                invoke-static {p1, v0}, $RIF_VIDEO_HOOK->fallback(Ljava/lang/Object;Landroid/net/Uri;)Ljava/lang/Object;
                move-result-object p1
                check-cast p1, $VIDEO
            """)
        }
    }
