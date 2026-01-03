package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionReversed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/http/OkHttpRequestHook;"
internal const val EXTRA_EMOJI_CONTEXT_KEY = "extraEmoji"
internal const val EXTRA_EMOJI_GETTER = "getExtraEmoji"

@Suppress("unused")
val undeleteRedditPatch = bytecodePatch(
    name="Automatically undelete Reddit content"
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        installJrawInterceptorFingerprint.method.apply {
            val index = indexOfFirstInstructionReversed(Opcode.INVOKE_VIRTUAL)
            addInstructions(
                index,
                """
                invoke-static       { v0 }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object  v0
                """
            )
        }

        // region Add additional field and marshaling code for ContributionModel.
        val fieldType = "Ljava/lang/String;"
        contributionModelConstructorFingerprint.classDef.apply {
            fields.add(
                ImmutableField(
                type,
                EXTRA_EMOJI_CONTEXT_KEY,
                fieldType,
                AccessFlags.PROTECTED.value,
                null,
                null,
                null,
            ).toMutable())
            methods.add(
                ImmutableMethod(
                type,
                EXTRA_EMOJI_GETTER,
                emptyList(),
                fieldType,
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(
                    """
                    iget-object v0, p0, $type->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                    return-object v0
                """,
                )
            },)
        }
        contributionModelConstructorFingerprint.method.apply {
            addInstructions(
                instructions.lastIndex,
                """
                invoke-virtual      {p1}, Landroid/os/Parcel;->readString()$fieldType
                move-result-object  v0
                iput-object         v0, p0, ${this.definingClass}->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                """
            )
        }
        contributionModelWriteToParcelFingerprint.method.apply {
            addInstructions(
                instructions.lastIndex,
                """
                iget-object         v0, p0, ${this.definingClass}->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                invoke-virtual      {p1, v0}, Landroid/os/Parcel;->writeString($fieldType)V
                """
            )
        }
        // endregion

        // region Modify SubmissionModel/CommentModel deserialization.
        arrayOf(submissionModelDeserializeFingerprint, commentModelDeserializeFingerprint).forEach {
            it.method.apply {
                val index = indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL)
                addInstructions(
                    index,
                    """
                    const-string        v1, "$EXTRA_EMOJI_CONTEXT_KEY"
                    invoke-virtual      {p0, v1}, Lnet/dean/jraw/models/JsonModel;->data(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object  v1
                    iput-object         v1, v0, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                    """
                )
            }
        }
        // endregion

        // region Extend emojis used for BabushkaText.
        val babushkaText = "\$a"
        setCommentBabushkaTextFingerprint.method.apply {
            val babushkaTextCheckGotoIndex = indexOfFirstInstruction(Opcode.GOTO)
            val gotoTarget = getInstruction<BuilderInstruction10t>(babushkaTextCheckGotoIndex).target
            replaceInstruction(babushkaTextCheckGotoIndex - 1, BuilderInstruction10t(Opcode.GOTO, gotoTarget))
            val checkInstructions = """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v2
                    invoke-static       {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v0
                    """.toInstructions(this).toMutableList()
            checkInstructions.add(BuilderInstruction21t(Opcode.IF_EQZ, 0, gotoTarget))
            addInstructions(
                babushkaTextCheckGotoIndex,
                checkInstructions,
            )

            val babushkaTextLoadIndex = indexOfFirstInstructionReversed(Opcode.GOTO_16) + 1
            addInstructionsAtControlFlowLabel(
                babushkaTextLoadIndex,
                """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v2
                    invoke-static       {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v0
                    if-nez              v0, :continue
                    iget-object         v0, p0, Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;->distinguishedTv:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;
                    new-instance        v1, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;
                    invoke-direct       {v1, v2}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;-><init>(Ljava/lang/String;)V
                    invoke-virtual      {v1}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;->r()Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    move-result-object  v1
                    invoke-virtual      {v0, v1}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    :continue
                    nop
                    """
            )
        }
        setSubmissionBabushkaTextFingerprint.method.apply {
            var babushkaTextLoadIndex = indexOfFirstInstruction {
                val methodReference = getReference<MethodReference>()
                opcode == Opcode.INVOKE_VIRTUAL && methodReference?.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/PublicContributionModel;" && methodReference.name == "x"
            }

            addInstructionsAtControlFlowLabel(
                babushkaTextLoadIndex,
                """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v0
                    invoke-static       {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v4
                    if-nez              v4, :continue
                    iget-object         v4, p0, $definingClass->infoTop:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;
                    iget-object         v5, p0, $definingClass->l:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    invoke-virtual      {v4, v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    new-instance        v5, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;
                    invoke-direct       {v5, v0}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;-><init>(Ljava/lang/String;)V
                    invoke-virtual      {v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;->r()Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    move-result-object  v5
                    invoke-virtual      {v4, v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    :continue
                    nop
                    """
            )
        }

        // endregion

        // region Fix the deletion reason detection
        stateSubmissionViewGetHeaderFingerprint.method.let {
            stateSubmissionViewGetHeaderFingerprint.stringMatches!!.forEach { match ->
                if (match.string == "copyright_takedown") {
                    it.addInstructionsAtControlFlowLabel(
                        match.index,
                        """
                        const               v1, 0x7F130594
                        """
                    )
                } else {
                    it.addInstructionsWithLabels(
                        match.index,
                        """
                        const-string        v0, "content_takedown"
                        invoke-virtual      {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                        move-result         v0
                        if-eqz              v0, :continue
                        const               p1, 0x7F130590
                        return              p1
                        """,
                        ExternalLabel("continue", it.getInstruction(match.index))
                    )
                }
            }
        }
        stateSubmissionViewGetSummaryFingerprint.method.let {
            stateSubmissionViewGetSummaryFingerprint.stringMatches!!.forEach { match ->
                if (match.string == "copyright_takedown") {
                    it.addInstructionsAtControlFlowLabel(
                        match.index,
                        """
                        const               v1, 0x7f130595
                        """
                    )
                } else {
                    it.addInstructionsWithLabels(
                        match.index,
                        """
                        const-string        v0, "content_takedown"
                        invoke-virtual      {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                        move-result         v0
                        if-eqz              v0, :continue
                        const               p1, 0x7F130591
                        return              p1
                        """,
                        ExternalLabel("continue", it.getInstruction(match.index))
                    )
                }
            }
        }
        stateSubmissionViewHasValidRemovalReasonFingerprint.method.let {
            stateSubmissionViewGetSummaryFingerprint.stringMatches!!.forEach { match ->
                val index = it.indexOfFirstInstructionReversed(Opcode.CONST_4)
                it.addInstructionsWithLabels(
                    match.index,
                    """
                    const-string        v0, "content_takedown"
                    invoke-virtual      {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result         v0
                    if-nez              v0, :matched
                    """,
                    ExternalLabel("matched", it.getInstruction(index))
                )
            }
        }
        // endregion
    }
}