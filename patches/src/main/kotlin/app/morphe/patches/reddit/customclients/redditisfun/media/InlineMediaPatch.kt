/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.media

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.currentMethod
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.parameterWords
import app.morphe.patches.reddit.customclients.redditisfun.api.imgurAlbumsPatch
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.fix.commentvideo.fixCommentVideoLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val MEDIA = "Lapp/morphe/extension/redditisfun/media/InlineMedia;"
private const val VIDEO = "Lapp/morphe/extension/redditisfun/media/InlineVideo;"
private const val BRIDGE = "Lapp/morphe/extension/redditisfun/media/NativePlayer;"
private const val CONTROLS = "Lcom/google/android/exoplayer2/ui/PlayerControlView;"
private const val DASH = "Lcom/google/android/exoplayer2/source/dash/DashMediaSource"
private const val CONTEXT = "Landroid/content/Context;"
private const val URI = "Landroid/net/Uri;"
private const val TEXT = "Landroid/widget/TextView;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"

@Suppress("unused")
val inlineMediaPatch = morpheBytecodePatch(
    name = "Inline media",
    description = "Shows images, albums, animated images, and videos in comments and text posts.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.MEDIA,
    preferenceKey = "morphe_patch_inline_media",
    optionsResource = "morphe_rif_inline_media",
) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(imgurAlbumsPatch, fixCommentVideoLinksPatch)

    execute {
        val classes = buildList { classDefForEach(::add) }.associateBy { it.type }
        val free = resolveRifNativeSku(classes.values.toList()) == RifNativeSku.FREE
        val error = "Unsupported RIF 5.6.22 inline media integration."
        fun method(owner: String, name: String, parameters: List<String>, returns: String): Method =
            classes[owner]?.methods?.singleOrNull {
                it.name == name && it.parameterTypes.map(CharSequence::toString) == parameters && it.returnType == returns
            }?.also { require(!AccessFlags.STATIC.isSet(it.accessFlags)) { error } }
                ?: throw IllegalArgumentException(error)

        val feed = if (free) "e5" else "f5"
        val comment = if (free) "n2" else "o2"
        val threadBody = if (free) "C0" else "F0"
        val commentBody = if (free) "c0" else "g0"
        val thread = "Lcom/andrewshu/android/reddit/things/objects/ThreadThing;"
        val thing = "Lcom/andrewshu/android/reddit/things/objects/CommentThing;"
        val fragment = "Landroidx/fragment/app/Fragment;"
        val bodyMethods = listOf(
            Triple(method("L$feed/g;", "p", listOf("L$feed/h;", thread, "L${if (free) "b5" else "c5"}/r0;"), "V"),
                method(thread, threadBody, emptyList(), CHAR_SEQUENCE), 1),
            Triple(method("L$feed/u;", "o", listOf("L$feed/z;", thread), "V"),
                method(thread, threadBody, emptyList(), CHAR_SEQUENCE), 2),
            Triple(method("L$comment/c;", "e", listOf("L$comment/m;", thing, fragment), "V"),
                method(thing, commentBody, emptyList(), CHAR_SEQUENCE), 1),
            Triple(method("L$comment/o;", "h", listOf("L$comment/m;", thing, fragment), "V"),
                method(thing, commentBody, emptyList(), CHAR_SEQUENCE), 1),
        )
        for ((target, getter, count) in bodyMethods) {
            val code = target.implementation!!.instructions.toList()
            val sites = code.indices.filter { index ->
                val reference = (code[index] as? ReferenceInstruction)?.reference
                reference.toString() == "$TEXT->setText($CHAR_SEQUENCE)V" &&
                        code.subList(maxOf(0, index - 14), index).any {
                            (it as? ReferenceInstruction)?.reference?.toString() == getter.toString()
                        }
            }
            require(sites.size == count) { error }
            val mutable = currentMethod(mutableClassDefBy(target.definingClass), target)
            for (index in sites) {
                val call = code[index] as FiveRegisterInstruction
                require(call.opcode == Opcode.INVOKE_VIRTUAL && call.registerCount == 2) { error }
                mutable.replaceInstruction(index,
                    "invoke-static {v${call.registerC}, v${call.registerD}}, $MEDIA->setText($TEXT$CHAR_SEQUENCE)V")
            }
        }

        val player = if (free) "Lv7/e3;" else "Lu7/l3;"
        val exo = if (free) "Lv7/s;" else "Lu7/z;"
        val builder = exo.dropLast(1) + "\$b;"
        val renderers = if (free) "Lv7/m;" else "Lu7/t;"
        val rendererInterface = if (free) "Lv7/r3;" else "Lu7/y3;"
        val source = if (free) "Lx8/u;" else "Lw8/u;"
        val item = if (free) "Lv7/x1;" else "Lu7/e2;"
        val itemBuilder = item.dropLast(1) + "\$c;"
        val data = if (free) "Lr9/m\$a;" else "Lq9/m\$a;"
        val http = if (free) "Lb8/a\$b;" else "La8/a\$b;"
        val dataFactory = if (free) "Lr9/u\$a;" else "Lq9/u\$a;"
        val progressive = if (free) "Lx8/i0\$b;" else "Lw8/i0\$b;"
        val texture = "Landroid/view/TextureView;"
        require(AccessFlags.INTERFACE.isSet(classes.getValue(player).accessFlags) &&
                player in classes.getValue(exo).interfaces) { error }

        // Every emitted native call is checked against this SKU, including dispatch kind.
        fun call(owner: String, name: String, parameters: List<String>, returns: String): String {
            val target = method(owner, name, parameters, returns)
            require(AccessFlags.PUBLIC.isSet(target.accessFlags)) { error }
            val dispatch = when {
                name == "<init>" -> "invoke-direct"
                AccessFlags.INTERFACE.isSet(classes.getValue(owner).accessFlags) -> "invoke-interface"
                else -> "invoke-virtual"
            }
            return "$dispatch %s, $target"
        }
        fun bridge(name: String, parameters: Int? = null, body: String) {
            val owner = mutableClassDefBy(BRIDGE)
            val target = owner.methods.single { it.name == name && (parameters == null || it.parameterTypes.size == parameters) }
            val replacement = ImmutableMethod(target.definingClass, target.name, target.parameters, target.returnType,
                target.accessFlags, target.annotations, target.hiddenApiRestrictions,
                MutableMethodImplementation(parameterWords(target) + 6)).toMutable().apply { addInstructions(0, body) }
            check(owner.methods.remove(target))
            check(owner.methods.add(replacement))
        }
        bridge("create", body = """
            new-instance v0, $renderers
            ${call(renderers, "<init>", listOf(CONTEXT), "V").format("{v0, p0}")}
            new-instance v1, $builder
            ${call(builder, "<init>", listOf(CONTEXT, rendererInterface), "V").format("{v1, p0, v0}")}
            ${call(builder, "f", emptyList(), exo).format("{v1}")}
            move-result-object v1
            new-instance v0, $http
            ${call(http, "<init>", listOf("Lokhttp3/e\$a;"), "V").format("{v0, p3}")}
            new-instance v2, $dataFactory
            ${call(dataFactory, "<init>", listOf(CONTEXT, data), "V").format("{v2, p0, v0}")}
            new-instance v0, $itemBuilder
            ${call(itemBuilder, "<init>", emptyList(), "V").format("{v0}")}
            ${call(itemBuilder, "g", listOf(URI), itemBuilder).format("{v0, p2}")}
            ${call(itemBuilder, "a", emptyList(), item).format("{v0}")}
            move-result-object v0
            if-eqz p4, :progressive
            new-instance v3, $DASH${'$'}Factory;
            ${call("$DASH\$Factory;", "<init>", listOf(data), "V").format("{v3, v2}")}
            ${call("$DASH\$Factory;", "a", listOf(item), "$DASH;").format("{v3, v0}")}
            move-result-object v0
            goto :source_ready
            :progressive
            new-instance v3, $progressive
            ${call(progressive, "<init>", listOf(data), "V").format("{v3, v2}")}
            ${call(progressive, "b", listOf(item), if (free) "Lx8/i0;" else "Lw8/i0;").format("{v3, v0}")}
            move-result-object v0
            :source_ready
            ${call(exo, if (free) "L" else "I", listOf(source), "V").format("{v1, v0}")}
            ${call(player, if (free) "Y" else "b0", listOf(texture), "V").format("{v1, p1}")}
            ${call(player, if (free) "a" else "d", emptyList(), "V").format("{v1}")}
            return-object v1
        """)
        bridge("controls", body = """
            check-cast p1, $player
            new-instance v0, $CONTROLS
            const/4 v1, 0x0
            ${call(CONTROLS, "<init>", listOf(CONTEXT, "Landroid/util/AttributeSet;"), "V").format("{v0, p0, v1}")}
            ${call(CONTROLS, "setPlayer", listOf(player), "V").format("{v0, p1}")}
            const/4 v1, 0x1
            ${call(CONTROLS, "setRepeatToggleModes", listOf("I"), "V").format("{v0, v1}")}
            const/4 v1, 0x0
            ${call(CONTROLS, "setShowTimeoutMs", listOf("I"), "V").format("{v0, v1}")}
            return-object v0
        """)
        // R8 removed setVolume, but retained the audio-focus volume dispatcher.
        val implementation = classes.values.single { exo in it.interfaces }
        val volume = implementation.methods.single { target ->
            target.parameterTypes.isEmpty() && target.returnType == "V" &&
                    target.implementation?.instructions?.any {
                        (it as? ReferenceInstruction)?.reference?.toString() == "Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;"
                    } == true
        }
        val volumeCode = volume.implementation!!.instructions.toList()
        val volumeField = (volumeCode.first() as? ReferenceInstruction)?.reference as? FieldReference
        require(volume.accessFlags == AccessFlags.PRIVATE.value && volumeField?.definingClass == implementation.type
                && volumeField.type == "F" && volumeCode.map { it.opcode } == listOf(
            Opcode.IGET, Opcode.IGET_OBJECT, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.MUL_FLOAT,
            Opcode.CONST_4, Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT_OBJECT, Opcode.CONST_4,
            Opcode.INVOKE_DIRECT, Opcode.RETURN_VOID,
        )) { error }
        val volumeSetter = ImmutableMethod(implementation.type, "morpheInlineVolume", listOf(
            com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter("F", emptySet(), null),
        ), "V", AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(2)).toMutable().apply {
            addInstructions(0, """
                iput p1, p0, $volumeField
                invoke-direct {p0}, $volume
                return-void
            """)
        }
        mutableClassDefBy(implementation.type).apply {
            setAccessFlags(accessFlags or AccessFlags.PUBLIC.value)
            methods.add(volumeSetter)
        }
        bridge("volume", body = """
            check-cast p0, ${implementation.type}
            invoke-virtual {p0, p1}, $volumeSetter
            return-void
        """)
        for ((name, native, type) in listOf(
            Triple("play", if (free) "y" else "D", "Z"),
            Triple("repeat", if (free) "M" else "P", "I"),
            Triple("seek", if (free) "u" else "y", "J"),
        )) bridge(name, 2, """
            check-cast p0, $player
            ${call(player, native, listOf(type), "V").format(if (type == "J") "{p0, p1, p2}" else "{p0, p1}")}
            return-void
        """)
        bridge("release", body = """
            check-cast p0, $player
            ${call(player, if (free) "release" else "a", emptyList(), "V").format("{p0}")}
            return-void
        """)
        for ((name, native, type) in listOf(
            Triple("position", if (free) "getCurrentPosition" else "e0", "J"),
            Triple("repeat", if (free) "Q" else "T", "I"),
            Triple("state", if (free) "D" else "H", "I"),
            Triple("playing", if (free) "i" else "m", "Z"),
        )) bridge(name, 1, """
            check-cast p0, $player
            ${call(player, native, emptyList(), type).format("{p0}")}
            move-result${if (type == "J") "-wide" else ""} v0
            return${if (type == "J") "-wide" else ""} v0
        """)
        bridge("failed", body = """
            check-cast p0, $player
            ${call(player, if (free) "x" else "C", emptyList(), if (free) "Lv7/a3;" else "Lu7/h3;").format("{p0}")}
            move-result-object v0
            if-eqz v0, :no_error
            const/4 v0, 0x1
            return v0
            :no_error
            const/4 v0, 0x0
            return v0
        """)

        val nativeVideo = if (free) "Lv1/k0;" else "Lw1/k0;"
        val baseVideo = if (free) "Lv1/k;" else "Lw1/k;"
        val playerField = if (free) "T0" else "Q0"
        val uriField = if (free) "p0" else "m0"
        val initialize = method(nativeVideo, if (free) "X6" else "M6", emptyList(), "V")
        require(classes.getValue(nativeVideo).fields.single { it.name == playerField }.type == exo &&
                classes.getValue(baseVideo).fields.single { it.name == uriField }.type == URI &&
                initialize.implementation!!.instructions.any {
                    (it as? ReferenceInstruction)?.reference?.toString() == "$CONTROLS->setPlayer($player)V"
                }) { error }
        wrapMorpheHook(mutableClassDefBy(nativeVideo), initialize, after = """
            iget-object v0, p0, $nativeVideo->$playerField:$exo
            iget-object v1, p0, $baseVideo->$uriField:$URI
            invoke-static {v0, v1}, $VIDEO->handoff(Ljava/lang/Object;$URI)V
        """)
    }
}
