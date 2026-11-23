package app.morphe.patches.reddit.customclients.baconreader.fix.slink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.SHARE_NAV
import app.morphe.patches.reddit.customclients.bindShareLinkAccessToken
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField

private const val PROCESSOR = "Lcom/onelouder/baconreader/LinkProcessor;"
private const val DISPATCHER = "Lcom/onelouder/baconreader/LinkDispatcher;"
private const val TOKEN = "Lcom/onelouder/baconreader/data/SessionToken;"
private const val SESSIONS = "Lcom/onelouder/baconreader/data/SessionManager;"
private const val RAW_URL = "morpheRawShareUrl"
private const val STRING = "Ljava/lang/String;"
private const val INTENT = "Landroid/content/Intent;"
private const val CONTEXT = "Landroid/content/Context;"

internal val fixSLinksPatch = fixSLinksPatch(ExtensionPatches.BaconReader) {
    compatibleWith(*AppCompatibility.BaconReader)
    execute {
        Fingerprint(
            definingClass = DISPATCHER,
            name = "processing",
            parameters = listOf(INTENT),
            returnType = "Z",
            strings = listOf("processing START"),
        ).apply {
            wrapMorpheHook(classDef, method, before = """
                invoke-static {p0, p1}, $SHARE_NAV->openBaconReader(Landroid/app/Activity;${INTENT})Z
                move-result v0
                if-eqz v0, :morphe_skip_before
                return v0
            """)
        }
        val constructor = Fingerprint(
            definingClass = PROCESSOR,
            name = "<init>",
            parameters = listOf(CONTEXT, STRING),
            returnType = "V",
        )
        constructor.classDef.fields.add(ImmutableField(
            PROCESSOR, RAW_URL, STRING, AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, null, null, null,
        ).toMutable())
        constructor.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.IPUT_OBJECT && getReference<FieldReference>()?.name == "url"
            }
            // Keep the raw URL: the native constructor can unwrap/normalize other domains.
            addInstruction(index, "iput-object p2, p0, $PROCESSOR->$RAW_URL:$STRING")
        }
        Fingerprint(
            definingClass = PROCESSOR,
            name = "getIntent",
            parameters = emptyList(),
            returnType = INTENT,
            strings = listOf("getIntent"),
        ).apply {
            wrapMorpheHook(classDef, method, before = """
                iget-boolean v0, p0, $PROCESSOR->forceBrowser:Z
                if-nez v0, :morphe_skip_before
                iget-object v1, p0, $PROCESSOR->context:$CONTEXT
                iget-object v2, p0, $PROCESSOR->$RAW_URL:$STRING
                invoke-static {v1, v2}, $SHARE_NAV->baconReaderIntent($CONTEXT$STRING)$INTENT
                move-result-object v0
                if-eqz v0, :morphe_skip_before
                return-object v0
            """)
        }
        bindShareLinkAccessToken("""
            invoke-static {}, $SESSIONS->getToken()$TOKEN
            move-result-object v0
            if-eqz v0, :morphe_skip_before
            iget-object v0, v0, $TOKEN->accessToken:$STRING
            return-object v0
        """)
    }
}
