package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.SHARE_NAV
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.redditWrapperViewFilters
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val ACTIVITY = "ml.docilealligator.infinityforreddit.activities.LinkResolverActivity"
private const val SESSION = "Lapp/morphe/extension/infinity/http/ShareLinkSession;"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"

internal val infinityShareLinks = fixSLinksPatch(ExtensionPatches.Infinity) {
    compatibleWith(*AppCompatibility.Infinity)
    dependsOn(redditWrapperViewFilters(ACTIVITY))
    execute {
        Fingerprint(
            definingClass = "L${ACTIVITY.replace('.', '/')};",
            returnType = "V", parameters = listOf("Landroid/net/Uri;"),
            strings = listOf("click.redditmail.com", "redgifs.com"),
        ).wrapMorpheHook(before = """
            invoke-static {p0, p1}, $SHARE_NAV->openInfinity(Landroid/app/Activity;Landroid/net/Uri;)Z
            move-result v0
            if-eqz v0, :morphe_skip_before
            return-void
        """)
        bindShareLinkAccessToken("""
            invoke-static {p0}, $SESSION->accessToken(Landroid/content/Context;)$STRING
            move-result-object v0
            return-object v0
        """)

        // Older Infinity builds lack the guest provider; retain their existing account preference lookup.
        val refresh = Fingerprint(parameters = emptyList(), returnType = STRING,
            strings = listOf("https://oauth.reddit.com/grants/installed_client", "DO_NOT_TRACK_THIS_DEVICE")
        ).matchOrNull()?.method ?: return@execute
        val provider = refresh.definingClass
        val cached = Fingerprint(definingClass = provider, parameters = emptyList(), returnType = STRING,
            strings = listOf("SELECT access_token FROM accounts WHERE username = '-'")
        ).method
        val lock = cached.implementation!!.instructions.zipWithNext().single { (read, enter) ->
            read.opcode == Opcode.IGET_OBJECT && enter.opcode == Opcode.MONITOR_ENTER &&
                (read as TwoRegisterInstruction).registerA == (enter as OneRegisterInstruction).registerA
        }.first.getReference<FieldReference>()!!
        val constructor = Fingerprint(definingClass = provider, name = "<init>").method
        val initializer = constructor.implementation!!.instructions
        // The link activity's injected Retrofit creates this provider before handling its intent.
        constructor.addInstruction(initializer.indices.single { initializer[it].opcode == Opcode.RETURN_VOID },
            "invoke-static/range {p0 .. p0}, $SESSION->setGuestProvider($OBJECT)V")
        mapOf(
            "refreshLock" to (OBJECT to "iget-object v0, p0, $lock"),
            "cachedGuestToken" to (STRING to "invoke-virtual {p0}, $cached\nmove-result-object v0"),
            "refreshGuestToken" to (STRING to "invoke-virtual {p0}, $refresh\nmove-result-object v0"),
        ).forEach { (name, binding) ->
            val (type, read) = binding
            Fingerprint(definingClass = SESSION, name = name, parameters = listOf(OBJECT), returnType = type)
                .wrapMorpheHook(before = """
                    check-cast p0, $provider
                    $read
                    return-object v0
                """)
        }
    }
}
