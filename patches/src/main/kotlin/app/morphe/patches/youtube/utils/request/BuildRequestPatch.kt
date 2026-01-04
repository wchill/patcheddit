package app.morphe.patches.youtube.utils.request

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.buildRequestFingerprint
import app.morphe.patches.shared.buildRequestParentFingerprint
import app.morphe.patches.shared.indexOfEntrySetInstruction
import app.morphe.patches.shared.indexOfNewUrlRequestBuilderInstruction
import app.morphe.patches.youtube.utils.extension.sharedExtensionPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.fingerprint.methodOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private lateinit var buildInitPlaybackRequestMethod: MutableMethod
private var initPlaybackUrlRegister = 0
private var initPlaybackMapRegister = 0

private lateinit var buildRequestMethod: MutableMethod
private var urlRegister = 0
private var mapRegister = 0
private var offSet = 0

val buildRequestPatch = bytecodePatch(
    description = "buildRequestPatch",
) {
    dependsOn(sharedExtensionPatch)

    execute {
        buildInitPlaybackRequestFingerprint.methodOrThrow().apply {
            buildInitPlaybackRequestMethod = this
            val mapIndex = indexOfMapInstruction(this)
            val mapField = getInstruction<ReferenceInstruction>(mapIndex).reference

            val uriIndex = indexOfUriToStringInstruction(this) + 1
            initPlaybackUrlRegister =
                getInstruction<OneRegisterInstruction>(uriIndex).registerA

            initPlaybackMapRegister =
                findFreeRegister(uriIndex, false, initPlaybackUrlRegister)

            addInstruction(
                0,
                "iget-object v$initPlaybackMapRegister, p1, $mapField"
            )
        }

        buildRequestFingerprint.methodOrThrow(buildRequestParentFingerprint).apply {
            buildRequestMethod = this

            val newRequestBuilderIndex = indexOfNewUrlRequestBuilderInstruction(this)
            urlRegister =
                getInstruction<FiveRegisterInstruction>(newRequestBuilderIndex).registerD

            val entrySetIndex = indexOfEntrySetInstruction(this)
            val isLegacyTarget = entrySetIndex < 0
            mapRegister = if (isLegacyTarget)
                urlRegister + 1
            else
                getInstruction<FiveRegisterInstruction>(entrySetIndex).registerC

            if (isLegacyTarget) {
                addInstructions(
                    newRequestBuilderIndex + 2,
                    "move-object/from16 v$mapRegister, p1"
                )
                offSet++
            }
        }
    }
}

internal fun hookBuildRequest(descriptor: String) {
    buildRequestMethod.apply {
        val insertIndex = indexOfNewUrlRequestBuilderInstruction(this) + 2 + offSet

        addInstructions(
            insertIndex,
            "invoke-static { v$urlRegister, v$mapRegister }, $descriptor"
        )
    }
}

internal fun hookBuildRequestUrl(descriptor: String) {
    buildRequestMethod.apply {
        val insertIndex = indexOfNewUrlRequestBuilderInstruction(this)

        addInstructions(
            insertIndex, """
                invoke-static { v$urlRegister }, $descriptor
                move-result-object v$urlRegister
                """
        )
    }
}

internal fun hookInitPlaybackBuildRequest(descriptor: String) {
    buildInitPlaybackRequestMethod.apply {
        val insertIndex = indexOfUriToStringInstruction(this) + 2

        addInstructions(
            insertIndex,
            "invoke-static { v$initPlaybackUrlRegister, v$initPlaybackMapRegister }, $descriptor"
        )
    }
}
