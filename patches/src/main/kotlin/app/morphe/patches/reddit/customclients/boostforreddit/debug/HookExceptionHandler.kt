package app.morphe.patches.reddit.customclients.boostforreddit.debug

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/debug/ExceptionHook;"

@Suppress("unused")
val hookExceptionHandler = bytecodePatch(
    name="Hook exception handler",
    description = "Hook the exception handler in Boost. Don't enable except for development purposes",
    use=false
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        exceptionHandlerFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->handleException(Ljava/lang/Throwable;Ljava/lang/String;)V
                """,
            )
        }
    }
}