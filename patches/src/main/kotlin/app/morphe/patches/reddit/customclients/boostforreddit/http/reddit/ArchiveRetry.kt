/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.http.reddit

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.BoostNativeLibraries
import app.morphe.patches.reddit.customclients.boostforreddit.wrapHook
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal const val ARCHIVE_RETRY_ITEM = "Lapp/morphe/extension/boost/http/reddit/ArchiveRetryItem;"
private const val RETRY = "Lapp/morphe/extension/boost/http/reddit/ArcticShiftRetry;"

/** Space-separated descriptors of the model fields assigned by a native deserializer. */
internal fun archiveRetryFields(fields: List<FieldReference>) =
    fields.joinToString(" ") { "${it.definingClass}->${it.name}:${it.type}" }

internal fun installArchiveRetry() {
    val native = BoostNativeLibraries.classes.associateBy { it.type }
    val (commentFields, submissionFields) = listOf(
        BoostApi.CommentModel.commentModelDeserialize,
        BoostApi.SubmissionModel.fromSubmission,
    ).map { deserialize ->
        val cls = native.getValue(deserialize.type)
        val hierarchy = generateSequence(cls) { it.superclass?.let(native::get) }.map { it.type }.toSet()
        val fields = cls.methods.single { deserialize.matches(it) }.implementation!!.instructions
            .filter { it.opcode.name.startsWith("iput") }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass in hierarchy }
            .distinctBy { it.toString() }
        check(fields.isNotEmpty()) { "Boost archive model has no deserialized fields: ${cls.type}" }
        mutableClassDefBy(cls.type).interfaces.add(ARCHIVE_RETRY_ITEM)
        archiveRetryFields(fields)
    }

    mutableClassDefBy(RETRY).methods.single { it.name == "deserializedFields" }.apply {
        val commentBody = "${BoostApi.CommentModel.body} ${BoostApi.CommentModel.bodyHtml}"
        val submissionBody = "${BoostApi.SubmissionModel.selftext} ${BoostApi.SubmissionModel.selftextHtml}"
        val code = implementation!!
        code.instructions.indices.reversed().forEach(code::removeInstruction)
        addInstructions(0, """
            if-eqz p1, :full_model
            if-eqz p0, :submission_body
            const-string v0, "$commentBody"
            return-object v0
            :submission_body
            const-string v0, "$submissionBody"
            return-object v0
            :full_model
            if-eqz p0, :submission
            const-string v0, "$commentFields"
            return-object v0
            :submission
            const-string v0, "$submissionFields"
            return-object v0
        """)
    }
    for (bind in listOf(BoostApi.CommentViewHolder.bind, BoostApi.SubmissionViewHolder.bind)) {
        bind.fingerprint().wrapHook(before = """
            invoke-static/range {p0 .. p5}, $RETRY->onBind(Ljava/lang/Object;Ljava/lang/Object;ZZZLjava/lang/Object;)V
        """)
    }
    for (menu in listOf(BoostApi.CommentViewHolder.showMenu, BoostApi.SubmissionViewHolder.showMenu)) {
        menu.fingerprint().wrapHook(before = """
            invoke-static {p0, p2}, $RETRY->addMenuOption(Ljava/lang/Object;Ljava/util/List;)V
        """)
    }
    BoostApi.CommentMenuListener.onClick.fingerprint().wrapHook(before = """
        invoke-static {p0, p1}, $RETRY->onCommentMenuClick(Ljava/lang/Object;${BoostApi.MenuOption.type})Z
        move-result v0
        if-eqz v0, :native_action
        return-void
        :native_action
    """)
    BoostApi.SubmissionViewHolder.onMenuClick.fingerprint().wrapHook(before = """
        invoke-static {p0, p1}, $RETRY->onClick(Ljava/lang/Object;${BoostApi.MenuOption.type})Z
        move-result v0
        if-eqz v0, :native_action
        return-void
        :native_action
    """)
}
