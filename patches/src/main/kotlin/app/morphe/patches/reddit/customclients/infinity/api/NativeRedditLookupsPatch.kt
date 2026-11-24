/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.infinity.api

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val HOOK = "Lapp/morphe/extension/infinity/http/NativeRedditLookups;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val MAP = "Ljava/util/Map;"
private const val LIST = "Ljava/util/List;"

private fun List<CharSequence>.registerWords(): Int = size + count { it == "J" || it == "D" }

private fun Method.parameterField(word: Int): FieldReference {
    val code = checkNotNull(implementation)
    val receiver = code.registerCount - 1 - parameterTypes.registerWords()
    return code.instructions.single {
        it.opcode in setOf(Opcode.IPUT_OBJECT, Opcode.IPUT, Opcode.IPUT_WIDE) &&
            it is TwoRegisterInstruction && it.registerA == receiver + word && it.registerB == receiver
    }.getReference<FieldReference>()!!
}

internal val infinityLookupTransportPatch = patch(default = false) {
    compatibleWith(*AppCompatibility.Infinity)
    dependsOn(ExtensionPatches.Infinity)
    execute {
        val chainMethod = Fingerprint(strings = listOf(
            " must call proceed() exactly once", " must retain the same host and port",
        )).method
        // The Random patch may already have wrapped proceed, moving its strings to a private copy.
        val proceed = classDefBy(chainMethod.definingClass).methods.single {
            AccessFlags.PUBLIC.isSet(it.accessFlags) && it.parameterTypes == chainMethod.parameterTypes &&
                it.returnType == chainMethod.returnType
        }
        val request = proceed.parameterTypes.single().toString()
        val response = proceed.returnType
        val requestType = classDefBy(request)
        val requestConstructor = requestType.methods.single {
            it.name == "<init>" && it.parameterTypes.size == 5 &&
                it.parameterTypes[1] == STRING && it.parameterTypes.last() == MAP
        }
        val url = requestConstructor.parameterField(1)
        val urlType = requestConstructor.parameterTypes[0].toString()
        val verb = requestConstructor.parameterField(2)
        val requestHeaders = requestConstructor.parameterField(3)
        val headersType = requestConstructor.parameterTypes[2].toString()
        val requestBody = requestConstructor.parameterField(4)
        val requestBodyType = requestConstructor.parameterTypes[3].toString()
        val requestBuild = Fingerprint(parameters = emptyList(), returnType = request, strings = listOf("url == null")).method
        val requestBuilder = requestBuild.definingClass
        val copyRequest = requestType.methods.single { it.parameterTypes.isEmpty() && it.returnType == requestBuilder }
        val setUrl = Fingerprint(definingClass = requestBuilder, parameters = listOf(STRING),
            strings = listOf("ws:", "wss:")).method
        val setMethod = Fingerprint(definingClass = requestBuilder, parameters = listOf(STRING, requestBodyType),
            strings = listOf(" must have a request body.")).method
        val form = Fingerprint(name = "<clinit>", strings = listOf("application/x-www-form-urlencoded"),
            custom = { _, owner -> owner.superclass == requestBodyType })
        val formType = form.classDef
        val formInitializer = form.method.implementation!!.instructions
        val parseMediaType = formInitializer
            .mapNotNull { it.getReference<MethodReference>() }
            .single { it.parameterTypes == listOf(STRING) }
        val mediaTypeCompanion = formInitializer.filter { it.opcode == Opcode.SGET_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }
            .singleOrNull { it.type == parseMediaType.definingClass }
        val formConstructor = formType.methods.single { it.name == "<init>" && it.parameterTypes.size == 2 }
        // The constructor reuses a temporary register for both immutable form lists.
        val formFields = formConstructor.implementation!!.instructions
            .filter { it.opcode == Opcode.IPUT_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass == formType.type && it.type == LIST }
        check(formFields.size == 2 && formFields[0] != formFields[1])
        val (names, values) = formFields

        val responseType = classDefBy(response)
        val responseConstructor = responseType.methods.single {
            it.name == "<init>" && it.parameterTypes.size == 13 && it.parameterTypes[5] == headersType
        }
        val status = responseType.fields.single { it.type == "I" && !AccessFlags.STATIC.isSet(it.accessFlags) }
        val responseHeaders = responseType.fields.single { it.type == headersType }
        val responseBody = responseType.fields.single { it.type == responseConstructor.parameterTypes[6] }
        val bodyType = classDefBy(responseBody.type)
        val bodyLength = bodyType.methods.single { it.parameterTypes.isEmpty() && it.returnType == "J" }
        val byteStream = bodyType.methods.single { it.parameterTypes.isEmpty() && it.returnType == "Ljava/io/InputStream;" }
        val source = byteStream.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .single { it.definingClass == bodyType.type && it.parameterTypes.isEmpty() }
        val sourceType = classDefBy(source.returnType)
        // Older Okio retains both buffer() and the deprecated getBuffer().
        val bufferGetter = sourceType.methods.filter { method ->
            method.parameterTypes.isEmpty() && method.returnType.startsWith("L") &&
                !method.returnType.startsWith("Ljava/") && classDefBy(method.returnType).superclass == OBJECT &&
                classDefBy(method.returnType).methods.any { it.name == "clone" && it.parameterTypes.isEmpty() }
        }.distinctBy { it.returnType }.single()
        val buffer = mutableClassDefBy(bufferGetter.returnType)
        val size = buffer.fields.single { it.type == "J" && !AccessFlags.STATIC.isSet(it.accessFlags) }
        val readBytes = buffer.methods.single { it.parameterTypes == listOf("J") && it.returnType == "[B" }
        val writeBytes = buffer.methods.single { it.name == "write" && it.parameterTypes == listOf("[B") }
        val requestBytes = sourceType.methods.single { it.parameterTypes == listOf("J") && it.returnType == "Z" }
        if (buffer.methods.none { it.name == "<init>" && it.parameterTypes.isEmpty() }) {
            // R8 can inline Okio's empty Buffer constructor at every stock call site.
            check(buffer.superclass == OBJECT)
            buffer.methods.add(ImmutableMethod(buffer.type, "<init>", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, null, null,
                MutableMethodImplementation(1)).toMutable().apply {
                addInstructions(0, "invoke-direct {p0}, $OBJECT-><init>()V\nreturn-void")
            })
        }
        val currentBodyParams = listOf(OBJECT, "J", sourceType.type, "B")
        val legacyBodyParams = listOf(parseMediaType.returnType, "J", sourceType.type)
        val concreteBody = buildList {
            classDefForEach { owner ->
                if (owner.superclass == bodyType.type) addAll(owner.methods.filter {
                    it.name == "<init>" && (it.parameterTypes == currentBodyParams || it.parameterTypes == legacyBodyParams)
                })
            }
        }.single()
        val responseBuild = Fingerprint(returnType = response, parameters = emptyList(),
            strings = listOf("code < 0: ", "message == null")).method
        val responseBuilder = classDefBy(responseBuild.definingClass)
        val copyResponse = responseType.methods.single { it.parameterTypes.isEmpty() && it.returnType == responseBuilder.type }
        val builderRequest = responseBuilder.fields.single { it.type == request }
        val builderStatus = responseBuilder.fields.single { it.type == "I" }
        val builderMessage = responseBuilder.fields.single { it.type == STRING }
        val builderBody = responseBuilder.fields.single { it.type == bodyType.type }
        val headers = classDefBy(headersType)
        val getHeader = headers.methods.single { it.parameterTypes == listOf(STRING) && it.returnType == STRING }
        val mutableHeaders = requestBuild.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .single { it.returnType == headersType && it.parameterTypes.isEmpty() }.definingClass
        val builderHeaders = responseBuilder.fields.single { it.type == mutableHeaders }
        fun Method.headerCalls() = (implementation?.instructions ?: emptyList()).mapNotNull { it.getReference<MethodReference>() }
            .filter { it.definingClass == mutableHeaders }
        val setters = (classDefBy(requestBuilder).methods + classDefBy(mutableHeaders).methods).filter {
            it.parameterTypes == listOf(STRING, STRING)
        }
        val headerUpdate = setters.map { it.headerCalls() }.single { calls ->
            calls.any { it.parameterTypes == listOf(STRING) } &&
                calls.any { it.parameterTypes == listOf(STRING, STRING) }
        }
        val removeHeader = headerUpdate.single { it.parameterTypes == listOf(STRING) }
        val addHeader = headerUpdate.single { it.parameterTypes == listOf(STRING, STRING) }

        val bridge = mutableClassDefBy(HOOK)
        fun bind(name: String, locals: Int, code: String) {
            val stub = bridge.methods.single { it.name == name }
            val parameters = stub.parameterTypes.registerWords()
            bridge.methods.remove(stub)
            bridge.methods.add(ImmutableMethod(bridge.type, name, stub.parameters, stub.returnType,
                stub.accessFlags, stub.annotations, stub.hiddenApiRestrictions,
                MutableMethodImplementation(locals + parameters)).toMutable().apply {
                addInstructions(0, code.trimIndent())
            })
        }
        fun readObject(name: String, field: FieldReference, type: String = field.type) = bind(name, 0, """
            check-cast p0, ${field.definingClass}
            iget-object p0, p0, $field
            check-cast p0, $type
            return-object p0
        """)
        val stripPayloadHeaders = listOf("Content-Type", "Content-Length", "Content-Encoding",
            "Transfer-Encoding", "If-None-Match", "If-Modified-Since", "If-Match", "If-Unmodified-Since", "Range", "If-Range")
        fun strip(headersToRemove: List<String>, owner: String, field: FieldReference) = headersToRemove.joinToString("\n") {
            """
                iget-object v0, $owner, $field
                check-cast v0, $mutableHeaders
                const-string v1, "$it"
                invoke-virtual {v0, v1}, $removeHeader
            """.trimIndent()
        }
        val requestHeadersBuilder = requestBuild.implementation!!.instructions.zipWithNext().single { (field, next) ->
            field.opcode == Opcode.IGET_OBJECT && (field.getReference<FieldReference>()?.type == mutableHeaders ||
                next.opcode == Opcode.CHECK_CAST && next.getReference<TypeReference>()?.type == mutableHeaders)
        }.first.getReference<FieldReference>()!!
        bind("requestUrl", 0, """
            check-cast p0, $request
            iget-object p0, p0, $url
            check-cast p0, $urlType
            invoke-virtual {p0}, $urlType->toString()$STRING
            move-result-object p0
            return-object p0
        """)
        readObject("requestMethod", verb, STRING)
        bind("requestReferer", 1, """
            check-cast p0, $request
            iget-object p0, p0, $requestHeaders
            check-cast p0, $headersType
            const-string v0, "Referer"
            invoke-virtual {p0, v0}, $getHeader
            move-result-object p0
            return-object p0
        """)
        bind("nativeForm", 1, """
            check-cast p0, $request
            iget-object p0, p0, $requestBody
            instance-of v0, p0, ${formType.type}
            if-nez v0, :form
            const/4 p0, 0x0
            :form
            return-object p0
        """)
        readObject("formNames", names)
        readObject("formValues", values)
        bind("proceed", 0, """
            check-cast p0, ${proceed.definingClass}
            check-cast p1, $request
            invoke-virtual {p0, p1}, $proceed
            move-result-object p0
            return-object p0
        """)
        bind("focusedGet", 2, """
            check-cast p0, $request
            invoke-virtual {p0}, $copyRequest
            move-result-object p0
            invoke-virtual {p0, p1}, $setUrl
            const-string v0, "GET"
            const/4 v1, 0x0
            invoke-virtual {p0, v0, v1}, $setMethod
            ${strip(stripPayloadHeaders, "p0", requestHeadersBuilder)}
            invoke-virtual {p0}, $requestBuild
            move-result-object p0
            return-object p0
        """)
        bind("status", 0, """
            check-cast p0, $response
            iget p0, p0, $status
            return p0
        """)
        bind("responseHeader", 0, """
            check-cast p0, $response
            iget-object p0, p0, $responseHeaders
            invoke-virtual {p0, p1}, $getHeader
            move-result-object p0
            return-object p0
        """)
        bind("bodyLength", 2, """
            check-cast p0, $response
            iget-object p0, p0, $responseBody
            invoke-virtual {p0}, $bodyLength
            move-result-wide v0
            return-wide v0
        """)
        bind("bodyPrefix", 2, """
            check-cast p0, $response
            iget-object p0, p0, $responseBody
            invoke-virtual {p0}, $source
            move-result-object p0
            invoke-interface {p0, p1, p2}, $requestBytes
            invoke-interface {p0}, $bufferGetter
            move-result-object p0
            invoke-virtual {p0}, ${buffer.type}->clone()$OBJECT
            move-result-object p0
            check-cast p0, ${buffer.type}
            iget-wide v0, p0, $size
            invoke-static {v0, v1, p1, p2}, Ljava/lang/Math;->min(JJ)J
            move-result-wide v0
            invoke-virtual {p0, v0, v1}, $readBytes
            move-result-object p0
            return-object p0
        """)
        bind("jsonResponse", 6, """
            check-cast p0, $response
            check-cast p1, $request
            invoke-virtual {p0}, $copyResponse
            move-result-object p0
            iput-object p1, p0, $builderRequest
            const/16 v0, 0xc8
            iput v0, p0, $builderStatus
            const-string v0, "OK"
            iput-object v0, p0, $builderMessage
            ${strip(listOf("Content-Type", "Content-Length", "Content-Encoding", "Transfer-Encoding", "ETag", "Last-Modified"),
                "p0", builderHeaders)}
            iget-object v0, p0, $builderHeaders
            const-string v1, "Content-Type"
            const-string v2, "application/json; charset=utf-8"
            invoke-virtual {v0, v1, v2}, $addHeader
            new-instance v4, ${buffer.type}
            invoke-direct {v4}, ${buffer.type}-><init>()V
            invoke-virtual {v4, p2}, $writeBytes
            array-length v2, p2
            int-to-long v2, v2
            new-instance v0, ${concreteBody.definingClass}
            const-string v1, "application/json; charset=utf-8"
            ${if (mediaTypeCompanion == null) "invoke-static {v1}, $parseMediaType" else """
                sget-object v5, $mediaTypeCompanion
                invoke-virtual {v5, v1}, $parseMediaType
            """.trimIndent()}
            move-result-object v1
            ${if (concreteBody.parameterTypes == currentBodyParams) """
                const/4 v5, 0x1
                invoke-direct/range {v0 .. v5}, $concreteBody
            """.trimIndent() else "invoke-direct/range {v0 .. v4}, $concreteBody"}
            iput-object v0, p0, $builderBody
            invoke-virtual {p0}, $responseBuild
            move-result-object p0
            return-object p0
        """)

        // Hook the initial, application-level proceed call, not the recursive network chain.
        val entry = Fingerprint(returnType = response, parameters = emptyList(),
            strings = listOf("Canceled"), custom = { method, _ ->
                method.implementation?.instructions?.any {
                    it.getReference<MethodReference>()?.toString() == proceed.toString()
                } == true
            }).method
        val index = entry.implementation!!.instructions.indexOfFirst {
            it.getReference<MethodReference>()?.toString() == proceed.toString()
        }
        val call = entry.getInstruction<FiveRegisterInstruction>(index)
        check(call.opcode == Opcode.INVOKE_VIRTUAL && call.registerCount == 2)
        val result = entry.getInstruction<OneRegisterInstruction>(index + 1)
        check(result.opcode == Opcode.MOVE_RESULT_OBJECT)
        entry.replaceInstruction(index, """
            invoke-static {v${call.registerC}, v${call.registerD}}, $HOOK->intercept($OBJECT$OBJECT)$OBJECT
        """.trimIndent())
        entry.addInstruction(index + 2, "check-cast v${result.registerA}, $response")
    }
}
