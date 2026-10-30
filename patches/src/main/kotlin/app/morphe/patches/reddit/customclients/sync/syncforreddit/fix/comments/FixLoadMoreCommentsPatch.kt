/*
 * Copyright 2026 trulow.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.comments

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.NETWORK_RESPONSE
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.VOLLEY_REQUEST
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/morphe/extension/syncforreddit/FixLoadMoreCommentsPatch;"
private const val STRING = "Ljava/lang/String;"

/** RedditRequest(Context, int method, String url, Listener, ErrorListener). */
private val SUPER_CONSTRUCTOR_PARAMETERS = listOf(
    "Landroid/content/Context;",
    "I",
    STRING,
    "Lcom/android/volley/Response\$Listener;",
    "Lcom/android/volley/Response\$ErrorListener;",
)

@Suppress("unused")
val fixLoadMoreCommentsPatch = bytecodePatch(
    name = "Fix loading more comments",
    description = "Fixes \"Error loading more comments\" since Reddit blocked /api/morechildren for " +
        "third-party clients. The missing comments are loaded from /comments instead. At the bottom of " +
        "very large threads Reddit's 500 comment limit can still leave some out.",
    default = true,
) {
    category(PatchCategory.COMMENTS)
    compatibleWith(*AppCompatibility.SyncForReddit)
    dependsOn(ExtensionPatches.Sync)

    execute {
        // Remember the parent of the "more" item: the request itself is not given it.
        RequestMoreFingerprint.method.addInstructions(
            0,
            "invoke-static/range { p1 .. p1 }, $EXTENSION_CLASS->setMoreItem(Ljava/lang/Object;)V",
        )

        MoreRequestConstructorFingerprint.method.apply {
            // The super constructor call: RedditRequest.<init>(this, Context, int method, String url, ...).
            val superclass = MoreRequestConstructorFingerprint.classDef.superclass
            val superIndex = instructions.indexOfFirst { instruction ->
                (instruction.opcode == Opcode.INVOKE_DIRECT || instruction.opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                    ((instruction as ReferenceInstruction).reference as MethodReference).let {
                        it.name == "<init>" && it.definingClass == superclass &&
                            it.parameterTypes.map(CharSequence::toString) == SUPER_CONSTRUCTOR_PARAMETERS
                    }
            }
            if (superIndex < 0) throw PatchException("Super constructor call not found")

            val (methodRegister, urlRegister) = when (val call = instructions[superIndex]) {
                is RegisterRangeInstruction -> (call.startRegister + 2) to (call.startRegister + 3)
                is FiveRegisterInstruction -> call.registerE to call.registerF
                else -> throw PatchException("Unexpected super constructor call: ${call.opcode}")
            }
            val methodCall = if (urlRegister == methodRegister + 1) {
                "invoke-static/range { v$methodRegister .. v$urlRegister }"
            } else {
                "invoke-static { v$methodRegister, v$urlRegister }"
            }

            // Swap the morechildren POST for a GET of /comments right before the super constructor uses them.
            addInstructions(
                superIndex,
                """
                    invoke-static/range { v$urlRegister .. v$urlRegister }, $EXTENSION_CLASS->moreUrl($STRING)$STRING
                    move-result-object v$urlRegister
                    $methodCall, $EXTENSION_CLASS->moreMethod(I$STRING)I
                    move-result v$methodRegister
                """,
            )

            // Hand over link id (p3), children (p5) and sort (p8). v0-v2 are written by the constructor
            // before it reads them.
            val locals = implementation!!.registerCount - parameters.size - 1
            if (locals < 3) throw PatchException("Expected at least 3 local registers, found $locals")
            addInstructions(
                0,
                """
                    move-object/from16 v0, p3
                    move-object/from16 v1, p5
                    move-object/from16 v2, p8
                    invoke-static { v0, v1, v2 }, $EXTENSION_CLASS->setMoreArgs($STRING$STRING$STRING)V
                """,
            )
        }

        // Convert the /comments reply into the morechildren "things" list Sync parses.
        MoreParseResponseFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual/range { p0 .. p0 }, $VOLLEY_REQUEST->getUrl()$STRING
                move-result-object v0
                move-object/from16 v1, p1
                invoke-static { v0, v1 }, $EXTENSION_CLASS->moreResponse($STRING$NETWORK_RESPONSE)$NETWORK_RESPONSE
                move-result-object v1
                move-object/from16 p1, v1
            """,
        )
    }
}
