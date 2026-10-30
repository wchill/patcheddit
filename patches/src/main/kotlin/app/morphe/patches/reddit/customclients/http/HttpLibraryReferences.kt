/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import app.morphe.patches.reddit.customclients.misc.extension.mapping.forEachReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal const val HTTP_LIBRARY_PREFIX = "Lapp/morphe/extension/shared/http/libraries/"

internal fun privateHttpType(type: String): String {
    // OkHttp 5 moved these OkHttp 4 utility entry points to its common JVM facade.
    val target = if (type == "Lokhttp3/internal/Util;") "Lokhttp3/internal/_UtilCommonKt;" else type
    return if (target.startsWith("Lokhttp3/") || target.startsWith("Lokio/")) {
        HTTP_LIBRARY_PREFIX + target.removePrefix("L")
    } else {
        target
    }
}

internal fun privateSharedLibraryType(type: String): String =
    if (type.startsWith("Lkotlin/")) {
        HTTP_LIBRARY_PREFIX + type.removePrefix("L")
    } else {
        privateHttpType(type)
    }

internal fun verifyHttpLibraryReferences(
    definitions: Collection<ClassDef>,
    callerTypes: Set<String>,
) {
    val classes = definitions.associateBy { it.type }
    val errors = linkedSetOf<String>()
    fun checkType(type: String, caller: String) {
        val element = type.trimStart('[')
        if (element.startsWith(HTTP_LIBRARY_PREFIX) && element !in classes) {
            errors += "$caller references missing HTTP class $element"
        }
    }
    fun resolve(owner: String, matches: (ClassDef) -> Int?): Int? {
        val pending = ArrayDeque(listOf(owner))
        val visited = hashSetOf<String>()
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (!visited.add(type)) continue
            val cls = classes[type] ?: continue
            matches(cls)?.let { return it }
            cls.superclass?.let(pending::add)
            pending.addAll(cls.interfaces)
        }
        return null
    }
    for (cls in definitions.filter { it.type in callerTypes }) {
        cls.forEachReference(runtimeOnly = true) { reference ->
            if (reference is TypeReference) checkType(reference.type, cls.type)
        }
        for (method in cls.methods) {
            val caller = "${cls.type}->${method.name}"
            for (instruction in method.implementation?.instructions ?: emptyList()) {
                when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                    is MethodReference -> {
                        if (!reference.definingClass.startsWith(HTTP_LIBRARY_PREFIX)) continue
                        val declaration: (ClassDef) -> Int? = { target ->
                            target.methods.firstOrNull {
                                it.name == reference.name && it.returnType == reference.returnType &&
                                    it.parameterTypes.map(CharSequence::toString) ==
                                    reference.parameterTypes.map(CharSequence::toString)
                            }?.accessFlags
                        }
                        val flags = if (reference.name == "<init>") {
                            classes[reference.definingClass]?.let(declaration)
                        } else {
                            resolve(reference.definingClass, declaration)
                        }
                        if (flags == null) {
                            errors += "$caller references missing HTTP method $reference"
                        } else {
                            val static = instruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
                            if (AccessFlags.STATIC.isSet(flags) != static) {
                                errors += "$caller has an HTTP static/instance mismatch for $reference"
                            }
                            if (AccessFlags.PRIVATE.isSet(flags) && cls.type != reference.definingClass) {
                                errors += "$caller references private HTTP method $reference"
                            }
                        }
                    }
                    is FieldReference -> {
                        if (!reference.definingClass.startsWith(HTTP_LIBRARY_PREFIX)) continue
                        val flags = resolve(reference.definingClass) { target ->
                            target.fields.firstOrNull {
                                it.name == reference.name && it.type == reference.type
                            }?.accessFlags
                        }
                        if (flags == null) {
                            errors += "$caller references missing HTTP field $reference"
                        } else {
                            val static = instruction.opcode.name.startsWith("sget", true) ||
                                instruction.opcode.name.startsWith("sput", true)
                            if (AccessFlags.STATIC.isSet(flags) != static) {
                                errors += "$caller has an HTTP static/instance mismatch for $reference"
                            }
                            if (AccessFlags.PRIVATE.isSet(flags) && cls.type != reference.definingClass) {
                                errors += "$caller references private HTTP field $reference"
                            }
                        }
                    }
                }
            }
        }
    }
    require(errors.isEmpty()) {
        "HTTP library redirection is incompatible (${errors.size} errors):\n${errors.joinToString("\n")}"
    }
}
