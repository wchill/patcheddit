/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.REDGIFS_PLAYBACK
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.patches.reddit.customclients.redgifsSetting
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val JSON = "Lcom/fasterxml/jackson/databind/JsonNode;"
private const val STRING = "Ljava/lang/String;"
private const val SUBMISSION = "Lnet/dean/jraw/models/Submission;"
private const val PLAYER = "Lza/l;"
private const val CALLBACK = "Lza/l\$f;"
private const val RESOLVER = "Lnd/b;"

@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(ExtensionPatches.Joey) {
    compatibleWith(*AppCompatibility.JoeyForReddit)

    execute {
        Fingerprint(
            definingClass = "Lyd/e0;",
            name = "f",
            parameters = listOf(SUBMISSION),
            returnType = STRING,
            strings = listOf("reddit_video_preview", "hls_url"),
        ).wrapMorpheHook(
            setting = redgifsSetting,
            before = """
                if-eqz p0, :morphe_skip_before
                invoke-virtual {p0}, $SUBMISSION->g0()$STRING
                move-result-object v0
                invoke-static {v0}, $REDGIFS_PLAYBACK->clipId($STRING)$STRING
                move-result-object v0
                if-eqz v0, :morphe_skip_before
                const/4 v0, 0x0
                return-object v0
            """,
        )

        Fingerprint(
            definingClass = PLAYER,
            name = "s",
            parameters = listOf(STRING),
            returnType = "Lza/l\$h;",
            strings = listOf(".mp4", "gfycat.com"),
        ).wrapMorpheHook(
            setting = redgifsSetting,
            before = """
                invoke-static {p0}, $REDGIFS_PLAYBACK->normalizeSourceUrl($STRING)$STRING
                move-result-object p0
            """,
        )

        // The separate streaming cache can return an old preview before reaching the resolver.
        Fingerprint(
            definingClass = PLAYER,
            name = "j",
            parameters = listOf(STRING, "Z"),
            returnType = "Z",
        ).wrapMorpheHook(
            setting = redgifsSetting,
            after = """
                if-eqz v0, :morphe_skip_after
                invoke-static {p0, p1}, $PLAYER->v(${STRING}Z)$STRING
                move-result-object v1
                invoke-static {p0, v1}, $REDGIFS_PLAYBACK->shouldBypassCachedSource($STRING$STRING)Z
                move-result v1
                if-eqz v1, :morphe_skip_after
                const/4 v0, 0x0
            """,
        )

        Fingerprint(
            definingClass = RESOLVER,
            name = "n",
            parameters = listOf(STRING, "Z"),
            returnType = STRING,
        ).wrapMorpheHook(
            setting = redgifsSetting,
            after = """
                if-nez p2, :morphe_skip_after
                invoke-static {v0}, $REDGIFS_PLAYBACK->shouldRefresh($STRING)Z
                move-result v1
                if-eqz v1, :morphe_skip_after
                const/4 v1, 0x1
                invoke-virtual {p0, p1, v1}, $RESOLVER->c(${STRING}Z)$JSON
                move-result-object v1
                invoke-direct {p0, v1}, $RESOLVER->m($JSON)$STRING
                move-result-object v1
                invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :morphe_skip_after
                move-object v0, v1
                goto :morphe_skip_after
                :refresh_error
                move-exception v1
                invoke-static {v1}, $REDGIFS_PLAYBACK->logLookupFailure(Ljava/io/IOException;)V
            """,
        ).catchIoFrom("$RESOLVER->c(${STRING}Z)$JSON", "$RESOLVER->m($JSON)$STRING")

        Fingerprint(
            definingClass = PLAYER,
            name = "G",
            parameters = listOf(STRING, CALLBACK, "Z"),
            returnType = STRING,
        ).wrapMorpheHook(
            setting = redgifsSetting,
            before = """
                invoke-static {p1}, $REDGIFS_PLAYBACK->clipId($STRING)$STRING
                move-result-object v1
                if-eqz v1, :morphe_skip_before
                invoke-static {}, $RESOLVER->j()$RESOLVER
                move-result-object v0
                const/4 v2, 0x0
                invoke-virtual {v0, v1, v2}, $RESOLVER->n(${STRING}Z)$STRING
                move-result-object v0
                invoke-static {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                move-result v1
                if-eqz v1, :play_source
                goto :lookup_fallback
                :lookup_error
                move-exception v1
                invoke-static {v1}, $REDGIFS_PLAYBACK->logLookupFailure(Ljava/io/IOException;)V
                :lookup_fallback
                invoke-static {p1}, $REDGIFS_PLAYBACK->isSilent($STRING)Z
                move-result v1
                if-eqz v1, :native
                move-object v0, p1
                :play_source
                invoke-direct {p0}, $PLAYER->N()Z
                move-result v1
                if-nez v1, :return_source
                invoke-direct {p0, v0, p2}, $PLAYER->R($STRING$CALLBACK)V
                :return_source
                return-object v0
                :native
                invoke-static {p1}, $REDGIFS_PLAYBACK->normalizeSourceUrl($STRING)$STRING
                move-result-object p1
            """,
        ).catchIoFrom("$RESOLVER->n(${STRING}Z)$STRING")
    }
}

// Inline smali insertion copies instructions, not try blocks.
private fun MutableMethod.catchIoFrom(firstCall: String, lastCall: String = firstCall) {
    val code = implementation!!
    val instructions = code.instructions
    fun call(reference: String) = instructions.indices.single {
        instructions[it].getReference<MethodReference>()?.toString() == reference
    }
    val start = call(firstCall)
    val end = call(lastCall) + 2
    val handler = instructions.indices.single { instructions[it].opcode == Opcode.MOVE_EXCEPTION }
    check(instructions[end - 1].opcode == Opcode.MOVE_RESULT_OBJECT && handler >= end)
    code.addCatch("Ljava/io/IOException;", code.newLabelForIndex(start), code.newLabelForIndex(end),
        code.newLabelForIndex(handler))
}
