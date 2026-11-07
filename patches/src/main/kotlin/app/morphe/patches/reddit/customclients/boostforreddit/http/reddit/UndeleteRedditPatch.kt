/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Modifications Copyright 2026 brealorg.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostFieldBinding

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.boostforreddit.http.REDDIT_UNDELETE_APPLIED_RESOURCE
import app.morphe.patches.reddit.customclients.boostforreddit.http.undeleteAppliedMarkerPatch
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import app.morphe.patches.all.misc.PatchCategory

internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boost/http/OkHttpRequestHook;"
private const val GLIDE_REDDIT_MEDIA_WAYBACK_DESCRIPTOR =
    "Lapp/morphe/extension/boost/http/reddit/GlideRedditMediaWayback;"
internal const val EXTRA_EMOJI_CONTEXT_KEY = "extraEmoji"
internal const val EXTRA_EMOJI_GETTER = "getExtraEmoji"

private fun extraEmojiInstructions(
    viewField: BoostFieldBinding,
    separatorField: BoostFieldBinding? = null,
): String {
    val separator = separatorField?.let {
        """
            iget-object v2, v1, ${BoostApi.BabushkaText.parts}
            invoke-virtual {v2}, Ljava/util/ArrayList;->isEmpty()Z
            move-result v2
            if-nez v2, :morphe_extra_emoji_separator_done
            iget-object v2, p0, $it
            invoke-virtual {v1, v2}, ${BoostApi.BabushkaText.addPart}
            :morphe_extra_emoji_separator_done
        """
    }.orEmpty()
    return """
        invoke-virtual {p1}, ${BoostApi.ContributionModel.type}->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
        move-result-object v0
        invoke-static {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
        move-result v1
        if-nez v1, :morphe_extra_emoji_done
        iget-object v1, p0, $viewField
        if-eqz v1, :morphe_extra_emoji_done
        $separator
        new-instance v2, ${BoostApi.BabushkaTextPartBuilder.type}
        invoke-direct {v2, v0}, ${BoostApi.BabushkaTextPartBuilder.construct}
        new-instance v0, ${BoostApi.BabushkaTextPart.type}
        invoke-direct {v0, v2}, ${BoostApi.BabushkaTextPart.construct}
        invoke-virtual {v1, v0}, ${BoostApi.BabushkaText.addPart}
        invoke-virtual {v1}, ${BoostApi.BabushkaText.updateText}
        const/4 v0, 0x0
        invoke-virtual {v1, v0}, Landroid/view/View;->setVisibility(I)V
        :morphe_extra_emoji_done
    """
}

@Suppress("unused")
val undeleteRedditPatch = morpheBytecodePatch(
    name = "Automatically undelete Reddit content",
    description = "Restores deleted and removed posts and comments from Arctic Shift, and their media from the Wayback Machine.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.UNDELETE,
    preferenceKey = "morphe_boost_reddit_undelete_enabled",
    defaultEnabled = false,
    settingsSummary = "Restore removed Reddit posts and comments from Arctic Shift and the Wayback Machine. " +
        "When a thread has removed content, its post and comment IDs are sent to these third-party archives. Off by default.",
) { setting ->
    dependsOn(ExtensionPatches.Boost, interceptHttpRequests, undeleteAppliedMarkerPatch(REDDIT_UNDELETE_APPLIED_RESOURCE))
    compatibleWith(*AppCompatibility.Boost)

    execute {
        // Glide uses HttpURLConnection directly, bypassing the OkHttp interceptors.
        GlideHttpUrlFetcherFetchFingerprint.method.apply {
            val nativeInstructions = implementation!!.instructions
            val responseCodeIndices = nativeInstructions.withIndex().filter { (_, instruction) ->
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()?.toString() ==
                    BoostApi.HttpUrlFetcher.responseCode.toString()
            }.map { it.index }
            check(responseCodeIndices.size == 3) {
                "Unexpected Glide response handling: $responseCodeIndices"
            }
            val normalResponseIndex = responseCodeIndices.first()
            val statusRegister = getInstruction<OneRegisterInstruction>(normalResponseIndex + 1).registerA
            val errorHandlerStart = nativeInstructions.withIndex().last { (index, instruction) ->
                index < responseCodeIndices.last() && instruction.opcode == Opcode.MOVE_EXCEPTION
            }.index + 1
            check(getInstruction(errorHandlerStart).opcode == Opcode.NEW_INSTANCE)

            // getInputStream() throws on HTTP errors before the normal status
            // branch. Keep the fetcher receiver and caught exception intact.
            addInstructionsWithLabels(
                errorHandlerStart,
                """
                    iget-object v3, p0, ${BoostApi.HttpUrlFetcher.connection}
                    invoke-static {v3}, ${BoostApi.HttpUrlFetcher.responseCode}
                    move-result v4
                    invoke-static {v3, v4}, Lapp/morphe/extension/boost/http/reddit/GlideRedditMediaWayback;->open(Ljava/net/HttpURLConnection;I)Ljava/io/InputStream;
                    move-result-object v3
                    if-eqz v3, :morphe_native_glide_error
                    iput-object v3, p0, ${BoostApi.HttpUrlFetcher.stream}
                    return-object v3
                """,
                ExternalLabel("morphe_native_glide_error", getInstruction(errorHandlerStart)),
            )
            addInstructionsWithLabels(
                normalResponseIndex + 2,
                """
                    iget-object v3, p0, ${BoostApi.HttpUrlFetcher.connection}
                    invoke-static {v3, v$statusRegister}, Lapp/morphe/extension/boost/http/reddit/GlideRedditMediaWayback;->open(Ljava/net/HttpURLConnection;I)Ljava/io/InputStream;
                    move-result-object v3
                    if-eqz v3, :morphe_native_glide_response
                    iput-object v3, p0, ${BoostApi.HttpUrlFetcher.stream}
                    return-object v3
                """,
                ExternalLabel("morphe_native_glide_response", getInstruction(normalResponseIndex + 2)),
            )
        }

        // region Add additional field and marshaling code for ContributionModel.
        val fieldType = "Ljava/lang/String;"
        ContributionModelConstructorFingerprint.classDef.apply {
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
                    0,
                    """
                    iget-object v0, p0, $type->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                    return-object v0
                """,
                )
            },)
        }
        ContributionModelConstructorFingerprint.method.apply {
            addInstructions(
                instructions.lastIndex,
                """
                invoke-virtual      {p1}, Landroid/os/Parcel;->readString()$fieldType
                move-result-object  v0
                iput-object         v0, p0, ${this.definingClass}->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                """
            )
        }
        ContributionModelWriteToParcelFingerprint.method.apply {
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
        arrayOf(SubmissionModelDeserializeFingerprint, CommentModelDeserializeFingerprint).forEach { fingerprint ->
            fingerprint.wrapHook(
                setting = setting,
                after = """
                    if-eqz v0, :morphe_extra_emoji_deserialized
                    const-string        v1, "extraEmoji"
                    invoke-virtual      {p0, v1}, ${BoostApi.JsonModel.data}
                    move-result-object  v1
                    iput-object         v1, v0, ${BoostApi.ContributionModel.type}->extraEmoji:Ljava/lang/String;
                    :morphe_extra_emoji_deserialized
                """,
            )
        }
        // endregion

        // region Extend emojis used for BabushkaText.
        SetCommentBabushkaTextFingerprint.wrapHook(
            setting = setting,
            after = extraEmojiInstructions(
                BoostApi.CommentViewHolder.distinguishedTv,
            ),
        )
        SetSubmissionBabushkaTextFingerprint.wrapHook(
            setting = setting,
            after = extraEmojiInstructions(
                BoostApi.SubmissionViewHolder.infoTop,
                BoostApi.SubmissionViewHolder.infoSeparator,
            ),
        )

        // endregion

        // region Fix the deletion reason detection
        setting.patchMethod(StateSubmissionViewSetStateFingerprint) {
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, p1, ${BoostApi.SubmissionModel.removalCategory}
                    const-string v1, "copyright_takedown"
                    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :morphe_check_content_takedown
                    const v1, 0x7f13057c
                    const v2, 0x7f13057d
                    goto :morphe_show_removal_reason
                    :morphe_check_content_takedown
                    const-string v1, "content_takedown"
                    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :morphe_native_removal_reason
                    const v1, 0x7f130578
                    const v2, 0x7f130579
                    :morphe_show_removal_reason
                    iget-object v0, p0, ${BoostApi.StateSubmissionView.headerText}
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(I)V
                    iget-object v0, p0, ${BoostApi.StateSubmissionView.summaryText}
                    invoke-virtual {v0, v2}, Landroid/widget/TextView;->setText(I)V
                    const/4 v0, 0x0
                    invoke-virtual {p0, v0}, Landroid/view/View;->setVisibility(I)V
                    return-void
                """,
                ExternalLabel("morphe_native_removal_reason", getInstruction(0)),
            )
        }
        // endregion
    }
}
