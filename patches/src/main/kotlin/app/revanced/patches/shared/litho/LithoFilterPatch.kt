package app.revanced.patches.shared.litho

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.revanced.patches.shared.conversionContextFingerprintToString
import app.revanced.patches.shared.extension.Constants.COMPONENTS_PATH
import app.revanced.util.addInstructionsAtControlFlowLabel
import app.revanced.util.findFreeRegister
import app.revanced.util.findMethodsOrThrow
import app.revanced.util.fingerprint.injectLiteralInstructionBooleanCall
import app.revanced.util.fingerprint.matchOrThrow
import app.revanced.util.fingerprint.methodOrThrow
import app.revanced.util.fingerprint.mutableClassOrThrow
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import app.revanced.util.or
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR =
    "$COMPONENTS_PATH/LithoFilterPatch;"

private const val EXTENSION_FILER_ARRAY_DESCRIPTOR =
    "[$COMPONENTS_PATH/Filter;"

private lateinit var filterArrayMethod: MutableMethod
private var filterCount = 0

internal lateinit var addLithoFilter: (String) -> Unit
    private set

internal var emptyComponentLabel = ""

val lithoFilterPatch = bytecodePatch(
    description = "lithoFilterPatch",
) {
    execute {

        // region Pass the buffer into extension.

        byteBufferFingerprint.methodOrThrow().addInstruction(
            0,
            "invoke-static { p2 }, $EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR->setProtoBuffer(Ljava/nio/ByteBuffer;)V"
        )

        // endregion

        // region Hook the method that parses bytes into a ComponentContext.

        // Allow the method to run to completion, and override the
        // return value with an empty component if it should be filtered.
        // It is important to allow the original code to always run to completion,
        // otherwise high memory usage and poor app performance can occur.

        // Find the identifier/path fields of the conversion context.
        val conversionContextClass = conversionContextFingerprintToString
            .matchOrThrow()
            .originalClassDef

        val conversionContextIdentifierField =
            componentContextParserFingerprint.matchOrThrow().let {
                // Identifier field is loaded just before the string declaration.
                val index = it.method.indexOfFirstInstructionReversedOrThrow(
                    it.stringMatches!!.first().index
                ) {
                    val reference = getReference<FieldReference>()
                    reference?.definingClass == conversionContextClass.type
                            && reference.type == "Ljava/lang/String;"
                }

                it.method.getInstruction<ReferenceInstruction>(index)
                    .getReference<FieldReference>()!!
            }

        val conversionContextPathBuilderField = conversionContextClass
            .fields.single { field -> field.type == "Ljava/lang/StringBuilder;" }

        // Find class and methods to create an empty component.
        val builderMethodDescriptor = emptyComponentFingerprint
            .mutableClassOrThrow()
            .methods
            .single {
                // The only static method in the class.
                    method ->
                AccessFlags.STATIC.isSet(method.accessFlags)
            }
        val emptyComponentField = classBy {
            // Only one field that matches.
            it.type == builderMethodDescriptor.returnType
        }!!.immutableClass.fields.single()

        emptyComponentLabel = """
            move-object/from16 v0, p1
            invoke-static {v0}, $builderMethodDescriptor
            move-result-object v0
            iget-object v0, v0, $emptyComponentField
            return-object v0
            """

        var isLegacyMethod = false

        try {
            isLegacyMethod = MethodUtil.methodSignaturesMatch(
                componentContextParserLegacyFingerprint.methodOrThrow(),
                componentContextParserFingerprint.methodOrThrow()
            )
        } catch (_: Exception) {
        }

        componentCreateFingerprint.methodOrThrow().apply {
            val insertIndex = if (isLegacyMethod) {
                // YT 19.16 and YTM 6.51 clobbers p2 so must check at start of the method and not at the return index.
                0
            } else {
                indexOfFirstInstructionOrThrow(Opcode.RETURN_OBJECT)
            }

            val freeRegister = findFreeRegister(insertIndex)
            val identifierRegister = findFreeRegister(insertIndex, freeRegister)
            val pathRegister = findFreeRegister(insertIndex, freeRegister, identifierRegister)

            addInstructionsAtControlFlowLabel(
                insertIndex,
                """
                    move-object/from16 v$freeRegister, p2

                    # Required for YouTube Music.
                    check-cast v$freeRegister, ${conversionContextIdentifierField.definingClass}

                    iget-object v$identifierRegister, v$freeRegister, $conversionContextIdentifierField
                    iget-object v$pathRegister, v$freeRegister, $conversionContextPathBuilderField
                    invoke-static {v$pathRegister, v$identifierRegister, v$freeRegister}, $EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR->isFiltered(Ljava/lang/StringBuilder;Ljava/lang/String;Ljava/lang/Object;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :unfiltered
                    """ + emptyComponentLabel + """
                    :unfiltered
                    nop
                    """
            )
        }

        // endregion

        // region Change Litho thread executor to 1 thread to fix layout issue in unpatched YouTube.

        lithoThreadExecutorFingerprint.methodOrThrow().addInstructions(
            0, """
                invoke-static { p1 }, $EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR->getExecutorCorePoolSize(I)I
                move-result p1
                invoke-static { p2 }, $EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR->getExecutorMaxThreads(I)I
                move-result p2
                """
        )

        // endregion

        // region A/B test of new Litho native code.

        // Turn off native code that handles litho component names.  If this feature is on then nearly
        // all litho components have a null name and identifier/path filtering is completely broken.

        mapOf(
            bufferUpbFeatureFlagFingerprint to BUFFER_UPD_FEATURE_FLAG,
            pathUpbFeatureFlagFingerprint to PATH_UPD_FEATURE_FLAG,
        ).forEach { (fingerprint, literalValue) ->
            if (fingerprint.second.methodOrNull != null) {
                fingerprint.injectLiteralInstructionBooleanCall(
                    literalValue,
                    "0x0"
                )
            }
        }

        // endregion

        // Create a new method to get the filter array to avoid register conflicts.
        // This fixes an issue with extension compiled with Android Gradle Plugin 8.3.0+.
        // https://github.com/ReVanced/revanced-patches/issues/2818
        val lithoFilterMethods = findMethodsOrThrow(EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR)

        lithoFilterMethods
            .first { it.name == "<clinit>" }
            .apply {
                val setArrayIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.SPUT_OBJECT &&
                            getReference<FieldReference>()?.type == EXTENSION_FILER_ARRAY_DESCRIPTOR
                }
                val setArrayRegister =
                    getInstruction<OneRegisterInstruction>(setArrayIndex).registerA
                val addedMethodName = "getFilterArray"

                addInstructions(
                    setArrayIndex, """
                        invoke-static {}, $EXTENSION_LITHO_FILER_CLASS_DESCRIPTOR->$addedMethodName()$EXTENSION_FILER_ARRAY_DESCRIPTOR
                        move-result-object v$setArrayRegister
                        """
                )

                filterArrayMethod = ImmutableMethod(
                    definingClass,
                    addedMethodName,
                    emptyList(),
                    EXTENSION_FILER_ARRAY_DESCRIPTOR,
                    AccessFlags.PRIVATE or AccessFlags.STATIC,
                    null,
                    null,
                    MutableMethodImplementation(3),
                ).toMutable().apply {
                    addInstruction(
                        0,
                        "return-object v2"
                    )
                }

                lithoFilterMethods.add(filterArrayMethod)
            }

        addLithoFilter = { classDescriptor ->
            filterArrayMethod.addInstructions(
                0,
                """
                    new-instance v0, $classDescriptor
                    invoke-direct {v0}, $classDescriptor-><init>()V
                    const/16 v1, ${filterCount++}
                    aput-object v0, v2, v1
                    """
            )
        }
    }

    finalize {
        filterArrayMethod.addInstructions(
            0, """
                const/16 v0, $filterCount
                new-array v2, v0, $EXTENSION_FILER_ARRAY_DESCRIPTOR
                """
        )
    }
}


