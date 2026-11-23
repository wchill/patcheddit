/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.relay.fix.medialinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.currentMethod
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.relay.fix.slink.urlLinkClickManagerFingerprint
import app.morphe.patches.reddit.customclients.relay.fix.slink.webAndCommentsFragmentLinkFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val LINKS = "Lapp/morphe/extension/relay/media/MediaLinks;"
private const val VIEWER = "Lapp/morphe/extension/relay/media/MediaViewer;"
private const val NATIVE = "Lapp/morphe/extension/relay/media/NativeMedia;"
private const val FETCHER = "Lreddit/news/previews/MediaUrlFetcher;"
private const val BASE = "Lreddit/news/previews/FragmentBasePreview;"
private const val RX = "Lrx/Observable;"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"
private const val LIST = "Ljava/util/ArrayList;"
private const val BUILDER = "Lcom/bumptech/glide/RequestBuilder;"
private const val MANAGER = "Lcom/bumptech/glide/RequestManager;"

private fun target(owner: String, name: String, parameters: List<String>, returns: String) =
    Fingerprint(definingClass = owner, name = name, parameters = parameters, returnType = returns)

@Suppress("unused")
val mediaLinksPatch = morpheBytecodePatch(
    name = "Fix Reddit media link handling",
    description = "Opens Reddit-hosted images, GIFs, and videos, including videos linked in comments, in Relay's native media viewer.",
    extensionPatch = ExtensionPatches.Relay,
    category = PatchCategory.MEDIA,
    preferenceKey = "morphe_patch_open_reddit_media_links",
) { setting ->
    compatibleWith(*AppCompatibility.Relay)
    execute {
        fun method(owner: String, name: String, parameters: List<String>, returns: String): Method =
            target(owner, name, parameters, returns).method.also {
                require(AccessFlags.PUBLIC.isSet(it.accessFlags)) { "Unsupported Relay media API: $it" }
            }
        fun bridge(name: String, registers: Int, body: String) {
            val owner = mutableClassDefBy(NATIVE)
            val old = owner.methods.single { it.name == name }
            val replacement = ImmutableMethod(owner.type, old.name, old.parameters, old.returnType,
                old.accessFlags, old.annotations, old.hiddenApiRestrictions,
                MutableMethodImplementation(registers)).toMutable()
            replacement.addInstructions(0, body)
            owner.methods.remove(old)
            owner.methods.add(replacement)
        }
        val just = method(RX, "w", listOf(OBJECT), RX)
        val defer = method(RX, "v", listOf("Ljava/util/concurrent/Callable;"), RX)
        val io = method("Lrx/schedulers/Schedulers;", "d", emptyList(), "Lrx/Scheduler;")
        val subscribe = method(RX, "U", listOf("Lrx/Scheduler;"), RX)
        val parse = method(FETCHER, "A", listOf(STRING, "Ljava/io/InputStream;"), LIST)
        method("Lreddit/news/oauth/reddit/model/links/MediaDetails;", "<init>", listOf(STRING), "V")
        method("Lreddit/news/oauth/reddit/model/links/MediaPreview;", "<init>",
            listOf("Lreddit/news/oauth/reddit/model/links/MediaDetails;",
                "Lreddit/news/oauth/reddit/model/links/MediaDetails;") + List(5) { STRING } + "I", "V")
        bridge("just", 2, """
            invoke-static {p0}, $just
            move-result-object v0
            return-object v0
        """)
        bridge("defer", 3, """
            invoke-static {p0}, $defer
            move-result-object v0
            invoke-static {}, $io
            move-result-object v1
            invoke-virtual {v0, v1}, $subscribe
            move-result-object v0
            return-object v0
        """)
        bridge("parseDash", 4, """
            check-cast p0, $FETCHER
            invoke-virtual {p0, p1, p2}, $parse
            move-result-object v0
            return-object v0
        """)

        // Both comment and general link routers use this eligibility/resolution pair.
        target(FETCHER, "l", listOf("Lokhttp3/HttpUrl;"), "Z").method.apply {
            addInstructionsWithLabels(0, """
                if-eqz p1, :native
                invoke-virtual {p1}, $OBJECT->toString()$STRING
                move-result-object v0
                invoke-static {v0}, $LINKS->canHandle($STRING)Z
                move-result v0
                if-eqz v0, :native
                return v0
            """, ExternalLabel("native", getInstruction(0)))
        }
        target(FETCHER, "u", listOf(STRING), RX).method.apply {
            addInstructionsWithLabels(0, """
                invoke-static/range {p0 .. p1}, $VIEWER->resolve($OBJECT$STRING)$OBJECT
                move-result-object v0
                if-eqz v0, :native
                check-cast v0, $RX
                return-object v0
            """, ExternalLabel("native", getInstruction(0)))
        }
        setting.guardHook(LINKS, "canHandle")
        setting.guardHook(VIEWER, "resolve")

        // A reddit.com player link otherwise reaches Relay's Reddit page router before its media matcher.
        for (router in listOf(urlLinkClickManagerFingerprint, webAndCommentsFragmentLinkFingerprint)) {
            router.method.apply {
                addInstructionsWithLabels(0, """
                    invoke-static/range {p1 .. p1}, $LINKS->commentVideoBase($STRING)$STRING
                    move-result-object v0
                    if-eqz v0, :native
                    move-object/from16 p1, v0
                """, ExternalLabel("native", getInstruction(0)))
            }
        }
        setting.guardHook(LINKS, "commentVideoBase")

        // Native DASH parsing previously assumed every manifest lived directly under v.redd.it.
        val parser = currentMethod(mutableClassDefBy(FETCHER), parse)
        val uriSite = parser.implementation!!.instructions.withIndex().single {
            (it.value as? ReferenceInstruction)?.reference.toString() ==
                    "Landroid/net/Uri;->parse($STRING)Landroid/net/Uri;"
        }.index
        val uriRegister = parser.getInstruction<FiveRegisterInstruction>(uriSite).registerC
        parser.addInstructions(uriSite, """
            move-object/from16 v3, p1
            invoke-static {v3, v$uriRegister}, $VIEWER->manifestBase($STRING$STRING)$STRING
            move-result-object v$uriRegister
        """)

        // Glide's drawable target handles both GIF and animated WebP without a second renderer.
        val gif = target("Lreddit/news/previews/FragmentGifPreview;", "H0", emptyList(), "V")
        fun request(resource: String): Method = Fingerprint(
            definingClass = MANAGER, parameters = emptyList(), returnType = BUILDER,
            custom = { method, _ ->
                method.implementation?.instructions?.any {
                    it.opcode == Opcode.CONST_CLASS &&
                            (it as ReferenceInstruction).reference.toString() == resource
                } == true
            },
        ).method
        val asGif = request("Lcom/bumptech/glide/load/resource/gif/GifDrawable;")
        val asDrawable = request("Landroid/graphics/drawable/Drawable;")
        bridge("gifRequest", 4, """
            check-cast p0, $MANAGER
            invoke-static {p1}, $LINKS->canHandle($STRING)Z
            move-result v0
            if-eqz v0, :native
            invoke-virtual {p0}, $asDrawable
            move-result-object v0
            return-object v0
            :native
            invoke-virtual {p0}, $asGif
            move-result-object v0
            return-object v0
        """)
        setting.patchMethod(gif) {
            val site = implementation!!.instructions.withIndex().single {
                (it.value as? ReferenceInstruction)?.reference.toString() == asGif.toString()
            }.index
            val register = getInstruction<FiveRegisterInstruction>(site).registerC
            replaceInstruction(site, "invoke-static {v$register, v1}, $NATIVE->gifRequest($OBJECT$STRING)$OBJECT")
            addInstructions(site + 2, "check-cast v$register, $BUILDER")
        }
        val callback = Fingerprint(
            definingClass = "Lreddit/news/previews/FragmentGifPreview\$1;",
            parameters = listOf(OBJECT, OBJECT, "Lcom/bumptech/glide/request/target/Target;",
                "Lcom/bumptech/glide/load/DataSource;", "Z"), returnType = "Z",
        )
        val notify = method(callback.classDef.type, "a",
            listOf("Lcom/bumptech/glide/load/resource/gif/GifDrawable;", OBJECT,
                "Lcom/bumptech/glide/request/target/Target;", "Lcom/bumptech/glide/load/DataSource;", "Z"), "Z")
        require(notify.implementation!!.instructions.first().opcode == Opcode.IGET_OBJECT &&
                (notify.implementation!!.instructions.first() as TwoRegisterInstruction).let {
                    it.registerA == notify.implementation!!.registerCount - 5 &&
                            it.registerB == notify.implementation!!.registerCount - 6
                }) {
            "Relay GIF success callback must overwrite its unused resource argument"
        }
        setting.patchMethod(callback) {
            require(getInstruction(0).opcode == Opcode.CHECK_CAST) { "Unsupported Relay GIF callback" }
            // Its notification method overwrites this argument before reading it.
            replaceInstruction(0, "const/4 p1, 0x0")
        }

        // HLS uses its own source; other Reddit CDNs can also carry separate DASH audio.
        val redditVideo = method("Lreddit/news/utils/RedditUtils;", "r", listOf("Lokhttp3/HttpUrl;"), "Z")
        bridge("isRedditVideo", 3, """
            check-cast p0, Lokhttp3/HttpUrl;
            invoke-virtual {p0}, Lokhttp3/HttpUrl;->toString()$STRING
            move-result-object v0
            invoke-static {v0}, $LINKS->isHls($STRING)Z
            move-result v0
            if-eqz v0, :native
            const/4 v0, 0x0
            return v0
            :native
            invoke-virtual {p0}, Lokhttp3/HttpUrl;->toString()$STRING
            move-result-object v0
            invoke-static {v0}, $LINKS->canHandle($STRING)Z
            move-result v0
            if-nez v0, :reddit_media
            invoke-static {p0}, $redditVideo
            move-result v0
            return v0
            :reddit_media
            const/4 v0, 0x1
            return v0
        """)
        setting.patchMethod(target("Lreddit/news/previews/FragmentVideoPreview;", "Q0", emptyList(), "V")) {
            val urlField = implementation!!.instructions.mapNotNull {
                (it as? ReferenceInstruction)?.reference as? FieldReference
            }.filter { it.definingClass == BASE && it.type == STRING }.distinctBy { it.toString() }.single()
            val site = implementation!!.instructions.withIndex().single {
                (it.value as? ReferenceInstruction)?.reference.toString() == redditVideo.toString()
            }.index
            val register = getInstruction<FiveRegisterInstruction>(site).registerC
            replaceInstruction(site, "invoke-static {v$register}, $NATIVE->isRedditVideo($OBJECT)Z")
            val hls = implementation!!.instructions.withIndex().single {
                (it.value as? ReferenceInstruction)?.reference.let { reference ->
                    reference is StringReference && reference.string == ".m3u8"
                }
            }.index
            val hlsCall = getInstruction<FiveRegisterInstruction>(hls + 1)
            require(hlsCall.opcode == Opcode.INVOKE_VIRTUAL) { "Unsupported Relay HLS selector" }
            replaceInstruction(hls, "iget-object v${hlsCall.registerD}, p0, $urlField")
            replaceInstruction(hls + 1,
                "invoke-static {v${hlsCall.registerC}, v${hlsCall.registerD}}, $VIEWER->isHls($STRING$STRING)Z")
        }
    }
}
