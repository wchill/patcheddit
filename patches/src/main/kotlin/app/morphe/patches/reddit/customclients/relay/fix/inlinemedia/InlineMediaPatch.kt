/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.inlinemedia

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.relay.fix.medialinks.mediaLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val INLINE = "Lapp/morphe/extension/relay/media/InlineMedia;"
private const val BRIDGE = "Lapp/morphe/extension/relay/media/NativeInlineMedia;"
private const val TEXT = "Landroid/widget/TextView;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val SET_TEXT = "$TEXT->setText($CHAR_SEQUENCE)V"
private const val BUILDER = "Lcom/bumptech/glide/RequestBuilder;"
private const val MANAGER = "Lcom/bumptech/glide/RequestManager;"
private const val TARGET = "Lcom/bumptech/glide/request/target/Target;"
private const val OPTIONS = "Lcom/bumptech/glide/request/BaseRequestOptions;"
private const val DRAWABLE = "Landroid/graphics/drawable/Drawable;"
private const val ACTIVE_TEXT = "Lreddit/news/listings/common/views/ActiveTextView2;"
private const val MOTION = "Landroid/view/MotionEvent;"

private val commentBodies = Fingerprint(
    definingClass = "Lreddit/news/listings/comments/delegates/base/CommentAdapterDelegateBase;",
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            val field = (it as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lreddit/news/oauth/reddit/model/RedditComment;" && field.name == "htmlBodySpanned"
        } == true
    },
)
private val threadCommentBody = Fingerprint(
    definingClass = "Lreddit/news/adapters/CommentsAdapter;",
    name = "getView",
    parameters = listOf("I", "Landroid/view/View;", "Landroid/view/ViewGroup;"),
    returnType = "Landroid/view/View;",
)
private val postBody = Fingerprint(
    definingClass = "Lreddit/news/WebAndCommentsFragment;",
    parameters = listOf(CHAR_SEQUENCE), returnType = "V",
    custom = { method, _ -> AccessFlags.PROTECTED.isSet(method.accessFlags) },
)

private fun MutableMethod.hookBodyText(index: Int, opcode: Opcode) {
    val call = getInstruction<FiveRegisterInstruction>(index)
    require(call.opcode == opcode && call.registerCount == 2) { "Unsupported Relay body setter" }
    replaceInstruction(index,
        "invoke-static {v${call.registerC}, v${call.registerD}}, $INLINE->setText($TEXT$CHAR_SEQUENCE)V")
}

@Suppress("unused")
val inlineMediaPatch = morpheBytecodePatch(
    name = "Inline media",
    description = "Shows linked images and GIFs in Relay comments and text posts.",
    extensionPatch = ExtensionPatches.Relay,
    category = PatchCategory.MEDIA,
    optionsResource = "morphe_relay_inline_media",
) { setting ->
    compatibleWith(*AppCompatibility.Relay)
    dependsOn(mediaLinksPatch)
    execute {
        fun api(owner: String, parameters: List<String>, returns: String, static: Boolean = false) =
            Fingerprint(definingClass = owner, parameters = parameters, returnType = returns,
                custom = { method, _ -> AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                        AccessFlags.STATIC.isSet(method.accessFlags) == static }).method
        val with = api("Lcom/bumptech/glide/Glide;", listOf("Landroid/content/Context;"), MANAGER, true)
        val drawable = Fingerprint(
            definingClass = MANAGER, parameters = emptyList(), returnType = BUILDER,
            custom = { method, _ -> method.implementation?.instructions?.any {
                it.opcode == Opcode.CONST_CLASS && (it as ReferenceInstruction).reference.toString() == DRAWABLE
            } == true },
        ).method
        val override = api(OPTIONS, listOf("I", "I"), OPTIONS)
        val apply = api(BUILDER, listOf(OPTIONS), BUILDER)
        val load = api(BUILDER, listOf("Ljava/lang/String;"), BUILDER)
        val into = api(BUILDER, listOf(TARGET), TARGET)
        val clear = api(MANAGER, listOf(TARGET), "V")
        val span = mutableClassDefBy("Lreddit/news/oauth/glide/GlideImageSpan;")
        require(span.methods.any { it.name == "a" && it.parameterTypes.isEmpty() && it.returnType == "V" } &&
                span.methods.any { it.name == "b" && it.parameterTypes == listOf(DRAWABLE) && it.returnType == "V" }) {
            "Unsupported Relay inline image span API"
        }
        val owner = mutableClassDefBy(BRIDGE)
        setting.returnEnabled(owner.methods.single { it.name == "isIncluded" })
        fun bridge(name: String, registers: Int, code: String) {
            val original = owner.methods.single { it.name == name }
            val method = ImmutableMethod(owner.type, original.name, original.parameters, original.returnType,
                original.accessFlags, original.annotations, original.hiddenApiRestrictions,
                MutableMethodImplementation(registers)).toMutable()
            method.addInstructions(0, code)
            owner.methods.remove(original)
            owner.methods.add(method)
        }
        bridge("load", 8, """
            invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;
            move-result-object v0
            invoke-virtual {v0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;
            move-result-object v0
            invoke-static {v0}, $with
            move-result-object v0
            invoke-virtual {v0}, $drawable
            move-result-object v0
            new-instance v1, Lcom/bumptech/glide/request/RequestOptions;
            invoke-direct {v1}, Lcom/bumptech/glide/request/RequestOptions;-><init>()V
            invoke-virtual {v1, p3, p4}, $override
            move-result-object v1
            invoke-virtual {v0, v1}, $apply
            move-result-object v0
            invoke-virtual {v0, p1}, $load
            move-result-object v0
            check-cast p2, $TARGET
            invoke-virtual {v0, p2}, $into
            return-void
        """)
        bridge("clear", 3, """
            invoke-virtual {p0}, Landroid/view/View;->getContext()Landroid/content/Context;
            move-result-object v0
            invoke-virtual {v0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;
            move-result-object v0
            invoke-static {v0}, $with
            move-result-object v0
            check-cast p1, $TARGET
            invoke-virtual {v0, p1}, $clear
            return-void
        """)
        // Glide callback names differ between free and Pro; use its failure dispatch.
        val failure = Fingerprint(
            definingClass = "Lcom/bumptech/glide/request/SingleRequest;",
            parameters = emptyList(), returnType = "V",
            custom = { method, _ -> AccessFlags.PRIVATE.isSet(method.accessFlags) &&
                    method.implementation?.instructions?.any {
                        val call = (it as? ReferenceInstruction)?.reference as? MethodReference
                        call?.definingClass == TARGET && call.parameterTypes == listOf(DRAWABLE) && call.returnType == "V"
                    } == true },
        ).method.implementation!!.instructions.mapNotNull {
            (it as? ReferenceInstruction)?.reference as? MethodReference
        }.single { it.definingClass == TARGET }
        mutableClassDefBy(INLINE.dropLast(1) + "\$PreviewTarget;").methods.single { it.name == "failed" }.name = failure.name

        // Leave Relay's GestureDetector in charge of distinguishing a tap from a scroll.
        Fingerprint(definingClass = ACTIVE_TEXT, parameters = listOf(MOTION), returnType = "Z",
            strings = listOf("isOverLink: x-")).wrapMorpheHook(before = """
                invoke-static {p0, p1}, $INLINE->outsidePreviewTarget($TEXT$MOTION)Z
                move-result v0
                if-eqz v0, :morphe_skip_before
                const/4 v0, 0x0
                return v0
            """)
        Fingerprint(definingClass = ACTIVE_TEXT,
            parameters = listOf("Landroid/text/Spanned;", "Landroid/text/style/ClickableSpan;"),
            returnType = "V", strings = listOf("onDraw: ")).wrapMorpheHook(before = """
                instance-of v0, p2, ${INLINE.dropLast(1)}${'$'}PreviewLink;
                if-eqz v0, :morphe_skip_before
                return-void
            """)

        // Only body setters are hooked; titles, author flair, and award spans stay native.
        for (match in commentBodies.matchAll()) {
            val code = match.method.implementation!!.instructions.toList()
            val sites = code.indices.filter {
                val field = (code[it] as? ReferenceInstruction)?.reference as? FieldReference
                field?.name == "htmlBodySpanned" && field.definingClass == "Lreddit/news/oauth/reddit/model/RedditComment;"
            }
            for (site in sites) {
                match.method.hookBodyText(site + 1, Opcode.INVOKE_STATIC)
            }
        }
        // Thread comments use the ListView adapter, not the comment listing delegates.
        threadCommentBody.method.apply {
            val code = implementation!!.instructions.toList()
            val site = (1 until code.size).single { index ->
                val body = (code[index - 1] as? ReferenceInstruction)?.reference as? FieldReference
                code[index - 1].opcode == Opcode.IGET_OBJECT &&
                    body?.definingClass == "Lreddit/news/data/DataComment;" &&
                    body.type == "Landroid/text/Spanned;" &&
                    (code[index] as? ReferenceInstruction)?.reference.toString() == SET_TEXT
            }
            hookBodyText(site, Opcode.INVOKE_VIRTUAL)
        }
        postBody.method.apply {
            val site = implementation!!.instructions.withIndex().single {
                (it.value as? ReferenceInstruction)?.reference.toString() == SET_TEXT
            }.index
            hookBodyText(site, Opcode.INVOKE_VIRTUAL)
        }
    }
}
