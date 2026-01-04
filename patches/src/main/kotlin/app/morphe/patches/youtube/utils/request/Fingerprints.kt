package app.morphe.patches.youtube.utils.request

import app.morphe.util.fingerprint.legacyFingerprint
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val buildInitPlaybackRequestFingerprint = legacyFingerprint(
    name = "buildInitPlaybackRequestFingerprint",
    returnType = "Lorg/chromium/net/UrlRequest\$Builder;",
    parameters = listOf("L"),
    opcodes = listOf(
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET_OBJECT, // Moves the request URI string to a register to build the request with.
    ),
    strings = listOf(
        "Content-Type",
        "Range",
    ),
    customFingerprint = { method, _ ->
        indexOfUriToStringInstruction(method) >= 0 &&
                indexOfMapInstruction(method) >= 0 &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
    },
)

internal fun indexOfMapInstruction(method: Method) =
    method.indexOfFirstInstruction {
        opcode == Opcode.IGET_OBJECT &&
                getReference<FieldReference>()?.type == "Ljava/util/Map;"
    }

internal fun indexOfUriToStringInstruction(method: Method) =
    method.indexOfFirstInstruction {
        opcode == Opcode.INVOKE_VIRTUAL &&
                getReference<MethodReference>().toString() == "Landroid/net/Uri;->toString()Ljava/lang/String;"
    }
