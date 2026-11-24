/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.fix.feed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRandomSubredditPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val STRING = "Ljava/lang/String;"
private const val HOOK = "Lapp/morphe/extension/shared/fixes/feed/RandomSubreddit;"
private const val MAP = "Ljava/util/Map;"

@Suppress("unused")
val infinityRandomSubredditPatch = fixRandomSubredditPatch(ExtensionPatches.Infinity, AppCompatibility.Infinity) { setting ->
    execute {
        // Resolve R8's merged Request fields from its typed constructor, not from field names.
        val chain = Fingerprint(strings = listOf(" must call proceed() exactly once", " must retain the same host and port"))
        val proceed = chain.method
        val request = proceed.parameterTypes.single().toString()
        val proceedCode = checkNotNull(proceed.implementation).instructions.toList()
        val networkCheck = proceedCode.indexOfFirst {
            it.getReference<StringReference>()?.string == " must call proceed() exactly once"
        }
        check(networkCheck >= 0)
        val exchangeField = proceedCode.drop(networkCheck + 1).first {
            it.opcode == Opcode.IGET_OBJECT
        }.getReference<FieldReference>()!!
        check(exchangeField.definingClass == proceed.definingClass)
        val constructor = classDefBy(request).methods.single {
            it.name == "<init>" && it.parameterTypes.size == 5 &&
                    it.parameterTypes[1] == STRING && it.parameterTypes.last() == MAP
        }
        val code = checkNotNull(constructor.implementation)
        val receiver = code.registerCount - 6
        fun field(parameter: Int): FieldReference = code.instructions.single {
            it.opcode == Opcode.IPUT_OBJECT && it is TwoRegisterInstruction &&
                    it.registerA == receiver + parameter && it.registerB == receiver
        }.getReference<FieldReference>()!!
        val urlField = field(1)
        val methodField = field(2)
        val headersField = field(3)
        val urlType = constructor.parameterTypes[0].toString()
        val headerType = constructor.parameterTypes[2].toString()
        val build = Fingerprint(parameters = emptyList(), returnType = request, strings = listOf("url == null")).method
        val builder = build.definingClass
        val newBuilder = classDefBy(request).methods.single {
            it.parameterTypes.isEmpty() && it.returnType == builder
        }
        val setUrl = Fingerprint(definingClass = builder, parameters = listOf(STRING),
            strings = listOf("ws:", "wss:")).method
        val tokenInterceptor = Fingerprint(parameters = listOf(proceed.definingClass),
            returnType = proceed.returnType, strings = listOf("bearer ", "Authorization", "oauth.reddit.com"))
        val header = tokenInterceptor.method.implementation!!.instructions
            .mapNotNull { it.getReference<MethodReference>() }
            .filter { it.definingClass == headerType && it.parameterTypes == listOf(STRING) && it.returnType == STRING }
            .distinctBy { it.toString() }.single()
        require(urlField.type in listOf(urlType, "Ljava/lang/Object;") &&
                methodField.type in listOf(STRING, "Ljava/lang/Object;") &&
                headersField.type in listOf(headerType, "Ljava/lang/Object;")) { "Unexpected Infinity request fields" }
        require(setUrl.returnType == builder || setUrl.returnType == "V") {
            "Unexpected Infinity request builder"
        }

        chain.wrapMorpheHook(setting = setting, before = """
            iget-object v0, p1, $urlField
            check-cast v0, $urlType
            invoke-virtual {v0}, $urlType->toString()$STRING
            move-result-object v0
            iget-object v1, p1, $methodField
            check-cast v1, $STRING
            invoke-static {v1, v0}, $HOOK->matchesNativeRequest($STRING$STRING)Z
            move-result v1
            if-eqz v1, :check_network
            iget-object v3, p1, $headersField
            check-cast v3, $headerType
            const-string v1, "Authorization"
            invoke-virtual {v3, v1}, $header
            move-result-object v1
            invoke-static {v1}, $HOOK->hasBearer($STRING)Z
            move-result v2
            if-eqz v2, :check_network
            const-string v2, "User-Agent"
            invoke-virtual {v3, v2}, $header
            move-result-object v2
            invoke-static {v0, v1, v2}, $HOOK->rewriteAuthenticated($STRING$STRING$STRING)$STRING
            move-result-object v0
            invoke-virtual {p1}, $newBuilder
            move-result-object v1
            invoke-virtual {v1, v0}, $setUrl
            ${if (setUrl.returnType == builder) "move-result-object v1" else ""}
            invoke-virtual {v1}, $build
            move-result-object p1
            goto :morphe_skip_before
            :check_network
            iget-object v2, p0, $exchangeField
            if-eqz v2, :morphe_skip_before
            iget-object v1, p1, $methodField
            check-cast v1, $STRING
            invoke-static {v1, v0}, $HOOK->rejectUnresolvedRequest($STRING$STRING)V
        """)
    }
}
