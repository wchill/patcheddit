package app.revanced.patches.shared.litho

import app.revanced.util.containsLiteralInstruction
import app.revanced.util.fingerprint.legacyFingerprint
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstruction
import app.revanced.util.or
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val BUFFER_UPD_FEATURE_FLAG = 45419603L

internal val bufferUpbFeatureFlagFingerprint = legacyFingerprint(
    name = "bufferUpbFeatureFlagFingerprint",
    returnType = "L",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
    parameters = listOf("L"),
    literals = listOf(BUFFER_UPD_FEATURE_FLAG),
)

internal val byteBufferFingerprint = legacyFingerprint(
    name = "byteBufferFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf("I", "Ljava/nio/ByteBuffer;"),
    opcodes = listOf(
        null,
        Opcode.IF_EQZ,
        Opcode.IPUT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.SUB_INT_2ADDR,
        Opcode.IPUT,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IPUT,
        Opcode.RETURN_VOID,
        Opcode.CONST_4,
        Opcode.IPUT,
        Opcode.IPUT,
        Opcode.GOTO
    ),
    // Check method count and field count to support both YouTube and YouTube Music
    customFingerprint = { _, classDef ->
        classDef.methods.count() > 6
                && classDef.fields.count() > 4
    },
)

internal val emptyComponentFingerprint = legacyFingerprint(
    name = "emptyComponentFingerprint",
    accessFlags = AccessFlags.PRIVATE or AccessFlags.CONSTRUCTOR,
    parameters = emptyList(),
    strings = listOf("EmptyComponent"),
    customFingerprint = { _, classDef ->
        classDef.methods.filter { AccessFlags.STATIC.isSet(it.accessFlags) }.size == 1
    }
)

internal val componentContextParserFingerprint = legacyFingerprint(
    name = "componentContextParserFingerprint",
    strings = listOf("Number of bits must be positive"),
)

internal val componentContextParserLegacyFingerprint = legacyFingerprint(
    name = "componentContextParserLegacyFingerprint",
    returnType = "L",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    opcodes = listOf(
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET_OBJECT,
    ),
    customFingerprint = { method, _ ->
        method.indexOfFirstInstruction {
            opcode == Opcode.CONST_STRING &&
                    getReference<StringReference>()
                        ?.string.toString()
                        .startsWith("Error while converting")
        } >= 0
    }
)

internal val componentCreateFingerprint = legacyFingerprint(
    name = "componentCreateFingerprint",
    strings = listOf(
        "Element missing correct type extension",
        "Element missing type"
    )
)

internal val lithoThreadExecutorFingerprint = legacyFingerprint(
    name = "lithoThreadExecutorFingerprint",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.CONSTRUCTOR,
    parameters = listOf("I", "I", "I"),
    customFingerprint = { method, classDef ->
        classDef.superclass == "Ljava/util/concurrent/ThreadPoolExecutor;" &&
                method.containsLiteralInstruction(1L) // 1L = default thread timeout.
    }
)

/**
 * Since YouTube v19.18.41 and YT Music 7.01.53, pathBuilder is being handled by a different Method.
 */
internal val componentContextSubParserFingerprint = legacyFingerprint(
    name = "componentContextSubParserFingerprint",
    returnType = "L",
    strings = listOf("Number of bits must be positive"),
)

internal const val PATH_UPD_FEATURE_FLAG = 45631264L

internal val pathUpbFeatureFlagFingerprint = legacyFingerprint(
    name = "pathUpbFeatureFlagFingerprint",
    returnType = "Z",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = emptyList(),
    literals = listOf(PATH_UPD_FEATURE_FLAG),
)