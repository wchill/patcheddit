/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension.mapping

import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.AnnotationVisibility
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.debug.StartLocal
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.*
import com.android.tools.smali.dexlib2.iface.value.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference

internal fun ClassDef.forEachReference(runtimeOnly: Boolean = false, action: (Reference) -> Unit) {
    val visitor = ReferenceVisitor(runtimeOnly, action)
    superclass?.let(visitor::type)
    interfaces.forEach(visitor::type)
    annotations.forEach(visitor::annotation)
    for (field in fields) {
        visitor.type(field.type)
        field.annotations.forEach(visitor::annotation)
        field.initialValue?.let(visitor::value)
    }
    for (method in methods) {
        visitor.type(method.returnType)
        method.annotations.forEach(visitor::annotation)
        for (parameter in method.parameters) {
            visitor.type(parameter.type)
            parameter.annotations.forEach(visitor::annotation)
        }
        val implementation = method.implementation ?: continue
        for (instruction in implementation.instructions) {
            if (instruction is ReferenceInstruction) visitor.reference(instruction.reference)
            if (instruction is DualReferenceInstruction) visitor.reference(instruction.reference2)
        }
        implementation.tryBlocks.forEach { block ->
            block.exceptionHandlers.forEach { handler -> handler.exceptionType?.let(visitor::type) }
        }
        if (!runtimeOnly) {
            implementation.debugItems.filterIsInstance<StartLocal>().forEach { it.type?.let(visitor::type) }
        }
    }
}

private class ReferenceVisitor(private val runtimeOnly: Boolean, private val action: (Reference) -> Unit) {
    fun type(type: String) = action(ImmutableTypeReference(type))

    fun reference(reference: Reference) {
        action(reference)
        when (reference) {
            is MethodReference -> {
                type(reference.definingClass)
                type(reference.returnType)
                reference.parameterTypes.forEach { type(it.toString()) }
            }
            is FieldReference -> {
                type(reference.definingClass)
                type(reference.type)
            }
            is MethodProtoReference -> {
                type(reference.returnType)
                reference.parameterTypes.forEach { type(it.toString()) }
            }
            is MethodHandleReference -> reference(reference.memberReference)
            is CallSiteReference -> {
                reference(reference.methodHandle)
                reference(reference.methodProto)
                reference.extraArguments.forEach(::value)
            }
        }
    }

    fun annotation(annotation: Annotation) {
        if (runtimeOnly && annotation.visibility == AnnotationVisibility.BUILD) return
        type(annotation.type)
        annotation.elements.forEach { value(it.value) }
    }

    fun value(value: EncodedValue) {
        when (value) {
            is TypeEncodedValue -> type(value.value)
            is FieldEncodedValue -> reference(value.value)
            is MethodEncodedValue -> reference(value.value)
            is EnumEncodedValue -> reference(value.value)
            is MethodTypeEncodedValue -> reference(value.value)
            is MethodHandleEncodedValue -> reference(value.value)
            is StringEncodedValue -> action(ImmutableStringReference(value.value))
            is ArrayEncodedValue -> value.value.forEach(::value)
            is AnnotationEncodedValue -> {
                type(value.type)
                value.elements.forEach { value(it.value) }
            }
        }
    }
}
