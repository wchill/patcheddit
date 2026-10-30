/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import app.morphe.patches.reddit.customclients.misc.extension.mapping.DexMappingRewriter
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpLibraryReferencesTest {
    private val request = privateHttpType("Lokhttp3/Request;")
    private val public = AccessFlags.PUBLIC.value

    @Test
    fun redirectsObfuscatedCallsToCompletePrivateLibraryWithoutRenamingAppClasses() {
        val parsed = ProguardMapping.read(StringReader("""
            okhttp3.Request -> a:
                java.lang.String method() -> b
        """.trimIndent()))
        val mapping = parsed.mapTargets(::privateHttpType)
            .withBindings(mapOf("Lokhttp3/Request;" to request), emptyMap(), emptyMap())
        val native = cls("La;", method("La;", "b", "Ljava/lang/String;"))
        val caller = cls("Lapp/Caller;", method("Lapp/Caller;", "run", instructions = listOf(
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference("La;", "b", emptyList(), "Ljava/lang/String;")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )))
        val complete = cls(request, method(request, "method", "Ljava/lang/String;"))
        val rewritten = DexMappingRewriter(mapping, listOf(native, caller, complete))
            .rewrite(listOf(caller)).single()
        val reference = rewritten.methods.single().implementation!!.instructions
            .filterIsInstance<ReferenceInstruction>().single().reference as MethodReference
        assertEquals("Lapp/Caller;", rewritten.type)
        assertEquals(request, reference.definingClass)
        assertEquals("method", reference.name)
        assertEquals("b", native.methods.single().name)
        verifyHttpLibraryReferences(listOf(native, rewritten, complete), setOf(rewritten.type))
    }

    @Test
    fun rejectsMissingLibraryMethodsAndWrongDispatch() {
        val caller = cls("Lapp/Caller;", method("Lapp/Caller;", "run", instructions = listOf(
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ImmutableMethodReference(request, "method", emptyList(), "Ljava/lang/String;")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )))
        assertFailsWith<IllegalArgumentException> {
            verifyHttpLibraryReferences(listOf(caller, cls(request)), setOf(caller.type))
        }
        assertFailsWith<IllegalArgumentException> {
            verifyHttpLibraryReferences(
                listOf(caller, cls(request, method(request, "method", "Ljava/lang/String;"))),
                setOf(caller.type),
            )
        }
    }

    @Test
    fun checksInheritedPrivateLibraryReferencesWithoutRejectingUnrelatedAppLibraries() {
        val parent = privateHttpType("Lokhttp3/RequestBody;")
        val child = privateHttpType("Lokhttp3/CustomBody;")
        val caller = cls("Lapp/Caller;", method("Lapp/Caller;", "run", instructions = listOf(
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference(child, "contentLength", emptyList(), "J")),
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ImmutableMethodReference("Loptional/Unrelated;", "run", emptyList(), "V")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )))
        verifyHttpLibraryReferences(
            listOf(caller, cls(parent, method(parent, "contentLength", "J")), cls(child, parent = parent)),
            setOf(caller.type),
        )
    }

    @Test
    fun bundlesTheCompleteClientsPublicSuffixDatabase() {
        val input = assertNotNull(javaClass.classLoader.getResourceAsStream(
            "http/okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz",
        ))
        input.use {
            val data = java.util.zip.GZIPInputStream(it).readBytes()
            assertTrue(data.isNotEmpty())
        }
    }

    @Test
    fun routesLegacyUtilityReferencesToTheCompleteOkHttp5Facade() {
        assertEquals(
            HTTP_LIBRARY_PREFIX + "okhttp3/internal/_UtilCommonKt;",
            privateHttpType("Lokhttp3/internal/Util;"),
        )
    }

    @Test
    fun keepsTransportKotlinPrivateFromShrunkenNativeKotlin() {
        assertEquals(HTTP_LIBRARY_PREFIX + "kotlin/Unit;", privateSharedLibraryType("Lkotlin/Unit;"))
        assertEquals(
            HTTP_LIBRARY_PREFIX + "kotlin/jvm/internal/DefaultConstructorMarker;",
            privateSharedLibraryType("Lkotlin/jvm/internal/DefaultConstructorMarker;"),
        )
        assertEquals(
            HTTP_LIBRARY_PREFIX + "kotlin/jvm/functions/Function1;",
            privateSharedLibraryType("Lkotlin/jvm/functions/Function1;"),
        )
    }

    private fun cls(
        type: String,
        vararg methods: ImmutableMethod,
        parent: String = "Ljava/lang/Object;",
    ): ClassDef = ImmutableClassDef(
        type, public, parent, emptyList(), null, emptyList(), emptyList(), methods.toList(),
    )

    private fun method(
        owner: String,
        name: String,
        returns: String = "V",
        instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>? = null,
    ) = ImmutableMethod(
        owner, name, emptyList<ImmutableMethodParameter>(), returns, public,
        emptySet(), emptySet(), instructions?.let { ImmutableMethodImplementation(1, it, emptyList(), emptyList()) },
    )
}
