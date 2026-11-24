package app.morphe.patches.reddit.customclients.relay.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/relay/FixSLinksPatch;"
private const val APPLICATION = "Lreddit/news/RelayApplication;"
private const val COMPONENT = "Lreddit/news/oauth/dagger/components/RelayApplicationComponent;"
private const val MANAGER = "Lreddit/news/oauth/RedditAccountManager;"
private const val ACCOUNT = "Lreddit/news/oauth/reddit/model/RedditAccount;"
private const val TOKEN = "Lreddit/news/oauth/reddit/model/RedditAccessToken;"

@Suppress("unused")
val fixSLinksPatch = fixSLinksPatch(
    extensionPatch = ExtensionPatches.Relay,
) {
    compatibleWith(*AppCompatibility.Relay)

    execute {
        val application = Fingerprint(definingClass = APPLICATION, returnType = COMPONENT, parameters = emptyList()).method
        val manager = Fingerprint(
            definingClass = "Lreddit/news/listings/comments/managers/ClickManager;",
        ).classDef.methods.flatMap { it.implementation?.instructions.orEmpty() }
            .mapNotNull { it.getReference<MethodReference>() }
            .filter { it.definingClass == COMPONENT && it.returnType == MANAGER && it.parameterTypes.isEmpty() }
            .distinct().single()
        val account = Fingerprint(definingClass = MANAGER, name = "k0", returnType = ACCOUNT, parameters = emptyList()).method
        // Read the same account and access-token fields as the native OAuth interceptor.
        bindShareLinkAccessToken("""
            invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;
            move-result-object v0
            check-cast v0, $APPLICATION
            invoke-virtual {v0}, ${application.definingClass}->${application.name}()$COMPONENT
            move-result-object v0
            if-eqz v0, :morphe_no_token
            invoke-interface {v0}, ${manager.definingClass}->${manager.name}()$MANAGER
            move-result-object v0
            if-eqz v0, :morphe_no_token
            invoke-virtual {v0}, ${account.definingClass}->${account.name}()$ACCOUNT
            move-result-object v0
            if-eqz v0, :morphe_no_token
            iget-object v0, v0, $ACCOUNT->accessToken:$TOKEN
            if-eqz v0, :morphe_no_token
            iget-object v0, v0, $TOKEN->accessToken:Ljava/lang/String;
            return-object v0
            :morphe_no_token
            const/4 v0, 0x0
            return-object v0
        """)

        // region Patch navigation handler.

        IntentFilterActivityFingerprint.method.apply {
            val index = IntentFilterActivityFingerprint.instructionMatches.last().index
            val register = getInstruction<OneRegisterInstruction>(index).registerA

            addInstructionsWithLabels(
                index + 1,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result v5
                    if-eqz v5, :continue
                    return-void
                """,
                ExternalLabel("continue", getInstruction(index + 1)),
            )
        }

        // endregion

        // region Patch in-app link handler.

        urlLinkClickManagerFingerprint.method.apply {
            // p1 holds the url. v0 is safe to clobber here because the method's locals
            // (v0 to v7) are not yet initialised at index 0.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result v0
                    if-eqz v0, :continue
                    return-void
                """,
                ExternalLabel("continue", getInstruction(0)),
            )
        }

        // endregion

        // region Patch post and comment view link handler.

        webAndCommentsFragmentLinkFingerprint.method.apply {
            // p1 holds the url. v0 is safe to clobber here because the method's locals
            // (v0 to v6) are not yet initialised at index 0.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                    move-result v0
                    if-eqz v0, :continue
                    return-void
                """,
                ExternalLabel("continue", getInstruction(0)),
            )
        }

        // endregion
    }
}
