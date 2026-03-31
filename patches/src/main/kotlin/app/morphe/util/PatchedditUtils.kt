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
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

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
