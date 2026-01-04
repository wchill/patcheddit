package app.revanced.patches.youtube.video.playback

import app.revanced.util.fingerprint.legacyFingerprint
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstruction
import app.revanced.util.indexOfFirstInstructionReversed
import app.revanced.util.or
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal val deviceDimensionsModelToStringFingerprint = legacyFingerprint(
    name = "deviceDimensionsModelToStringFingerprint",
    returnType = "L",
    strings = listOf("minh.", ";maxh.")
)

internal val playbackSpeedChangedFromRecyclerViewFingerprint = legacyFingerprint(
    name = "playbackSpeedChangedFromRecyclerViewFingerprint",
    returnType = "L",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf("L"),
    opcodes = listOf(
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET,
        Opcode.INVOKE_VIRTUAL
    ),
    customFingerprint = { method, _ ->
        method.indexOfFirstInstruction {
            opcode == Opcode.IGET &&
                    getReference<FieldReference>()?.type == "F"
        } >= 0
    }
)

internal val mediaLibPlayerLoadVideoFingerprint = legacyFingerprint(
    name = "mediaLibPlayerLoadVideoFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    opcodes = listOf(Opcode.CONST_HIGH16),
    strings = listOf("Volume: %f"),
    customFingerprint = { method, _ ->
        indexOfPlaybackSpeedInstruction(method) >= 0
    }
)

internal fun indexOfPlaybackSpeedInstruction(method: Method, startIndex: Int = 0) =
    method.indexOfFirstInstruction(startIndex) {
        opcode == Opcode.IGET &&
                getReference<FieldReference>()?.type == "F"
    }

internal val qualityChangedFromRecyclerViewFingerprint = legacyFingerprint(
    name = "qualityChangedFromRecyclerViewFingerprint",
    returnType = "L",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    parameters = listOf("L"),
    opcodes = listOf(
        Opcode.IGET,  // Video resolution int (human readable).
        Opcode.IGET_OBJECT,  // Video resolution string (human readable).
        Opcode.IGET_BOOLEAN,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_DIRECT,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.GOTO,
        Opcode.CONST_4,
        Opcode.IF_NE,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET,
    )
)

internal val qualityMenuViewInflateOnItemClickFingerprint = legacyFingerprint(
    name = "qualityMenuViewInflateOnItemClickFingerprint",
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    customFingerprint = { method, _ ->
        method.name == "onItemClick" &&
                indexOfContextInstruction(method) >= 0
    }
)

internal fun indexOfContextInstruction(method: Method) =
    method.indexOfFirstInstructionReversed {
        opcode == Opcode.IGET_OBJECT &&
                getReference<FieldReference>()?.type == "Landroid/content/Context;"
    }


internal val videoQualityItemOnClickParentFingerprint = legacyFingerprint(
    name = "videoQualityItemOnClickParentFingerprint",
    returnType = "V",
    strings = listOf("VIDEO_QUALITIES_MENU_BOTTOM_SHEET_FRAGMENT")
)

internal val videoQualityItemOnClickFingerprint = legacyFingerprint(
    name = "videoQualityItemOnClickFingerprint",
    returnType = "V",
    parameters = listOf(
        "Landroid/widget/AdapterView;",
        "Landroid/view/View;",
        "I",
        "J"
    ),
    customFingerprint = { method, _ ->
        method.name == "onItemClick"
    }
)

internal val vp9CapabilityFingerprint = legacyFingerprint(
    name = "vp9CapabilityFingerprint",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    returnType = "Z",
    strings = listOf(
        "vp9_supported",
        "video/x-vnd.on2.vp9"
    )
)
