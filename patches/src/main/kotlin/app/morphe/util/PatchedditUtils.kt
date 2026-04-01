/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.util

import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethodParameter
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal fun Match.replaceStringMatchesWithFunc(methodName: String) {
    method.let {
        stringMatches.forEach { match ->
            val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
            it.replaceInstruction(match.index, "nop")
            it.addInstructions(match.index,
            """
                    invoke-static {}, $methodName()Ljava/lang/String;
                    move-result-object v$register
                """
            )
        }
    }
}

internal fun ClassDef.retargetClass(unobfuscatedType: String, obfuscatedType: String): MutableClass {
    val newSuperclass = if (superclass == unobfuscatedType) obfuscatedType else superclass
    val newMethods = methods.map { method ->
        method.cloneMutable(
            parameters = method.parameters.map { parameter ->
                if (parameter.type == unobfuscatedType) {
                    ImmutableMethodParameter(
                        obfuscatedType,
                        parameter.annotations,
                        parameter.name
                    )
                } else parameter
            },
            returnType = if (method.returnType == unobfuscatedType) obfuscatedType else method.returnType
        )
    }
    val newInterfaces = interfaces.map { if (it == unobfuscatedType) obfuscatedType else it }
    val newAnnotations = annotations.map { annotation ->
        if (annotation.type == unobfuscatedType) {
            ImmutableAnnotation(
                annotation.visibility,
                obfuscatedType,
                annotation.elements
            )
        } else annotation
    }
    val newFields = fields.map { field ->
        if (field.type == unobfuscatedType) {
            ImmutableField(
                field.definingClass,
                field.name,
                obfuscatedType,
                field.accessFlags,
                field.initialValue,
                field.annotations,
                field.hiddenApiRestrictions
            )
        } else field
    }

    throw NotImplementedError("Retargeting to obfuscated type is not implemented yet.")
}
