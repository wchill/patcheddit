package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.writeRegister
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n


internal val createOkHttpClientFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(),
    custom = { method, classDef ->
        // There are four functions (each creating a client) defined in this file with very similar fingerprints.
        // We're looking for the one that only creates one object (the builder) and sets client options true
        // (thus never reloading the register with a 0).
        classDef.sourceFile == "OkHttpHelper.java" &&
                method.instructions.count { it.opcode == Opcode.NEW_INSTANCE } == 1 &&
                method.indexOfFirstInstruction {
                    opcode == Opcode.CONST_4 && writeRegister == 1 && (this as Instruction11n).narrowLiteral == 0
                } == -1
    }
)

internal val getDefaultUserAgentFingerprint = Fingerprint(
    custom = { method, classDef ->
        method.name == "getDefaultUserAgent" && classDef.type == EXTENSION_CLASS_DESCRIPTOR
    }
)

internal val getOriginalUserAgentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    custom = { _, classDef -> classDef.sourceFile == "AccountSingleton.java" }
)
