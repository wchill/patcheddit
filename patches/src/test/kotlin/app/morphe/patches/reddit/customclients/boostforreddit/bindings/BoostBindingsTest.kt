/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.bindings

import app.morphe.patches.reddit.customclients.misc.extension.mapping.DexMappingRewriter
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.google.gson.Gson
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class BoostBindingsTest {
    private val public = AccessFlags.PUBLIC.value
    private val static = public or AccessFlags.STATIC.value
    private val libraries = ProguardMapping.classNames(emptyMap())

    private fun table(
        classes: Map<String, String> = mapOf("Lapi/Preferences;" to "Lp;"),
        methods: Map<String, String> = emptyMap(),
        fields: Map<String, String> = emptyMap(),
        staticMethods: List<String> = emptyList(),
        staticFields: List<String> = emptyList(),
        injectedMethods: Map<String, String> = emptyMap(),
    ): BoostBindingTable = BoostBindingTable.read(StringReader(Gson().toJson(mapOf(
        "version" to "test", "classes" to classes, "methods" to methods, "fields" to fields,
        "staticMethods" to staticMethods, "staticFields" to staticFields,
        "injectedMethods" to injectedMethods,
    ))))

    private fun cls(
        name: String,
        methods: List<ImmutableMethod> = emptyList(),
        fields: List<ImmutableField> = emptyList(),
        interfaces: List<String> = emptyList(),
        flags: Int = public,
    ): ClassDef = ImmutableClassDef(
        name, flags, "Ljava/lang/Object;", interfaces, null, emptyList(), fields, methods,
    )

    private fun method(
        owner: String,
        name: String,
        parameters: List<String> = emptyList(),
        returns: String = "V",
        flags: Int = public,
    ) = ImmutableMethod(
        owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
        returns, flags, emptySet(), emptySet(), null,
    )

    private fun field(owner: String, name: String, flags: Int = public) =
        ImmutableField(owner, name, "I", flags, null, emptyList(), emptySet())

    @Test
    fun resolvesTypedOverloadsAndExactReferences() {
        val source = "Lapi/Preferences;->read(Ljava/lang/String;)Ljava/lang/String;"
        val overload = "Lapi/Preferences;->read(I)I"
        val table = table(methods = mapOf(source to "Lp;->h(Ljava/lang/String;)Ljava/lang/String;", overload to "Lp;->b(I)I"))
        val binding = table.method(source)
        assertEquals("Lp;->h(Ljava/lang/String;)Ljava/lang/String;", binding.toString())
        assertTrue(binding.matches(ImmutableMethodReference("Lp;", "h", listOf("Ljava/lang/String;"), "Ljava/lang/String;")))
        assertEquals("b", table.method(overload).name)
        assertFails { table.method("Lapi/Preferences;->read()V") }
        assertFails { table.method("Lapi/Preferences;->read(Q)V") }
        assertFails { table.field("Lapi/Preferences;->missing:I") }
    }

    @Test
    fun rewritesMovedStaticMembersWithoutChangingNativeDefinitions() {
        val method = "Lapi/Preferences;->refresh()V"
        val field = "Lapi/Preferences;->generation:I"
        val table = table(
            methods = mapOf(method to "Lmerged;->b()V"),
            fields = mapOf(field to "Lmerged;->c:I"),
            staticMethods = listOf(method), staticFields = listOf(field),
        )
        val native = listOf(
            cls("Lp;"), cls("Lmerged;", listOf(method("Lmerged;", "b", flags = static)), listOf(field("Lmerged;", "c", static))),
        )
        val source = table.sourceDefinitions(native, libraries)
        val implementation = ImmutableMethodImplementation(1, listOf(
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ImmutableMethodReference("Lapi/Preferences;", "refresh", emptyList(), "V")),
            ImmutableInstruction21c(Opcode.SGET, 0, ImmutableFieldReference("Lapi/Preferences;", "generation", "I")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        ), emptyList(), emptyList())
        val caller = cls("Lextension/Caller;", listOf(ImmutableMethod(
            "Lextension/Caller;", "run", emptyList(), "V", static, emptySet(), emptySet(), implementation,
        )))
        val output = DexMappingRewriter(table.mapping(libraries), native + source + caller).rewrite(listOf(caller)).single()
        val instructions = output.methods.single().implementation!!.instructions.toList()
        val call = (instructions[0] as ReferenceInstruction).reference as MethodReference
        val read = (instructions[1] as ReferenceInstruction).reference as FieldReference
        assertEquals("Lmerged;", call.definingClass)
        assertEquals("b", call.name)
        assertEquals("Lmerged;", read.definingClass)
        assertEquals("c", read.name)
        assertEquals(listOf("b"), native.last().methods.map { it.name })
    }

    @Test
    fun bindsCallbackDeclarationsAndCallsThroughStableInterfaces() {
        val table = table(
            classes = mapOf("Lapi/Callback;" to "Lc;"),
            methods = mapOf("Lapi/Callback;->onChanged(I)V" to "Lc;->a(I)V"),
        )
        val native = listOf(cls(
            "Lc;", listOf(method("Lc;", "a", listOf("I"), flags = public or AccessFlags.ABSTRACT.value)),
            flags = public or AccessFlags.INTERFACE.value or AccessFlags.ABSTRACT.value,
        ))
        val caller = cls(
            "Lextension/Listener;", listOf(method("Lextension/Listener;", "onChanged", listOf("I"))),
            interfaces = listOf("Lapi/Callback;"),
        )
        val output = DexMappingRewriter(
            table.mapping(libraries), native + table.sourceDefinitions(native, libraries) + caller,
        ).rewrite(listOf(caller)).single()
        assertEquals(listOf("Lc;"), output.interfaces)
        assertEquals("a", output.methods.single().name)
    }

    @Test
    fun permitsReferenceAliasesButNeverDuplicateOutputClasses() {
        val table = table(classes = mapOf("Lapi/First;" to "Lp;", "Lapi/Second;" to "Lp;"))
        val mapping = table.mapping(libraries)
        val native = listOf(cls("Lp;"))
        assertEquals(2, table.sourceDefinitions(native, libraries).size)
        assertFails { mapping.reversed() }
        assertFails {
            DexMappingRewriter(mapping, listOf(cls("Lapi/First;"), cls("Lapi/Second;")))
                .rewrite(listOf(cls("Lapi/First;"), cls("Lapi/Second;")))
        }
    }

    @Test
    fun validatesInjectedBridgeAgainstItsNativeFactory() {
        val factory = "Lapi/Preferences;->create(I)Lapi/Preferences;"
        val bridge = "Lapi/Preferences;->createOriginal(I)Lapi/Preferences;"
        val table = table(
            methods = mapOf(factory to "Lp;->a(I)Lp;", bridge to "Lp;->morpheOriginal(I)Lp;"),
            staticMethods = listOf(factory, bridge), injectedMethods = mapOf(bridge to factory),
        )
        val native = listOf(cls("Lp;", listOf(method("Lp;", "a", listOf("I"), "Lp;", static))))
        assertEquals(2, table.sourceDefinitions(native, libraries).single().methods.count())
        assertFails {
            table.sourceDefinitions(listOf(cls("Lp;", listOf(
                method("Lp;", "a", listOf("I"), "Lp;", static),
                method("Lp;", "morpheOriginal", listOf("I"), "Lp;", static),
            ))), libraries)
        }
    }

    @Test
    fun rejectsMissingNativeMembersAndDispatchChanges() {
        val source = "Lapi/Preferences;->refresh()V"
        val table = table(methods = mapOf(source to "Lp;->a()V"), staticMethods = listOf(source))
        assertFails { table.sourceDefinitions(emptyList(), libraries) }
        assertFails { table.sourceDefinitions(listOf(cls("Lp;")), libraries) }
        assertFails { table.sourceDefinitions(listOf(cls("Lp;", listOf(method("Lp;", "a")))), libraries) }
        val fields = table(fields = mapOf("Lapi/Preferences;->count:I" to "Lp;->a:I"))
        assertFails { fields.sourceDefinitions(listOf(cls("Lp;")), libraries) }
        assertFails { fields.sourceDefinitions(listOf(cls("Lp;", fields = listOf(field("Lp;", "a", static)))), libraries) }
    }

    @Test
    fun rejectsPrototypeChangesAndConflictingLibraryBindings() {
        val table = table(methods = mapOf("Lapi/Preferences;->refresh(I)V" to "Lp;->a(J)V"))
        assertFails { table.mapping(libraries) }
        val classes = table()
        assertFails { classes.mapping(ProguardMapping.classNames(mapOf("Lapi/Preferences;" to "Lother;"))) }
        val fields = table(fields = mapOf("Lapi/Preferences;->count:I" to "Lp;->a:J"))
        assertFails { fields.mapping(libraries) }
    }

    @Test
    fun resolvesOnlyExplicitReflectiveFieldMarkers() {
        val table = table(fields = mapOf("Lapi/Preferences;->count:I" to "Lp;->a:I"))
        val rewriter = DexMappingRewriter(table.mapping(libraries), emptyList())
        assertEquals("a", rewriter.rewriteClassName("@boost-field:Lapi/Preferences;->count:I"))
        assertEquals("count", rewriter.rewriteClassName("count"))
        assertEquals("a", rewriter.rewriteClassName("a"))
        assertFails { rewriter.rewriteClassName("@boost-field:Lapi/Preferences;->missing:I") }
        val isolation = DexMappingRewriter(libraries, emptyList(), resolveReflectiveFields = false)
        assertEquals(
            "@boost-field:Lapi/Preferences;->count:I",
            isolation.rewriteClassName("@boost-field:Lapi/Preferences;->count:I"),
        )
    }

    @Test
    fun rebindsTheSameCompiledCallerUsingOnlyDifferentNativeTargets() {
        val call = ImmutableMethodReference("Lapi/Preferences;", "refresh", emptyList(), "V")
        val read = ImmutableFieldReference("Lapi/Preferences;", "count", "I")
        val caller = cls("Lextension/Caller;", listOf(ImmutableMethod(
            "Lextension/Caller;", "run",
            listOf(ImmutableMethodParameter("Lapi/Preferences;", emptySet(), null)), "V", static,
            emptySet(), emptySet(), ImmutableMethodImplementation(2, listOf(
                ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0, call),
                ImmutableInstruction22c(Opcode.IGET, 0, 1, read),
                ImmutableInstruction10x(Opcode.RETURN_VOID),
            ), emptyList(), emptyList()),
        )))
        for ((owner, name, field) in listOf(Triple("La;", "b", "c"), Triple("Lupdated;", "x", "y"))) {
            val table = table(
                classes = mapOf("Lapi/Preferences;" to owner),
                methods = mapOf("Lapi/Preferences;->refresh()V" to "$owner->$name()V"),
                fields = mapOf("Lapi/Preferences;->count:I" to "$owner->$field:I"),
            )
            val native = listOf(cls(owner, listOf(method(owner, name)), listOf(field(owner, field))))
            val output = DexMappingRewriter(
                table.mapping(libraries), table.sourceDefinitions(native, libraries) + caller,
            ).rewrite(listOf(caller)).single()
            val body = output.methods.single().implementation!!.instructions.toList()
            val target = (body[0] as ReferenceInstruction).reference as MethodReference
            val targetField = (body[1] as ReferenceInstruction).reference as FieldReference
            assertEquals(owner, target.definingClass)
            assertEquals(name, target.name)
            assertEquals(owner, targetField.definingClass)
            assertEquals(field, targetField.name)
        }
        val original = (caller.methods.single().implementation!!.instructions.first() as ReferenceInstruction).reference
        assertEquals(call, original)
    }

    @Test
    fun rejectsUnrelatedInstanceMemberOwners() {
        val methods = table(methods = mapOf("Lapi/Preferences;->refresh()V" to "Lother;->a()V"))
        val native = listOf(cls("Lp;"), cls("Lother;", listOf(method("Lother;", "a")), listOf(field("Lother;", "b"))))
        assertFails { methods.sourceDefinitions(native, libraries) }
        val fields = table(fields = mapOf("Lapi/Preferences;->count:I" to "Lother;->b:I"))
        assertFails { fields.sourceDefinitions(native, libraries) }
    }

    @Test
    fun rejectsDuplicateKeysInvalidSectionsAndNonStringTargets() {
        val json = """
            {"version":"test","classes":{"Lapi/Preferences;":"Lp;"},"methods":{},"fields":{},
             "staticMethods":[],"staticFields":[],"injectedMethods":{}}
        """.trimIndent()
        val invalid = listOf(
            json.replace("\"version\":\"test\"", "\"version\":42"),
            json.replace("\"version\":\"test\"", "\"version\":\"first\",\"version\":\"second\""),
            json.replace("\"Lp;\"", "\"Lp;\",\"Lapi/Preferences;\":\"Lother;\""),
            json.replace("\"methods\":{}", "\"methods\":{\"Lapi/Preferences;->refresh()V\":false}"),
            json.replace("\"injectedMethods\":{}", "\"unknown\":{}"),
            json + "{}",
        )
        for (value in invalid) assertFails { BoostBindingTable.read(StringReader(value)) }
    }
}
