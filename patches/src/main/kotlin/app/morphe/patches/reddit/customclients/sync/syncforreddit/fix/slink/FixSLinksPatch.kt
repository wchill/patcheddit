package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/FixSLinksPatch;"
private const val ACCOUNT = "Lcom/laurencedawson/reddit_sync/singleton/a;"
private const val QUEUE = "Lcom/android/volley/RequestQueue;"
private const val CACHE = "Lcom/android/volley/Cache;"
private const val ENTRY = "Lcom/android/volley/Cache\$Entry;"
private const val STRING = "Ljava/lang/String;"
private const val GET_REFRESH = "$ACCOUNT->f()$STRING"
private const val CACHE_KEY = "$EXTENSION_CLASS_DESCRIPTOR->tokenCacheKey($STRING)$STRING"

@Suppress("unused")
val fixSLinksPatch = fixSLinksPatch(
    extensionPatch = ExtensionPatches.Sync,
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // region Patch navigation handler.

        LinkHelperOpenLinkFingerprint.method.apply {
            val urlRegister = "p3"
            val tempRegister = "v2"

            addInstructionsWithLabels(
                0,
                """
                    invoke-static { $urlRegister }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result $tempRegister
                    if-eqz $tempRegister, :continue
                    return $tempRegister
                """,
                ExternalLabel("continue", getInstruction(0)),
            )
        }

        // endregion

        val signedIn = Fingerprint(returnType = "[B", strings = listOf("grant_type=refresh_token&refresh_token="),
            filters = listOf(methodCall(name = "f", definingClass = ACCOUNT)))
        val guest = Fingerprint(returnType = "[B", strings = listOf(
            "grant_type=https://oauth.reddit.com/grants/installed_client&device_id=",
        ))
        // Sync keeps the token in Volley's OAuth response cache, not in its account singleton.
        // Partition the shared token URL by the credential the native request actually uses.
        listOf(guest to false, signedIn to true).forEach { (fingerprint, user) ->
            val owner = fingerprint.classDef
            val refresh = "${owner.type}->morpheRefreshToken:$STRING"
            if (user) {
                owner.fields.add(ImmutableField(owner.type, "morpheRefreshToken", STRING,
                    AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, null, null, null).toMutable())
                owner.methods.filter { it.name == "<init>" }.forEach { constructor ->
                    val implementation = checkNotNull(constructor.implementation)
                    val returnIndex = implementation.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
                    check(returnIndex >= 0 && implementation.registerCount in 4..16)
                    constructor.addInstructions(returnIndex, """
                        invoke-static {}, $ACCOUNT->d()$ACCOUNT
                        move-result-object v0
                        invoke-virtual {v0}, $GET_REFRESH
                        move-result-object v0
                        iput-object v0, p0, $refresh
                    """)
                }
                val body = fingerprint.method
                val instructions = checkNotNull(body.implementation).instructions
                val readRefresh = instructions.indices.single {
                    instructions[it].getReference<MethodReference>()?.toString() == GET_REFRESH &&
                        instructions.getOrNull(it + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT
                }
                val result = (instructions[readRefresh + 1] as OneRegisterInstruction).registerA
                body.replaceInstruction(readRefresh + 1, "iget-object v$result, p0, $refresh")
                body.replaceInstruction(readRefresh, "nop")
            }
            owner.methods.add(ImmutableMethod(
                owner.type, "getCacheKey", emptyList(), STRING, AccessFlags.PUBLIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(0, """
                    ${if (user) """
                        iget-object v0, p0, $refresh
                        invoke-static {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                        move-result v0
                        if-eqz v0, :morphe_user_key
                        const-string v0, "patcheddit-oauth-missing-user"
                        return-object v0
                        :morphe_user_key
                        iget-object v0, p0, $refresh
                    """ else "const/4 v0, 0x0"}
                    invoke-static {v0}, $CACHE_KEY
                    move-result-object v0
                    return-object v0
                """)
            })
        }
        val enqueue = Fingerprint(strings = listOf("CONTEXT: "), returnType = "V",
            parameters = listOf("Landroid/content/Context;", STRING, signedIn.classDef.superclass!!)).method
        val tokenQueue = enqueue.implementation!!.instructions.mapNotNull { it.getReference<FieldReference>() }
            .filter { it.type == QUEUE }.distinct().last()
        bindShareLinkAccessToken("""
            invoke-static {}, $ACCOUNT->d()$ACCOUNT
            move-result-object v0
            invoke-virtual {v0}, $GET_REFRESH
            move-result-object v0
            invoke-static {v0}, $CACHE_KEY
            move-result-object v1
            sget-object v0, $tokenQueue
            if-eqz v0, :morphe_no_token
            invoke-virtual {v0}, $QUEUE->getCache()$CACHE
            move-result-object v0
            invoke-interface {v0, v1}, $CACHE->get($STRING)$ENTRY
            move-result-object v0
            if-eqz v0, :morphe_no_token
            invoke-virtual {v0}, $ENTRY->isExpired()Z
            move-result v2
            if-nez v2, :morphe_no_token
            iget-object v0, v0, $ENTRY->data:[B
            invoke-static {}, $ACCOUNT->d()$ACCOUNT
            move-result-object v2
            invoke-virtual {v2}, $GET_REFRESH
            move-result-object v2
            invoke-static {v2}, $CACHE_KEY
            move-result-object v2
            invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2
            if-eqz v2, :morphe_no_token
            invoke-static {v0}, $EXTENSION_CLASS_DESCRIPTOR->readCachedToken([B)$STRING
            move-result-object v0
            return-object v0
            :morphe_no_token
            const/4 v0, 0x0
            return-object v0
        """)
    }
}
