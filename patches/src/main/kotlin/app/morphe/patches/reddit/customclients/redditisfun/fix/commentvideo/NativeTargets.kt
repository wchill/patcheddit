/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.commentvideo

import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val VIDEO = "Lcom/andrewshu/android/reddit/things/objects/ThreadMediaRedditVideo;"
internal const val RIF_VIDEO_HOOK = "Lapp/morphe/extension/redditisfun/CommentVideoPatch;"
internal const val SOURCE_FIELD = "morpheCommentVideoUri"

internal data class VideoCallback(val method: Method, val constructor: Method, val sourceField: String)
internal data class RifCommentVideoTargets(
    val callbacks: List<VideoCallback>,
    val dashSetter: Method,
    val hlsSetter: Method,
    val gifSetter: Method,
)

internal fun resolveRifCommentVideoTargets(definitions: List<ClassDef>): RifCommentVideoTargets {
    val error = "Unsupported RIF 5.6.22 native comment-video integration."
    val classes = definitions.associateBy { it.type }
    val free = resolveRifNativeSku(definitions) == RifNativeSku.FREE
    val prefix = if (free) "Lj2/" else "Lk2/"
    val listener = if (free) "Lv1/y;" else "Lw1/y;"
    fun method(owner: String, name: String, parameters: List<String>, returns: String): Method =
        classes[owner]?.methods?.singleOrNull {
            it.name == name && it.parameterTypes.map(CharSequence::toString) == parameters && it.returnType == returns
        }?.also { require(it.implementation != null && !AccessFlags.STATIC.isSet(it.accessFlags)) { error } }
            ?: error(error)

    val serializer = classes["${VIDEO.dropLast(1)}\$\$JsonObjectMapper;"]?.methods?.singleOrNull {
        it.name == "serialize" && it.parameterTypes.size == 3 && it.parameterTypes.first() == VIDEO &&
                it.returnType == "V"
    }?.implementation?.instructions?.toList() ?: error(error)
    fun setter(name: String, getter: String, type: String, jsonKey: String): Method {
        val set = method(VIDEO, name, listOf(type), "V")
        val get = method(VIDEO, getter, emptyList(), type)
        val setCode = set.implementation!!.instructions.toList()
        val getCode = get.implementation!!.instructions.toList()
        val field = (setCode.firstOrNull() as? ReferenceInstruction)?.reference as? FieldReference
        require(AccessFlags.PUBLIC.isSet(set.accessFlags) && AccessFlags.PUBLIC.isSet(get.accessFlags)
                && setCode.size == 2 && getCode.size == 2 && field?.definingClass == VIDEO && field.type == type
                && field.toString() == ((getCode.first() as? ReferenceInstruction)?.reference as? FieldReference).toString()
                && setCode.first().opcode in setOf(Opcode.IPUT_OBJECT, Opcode.IPUT_BOOLEAN)
                && getCode.first().opcode in setOf(Opcode.IGET_OBJECT, Opcode.IGET_BOOLEAN)) { error }
        val keyIndex = serializer.indices.singleOrNull {
            ((serializer[it] as? ReferenceInstruction)?.reference as? StringReference)?.string == jsonKey
        } ?: error(error)
        require(serializer.drop(keyIndex + 1).take(3).any {
            it.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.toString() == get.toString()
        }) { error }
        return set
    }
    val dash = setter(if (free) "k" else "l", if (free) "c" else "a", "Ljava/lang/String;", "dash_url")
    val hls = setter(if (free) "o" else "p", if (free) "h" else "g", "Ljava/lang/String;", "hls_url")
    val gif = setter(if (free) "r" else "t", if (free) "j" else "k", "Z", "is_gif")
    val constructor = method(VIDEO, "<init>", emptyList(), "V")
    require(AccessFlags.PUBLIC.isSet(constructor.accessFlags)) { error }
    val callbacks = listOf("a" to "h0", "c" to "f0").map { (family, callbackName) ->
        val owner = "$prefix$family\$b;"
        val outer = "$prefix$family;"
        val callback = method(owner, callbackName, listOf(VIDEO), "V")
        val nativeConstructor = method(owner, "<init>",
            listOf("Landroid/net/Uri;", "Landroid/content/Context;", listener, outer), "V")
        require(callback.accessFlags == AccessFlags.PROTECTED.value &&
                callback.implementation!!.instructions.any {
                    val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.definingClass == outer && reference.name == "n" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf(outer, VIDEO) &&
                            reference.returnType == VIDEO && it.opcode == Opcode.INVOKE_STATIC
                }) { error }
        if (family == "a") {
            require(classes.getValue(owner).fields.singleOrNull { it.name == "t" }?.type == "Landroid/net/Uri;" &&
                    nativeConstructor.implementation!!.instructions.any {
                        (it as? ReferenceInstruction)?.reference?.toString() == "$owner->t:Landroid/net/Uri;" &&
                                it.opcode == Opcode.IPUT_OBJECT
                    }) { error }
            VideoCallback(callback, nativeConstructor, "t")
        } else {
            require(classes.getValue(owner).superclass == "${prefix}e;" &&
                    nativeConstructor.implementation!!.instructions.first().opcode == Opcode.INVOKE_DIRECT &&
                    classes.getValue(owner).fields.none { it.name == SOURCE_FIELD }) { error }
            VideoCallback(callback, nativeConstructor, SOURCE_FIELD)
        }
    }
    return RifCommentVideoTargets(callbacks, dash, hls, gif)
}
