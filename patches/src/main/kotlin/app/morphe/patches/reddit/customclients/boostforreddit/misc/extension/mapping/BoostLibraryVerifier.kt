/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.mapping

import app.morphe.patches.reddit.customclients.misc.extension.mapping.forEachReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.lang.reflect.Modifier

private fun Class<*>.descriptor(): String = when (name) {
    "void" -> "V"
    "boolean" -> "Z"
    "byte" -> "B"
    "short" -> "S"
    "char" -> "C"
    "int" -> "I"
    "long" -> "J"
    "float" -> "F"
    "double" -> "D"
    else -> if (isArray) name.replace('.', '/') else "L${name.replace('.', '/')};"
}

private fun frameworkInterfaceDefinition(type: String): ClassDef? {
    if (!type.startsWith("Ljava/") && !type.startsWith("Ljavax/")) return null
    val runtime = try {
        Class.forName(type.removePrefix("L").removeSuffix(";").replace('/', '.'), false, ClassDef::class.java.classLoader)
    } catch (_: ClassNotFoundException) {
        return null
    }
    if (!runtime.isInterface && runtime != Any::class.java) return null
    return ImmutableClassDef(
        type, runtime.modifiers, runtime.superclass?.descriptor(),
        runtime.interfaces.map { it.descriptor() }, null, emptyList(),
        runtime.declaredFields.filter { Modifier.isPublic(it.modifiers) }.map {
            ImmutableField(type, it.name, it.type.descriptor(), it.modifiers, null, emptyList(), emptySet())
        },
        runtime.declaredMethods.filter {
            !Modifier.isPrivate(it.modifiers) && !Modifier.isStatic(it.modifiers)
        }.map {
            ImmutableMethod(
                type, it.name,
                it.parameterTypes.map { parameter -> ImmutableMethodParameter(parameter.descriptor(), emptySet(), null) },
                it.returnType.descriptor(), it.modifiers, emptySet(), emptySet(), null,
            )
        },
    )
}

private fun MethodReference.matches(other: MethodReference) =
    name == other.name && returnType == other.returnType &&
        parameterTypes.map(CharSequence::toString) == other.parameterTypes.map(CharSequence::toString)

internal fun verifyBoostLibraryReferences(
    definitions: Collection<ClassDef>,
    extensionTypes: Set<String>,
) {
    val classes = definitions.associateBy { it.type }
    require(classes.size == definitions.size) { "Duplicate class definitions in patched Boost" }
    val extension = definitions.filter { it.type in extensionTypes }
    val errors = linkedSetOf<String>()
    val frameworkInterfaces = mutableMapOf<String, ClassDef?>()
    val framework = listOf(
        "Ljava/", "Ljavax/", "Landroid/", "Ldalvik/",
        "Lorg/json/", "Lorg/xml/", "Lorg/xmlpull/", "Lorg/w3c/",
    )
    fun external(type: String) = framework.any(type::startsWith)
    fun checkType(type: String, caller: String) {
        val element = type.trimStart('[')
        if (element.startsWith('L') && element !in classes && !external(element)) {
            errors += "$caller references missing class $element"
        }
    }
    for (cls in definitions) {
        if (cls.type in extensionTypes) continue
        cls.forEachReference(runtimeOnly = true) { reference ->
            if (reference is TypeReference &&
                reference.type.trimStart('[').startsWith("Lapp/morphe/extension/")
            ) {
                checkType(reference.type, cls.type)
            }
        }
    }
    fun resolve(owner: String, match: (ClassDef) -> Int?): Int? {
        val pending = ArrayDeque(listOf(owner))
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (!seen.add(type)) continue
            val definition = classes[type] ?: run {
                if (type !in frameworkInterfaces) frameworkInterfaces[type] = frameworkInterfaceDefinition(type)
                frameworkInterfaces[type]
            }
            if (definition == null) {
                if (external(type) && type != "Ljava/lang/Object;") return -1
                continue
            }
            match(definition)?.let { return it }
            definition.superclass?.let(pending::add)
            pending.addAll(definition.interfaces)
        }
        return null
    }
    val objectMethods = setOf(
        "getClass", "toString", "equals", "hashCode", "clone", "finalize", "wait",
        "notify", "notifyAll", "<init>",
    )
    for (cls in extension) {
        cls.forEachReference(runtimeOnly = true) { reference ->
            when (reference) {
                is TypeReference -> checkType(reference.type, cls.type)
                is MethodReference -> {
                    val owner = reference.definingClass
                    if (external(owner) || owner.startsWith('[')) return@forEachReference
                    val declaration: (ClassDef) -> Int? = { definition ->
                        definition.methods.firstOrNull {
                            it.matches(reference)
                        }?.accessFlags
                    }
                    val flags = if (reference.name == "<init>") {
                        classes[owner]?.let(declaration)
                    } else {
                        resolve(owner, declaration)
                    }
                    if (flags == null) errors += "${cls.type} references missing method $reference"
                }
                is FieldReference -> {
                    if (external(reference.definingClass)) return@forEachReference
                    val flags = resolve(reference.definingClass) { definition ->
                        definition.fields.firstOrNull {
                            it.name == reference.name && it.type == reference.type
                        }?.accessFlags
                    }
                    if (flags == null) errors += "${cls.type} references missing field $reference"
                }
            }
        }
        cls.superclass?.let { checkType(it, cls.type) }
        cls.interfaces.forEach { checkType(it, cls.type) }
        cls.fields.forEach { checkType(it.type, "${cls.type}->${it.name}") }
        for (method in cls.methods) {
            val caller = "${cls.type}->${method.name}"
            method.parameterTypes.forEach { checkType(it.toString(), caller) }
            checkType(method.returnType, caller)
            for (instruction in method.implementation?.instructions?.toList().orEmpty()) {
                val reference = (instruction as? ReferenceInstruction)?.reference ?: continue
                when (reference) {
                    is TypeReference -> checkType(reference.type, caller)
                    is MethodReference -> {
                        checkType(reference.definingClass, caller)
                        val owner = reference.definingClass
                        if (owner !in classes && external(owner)) continue
                        if (owner == "Ljava/lang/Object;" && reference.name in objectMethods) continue
                        if (owner.startsWith('[') && reference.name in objectMethods) continue
                        val declared: (ClassDef) -> Int? = { definition ->
                            definition.methods.firstOrNull {
                                it.matches(reference)
                            }?.accessFlags
                        }
                        val flags = if (reference.name == "<init>") {
                            classes[owner]?.let(declared)
                        } else {
                            resolve(owner, declared)
                        }
                        if (flags == null) {
                            errors += "$caller references missing method $reference"
                        } else {
                            val static = instruction.opcode == Opcode.INVOKE_STATIC ||
                                instruction.opcode == Opcode.INVOKE_STATIC_RANGE
                            if (flags >= 0 && AccessFlags.STATIC.isSet(flags) != static) {
                                errors += "$caller has a static/instance mismatch for $reference"
                            }
                        }
                    }
                    is FieldReference -> {
                        checkType(reference.definingClass, caller)
                        if (reference.definingClass !in classes && external(reference.definingClass)) continue
                        val flags = resolve(reference.definingClass) { definition ->
                            definition.fields.firstOrNull {
                                it.name == reference.name && it.type == reference.type
                            }?.accessFlags
                        }
                        if (flags == null) {
                            errors += "$caller references missing field $reference"
                        } else if (flags >= 0) {
                            val static = instruction.opcode.name.startsWith("sget", ignoreCase = true) ||
                                instruction.opcode.name.startsWith("sput", ignoreCase = true)
                            if (AccessFlags.STATIC.isSet(flags) != static) {
                                errors += "$caller has a static/instance mismatch for $reference"
                            }
                        }
                    }
                }
            }
        }
        if (!AccessFlags.ABSTRACT.isSet(cls.accessFlags) && !AccessFlags.INTERFACE.isSet(cls.accessFlags)) {
            val pending = ArrayDeque(listOfNotNull(cls.superclass) + cls.interfaces)
            val seen = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val parent = pending.removeFirst()
                if (!seen.add(parent)) continue
                val inherited = classes[parent] ?: continue
                for (required in inherited.methods.filter { AccessFlags.ABSTRACT.isSet(it.accessFlags) }) {
                    val implementation = resolve(cls.type) { candidate ->
                        candidate.methods.firstOrNull {
                            it.matches(required) &&
                                !AccessFlags.ABSTRACT.isSet(it.accessFlags) &&
                                !AccessFlags.STATIC.isSet(it.accessFlags) &&
                                !AccessFlags.PRIVATE.isSet(it.accessFlags)
                        }?.accessFlags
                    }
                    if (implementation == null) {
                        errors += "${cls.type} does not implement inherited abstract method $required"
                    }
                }
                inherited.superclass?.let(pending::add)
                pending.addAll(inherited.interfaces)
            }
        }
    }
    require(errors.isEmpty()) {
        "Boost extension is incompatible with the app's library API (${errors.size} errors):\n" +
            errors.joinToString("\n")
    }
}
