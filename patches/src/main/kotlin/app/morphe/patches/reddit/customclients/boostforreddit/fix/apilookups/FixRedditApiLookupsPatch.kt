/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.apilookups

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRedditApiLookupsPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PAGINATOR = "Lnet/dean/jraw/paginators/Paginator;"
private const val HISTORY = "Lnet/dean/jraw/paginators/SpecificPaginator;"
private const val ASYNC = "Landroid/os/AsyncTask;"
private const val LISTING = "Lnet/dean/jraw/models/Listing;"

private val historyPagingPatch = patch(default = false) {
    compatibleWith(*AppCompatibility.Boost)
    execute {
        // Use the shim's cursor and bounded fallback instead of the native retry on any 403.
        Fingerprint(
            definingClass = HISTORY,
            name = "next",
            returnType = LISTING,
            parameters = listOf("Z"),
        ).method.addInstructions(0, """
            invoke-super {p0, p1}, $PAGINATOR->next(Z)$LISTING
            move-result-object v0
            return-object v0
        """)
        Fingerprint(
            returnType = "V",
            parameters = listOf(PAGINATOR, "Z"),
            strings = listOf("paginator_limit"),
        ).method.apply {
            val instructions = checkNotNull(implementation).instructions
            val enqueue = instructions.indices.single {
                instructions[it].getReference<MethodReference>()?.toString() ==
                    "$ASYNC->execute([Ljava/lang/Object;)$ASYNC"
            }
            val native = getInstruction<FiveRegisterInstruction>(enqueue)
            check(native.opcode == Opcode.INVOKE_VIRTUAL && native.registerC == 3 && native.registerD == 4 &&
                instructions[enqueue + 1].opcode == Opcode.RETURN_VOID
            ) { "Unexpected Boost listing task dispatch" }
            // v0 is dead here. Leave every other listing on Boost's normal serial queue.
            addInstructionsWithLabels(enqueue, """
                iget-object v0, v3, La93;->c:$PAGINATOR
                instance-of v0, v0, $HISTORY
                if-eqz v0, :morphe_serial_queue
                sget-object v0, $ASYNC->THREAD_POOL_EXECUTOR:Ljava/util/concurrent/Executor;
                invoke-virtual {v3, v0, v4}, $ASYNC->executeOnExecutor(Ljava/util/concurrent/Executor;[Ljava/lang/Object;)$ASYNC
                return-void
            """, ExternalLabel("morphe_serial_queue", instructions[enqueue]))
        }
    }
}

@Suppress("unused")
val fixRedditApiLookups = fixRedditApiLookupsPatch(
    extensionPatch = ExtensionPatches.Boost,
    compatible = AppCompatibility.Boost,
    extraDependency = historyPagingPatch,
)
