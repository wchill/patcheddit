/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.mapping

import app.morphe.patches.reddit.customclients.misc.extension.mapping.DexMappingRewriter
import app.morphe.patches.reddit.customclients.misc.extension.mapping.FieldId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.MethodId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import app.morphe.patches.reddit.customclients.misc.extension.mapping.forEachReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.AnnotationVisibility
import com.android.tools.smali.dexlib2.MethodHandleType
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.value.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.*
import com.android.tools.smali.dexlib2.immutable.value.*
import java.io.StringReader
import kotlin.test.*

class MappingTest {
    private val public = AccessFlags.PUBLIC.value
    private val abstract = public or AccessFlags.ABSTRACT.value
    private fun mapping(text: String) = ProguardMapping.read(StringReader(text.trimIndent()))

    private fun cls(
        name: String,
        parent: String = "Ljava/lang/Object;",
        interfaces: List<String> = emptyList(),
        methods: List<ImmutableMethod> = emptyList(),
        fields: List<ImmutableField> = emptyList(),
        annotations: List<ImmutableAnnotation> = emptyList(),
        flags: Int = abstract,
    ) = ImmutableClassDef(name, flags, parent, interfaces, null, annotations, fields, methods)

    private fun method(
        owner: String,
        name: String,
        parameters: List<String> = emptyList(),
        returnType: String = "V",
        flags: Int = abstract,
        body: List<Instruction>? = null,
    ): ImmutableMethod {
        val implementation = body?.let { ImmutableMethodImplementation(8, it, emptyList(), emptyList()) }
        return ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returnType,
            if (body == null) flags else flags and AccessFlags.ABSTRACT.value.inv(),
            emptySet<ImmutableAnnotation>(), emptySet(), implementation,
        )
    }

    private fun field(
        owner: String,
        name: String,
        type: String = "I",
        flags: Int = public,
        value: EncodedValue? = null,
    ) = ImmutableField(owner, name, type, flags, value, emptySet(), emptySet())

    private val returnVoid = ImmutableInstruction10x(Opcode.RETURN_VOID)

    private fun rewrite(mapping: ProguardMapping, vararg definitions: ClassDef) =
        DexMappingRewriter(mapping, definitions.toList()).rewrite(definitions.toList()).associateBy { it.type }

    @Test
    fun parsesArraysLineRangesAndReverseBindings() {
        val mapping = mapping(
            """
            # {"id":"com.android.tools.r8.mapping","version":"2.2"}
            library.Owner -> a:
                library.Value[] values -> b
                1:2:void accept(byte[][],boolean,library.Value[]):10:11 -> c
            library.Value -> v:
            """,
        )
        val id = MethodId("La;", "c", listOf("[[B", "Z", "[Lv;"), "V")
        assertEquals("accept", mapping.methods[id])
        assertEquals("values", mapping.fields[FieldId("La;", "b", "[Lv;")])
        assertEquals("[[Llibrary/Value;", mapping.type("[[Lv;"))
        assertEquals("I", mapping.type("I"))
        val reversed = mapping.reversed()
        assertEquals("c", reversed.methods[MethodId("Llibrary/Owner;", "accept", listOf("[[B", "Z", "[Llibrary/Value;"), "V")])
        assertEquals(mapping.classes, reversed.reversed().classes)
        assertEquals(mapping.methods, reversed.reversed().methods)
        assertEquals(mapping.fields, reversed.reversed().fields)
    }

    @Test
    fun bindsOnlyExtensionCallsToObfuscatedNativeMembers() {
        val mapping = mapping(
            """
            library.Api -> a:
                library.Payload fetch(library.Payload) -> b
            library.Payload -> p:
            """,
        ).reversed()
        val native = cls("La;", methods = listOf(method("La;", "b", listOf("Lp;"), "Lp;")))
        val api = cls("Llibrary/Api;", methods = listOf(
            method("Llibrary/Api;", "fetch", listOf("Llibrary/Payload;"), "Llibrary/Payload;"),
        ))
        val extension = cls("Lextension/Calls;", methods = listOf(method("Lextension/Calls;", "run", body = listOf(
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 0, 1, 0, 0, 0,
                ImmutableMethodReference("Llibrary/Api;", "fetch", listOf("Llibrary/Payload;"), "Llibrary/Payload;")),
            returnVoid,
        ))))
        val rewriter = DexMappingRewriter(mapping, listOf(native, api, extension))
        val output = rewriter.rewrite(listOf(extension)).single()
        val reference = (output.methods.single().implementation!!.instructions.first() as ReferenceInstruction)
            .reference as MethodReference
        assertEquals("Lextension/Calls;", output.type)
        assertEquals("La;", reference.definingClass)
        assertEquals("b", reference.name)
        assertEquals(listOf("Lp;"), reference.parameterTypes.map(CharSequence::toString))
        assertEquals("Lp;", reference.returnType)
        assertEquals("La;", native.type)
        assertEquals("b", native.methods.single().name)
        assertEquals("Llibrary/Api;", api.type)
        assertEquals("fetch", api.methods.single().name)
    }

    @Test
    fun rejectsMalformedMergedAndAmbiguousMappings() {
        val invalid = listOf(
            "", "not a mapping", "    int field -> a", "int -> a:",
            "A -> a:\nA -> b:", "A -> a:\nB -> a:",
            "A -> a:\n    void value -> b", "A -> a:\n    void call(void) -> b",
            "A -> a:\n    void[] call() -> b", "A -> a:\n    void B.inlined() -> b",
            "A -> a:\n    void first() -> b\n    void second() -> b",
            "A -> a:\n    void renamed() -> <init>",
            "../Owner -> a:", "pkg\\Owner -> a:", "pkg..Owner -> a:",
        )
        for (text in invalid) assertFails(text) { mapping(text) }
    }

    @Test
    fun propagatesRenamesThroughInheritedImplementationsAndSiblingOverrides() {
        val mapping = mapping(
            """
            library.Base -> b:
                void a(library.Payload[]) -> a
            library.Callback -> i:
                void accept(library.Payload[]) -> a
            library.Payload -> p:
            """,
        )
        val reference = ImmutableMethodReference("Ld;", "a", listOf("[Lp;"), "V")
        val output = rewrite(
            mapping,
            cls("Lb;", methods = listOf(method("Lb;", "a", listOf("[Lp;")))),
            cls("Li;", methods = listOf(method("Li;", "a", listOf("[Lp;"))), flags = abstract or AccessFlags.INTERFACE.value),
            cls("Lc;", "Lb;", listOf("Li;")),
            cls("Ld;", "Lb;", methods = listOf(method("Ld;", "a", listOf("[Lp;")))),
            cls("Lp;"),
            cls("Lu;", methods = listOf(method("Lu;", "run", body = listOf(
                ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 0, 1, 0, 0, 0, reference), returnVoid,
            )))),
        )
        for (owner in listOf("Llibrary/Base;", "Llibrary/Callback;", "Ld;")) {
            val method = output.getValue(owner).methods.single()
            assertEquals("accept", method.name)
            assertEquals(listOf("[Llibrary/Payload;"), method.parameterTypes.map(CharSequence::toString))
        }
        val call = (output.getValue("Lu;").methods.single().implementation!!.instructions.first() as ReferenceInstruction)
            .reference as MethodReference
        assertEquals("accept", call.name)
        assertEquals("Ld;", call.definingClass)
    }

    @Test
    fun respectsPrivateAndStaticMethodsAndHiddenFields() {
        val mapping = mapping(
            """
            library.Base -> b:
                void hidden() -> a
                void helper() -> s
                int counter -> x
            """,
        )
        val read = ImmutableFieldReference("Lt;", "x", "I")
        val output = rewrite(
            mapping,
            cls("Lb;", methods = listOf(
                method("Lb;", "a", flags = AccessFlags.PRIVATE.value, body = listOf(returnVoid)),
                method("Lb;", "s", flags = public or AccessFlags.STATIC.value, body = listOf(returnVoid)),
            ), fields = listOf(field("Lb;", "x"))),
            cls("Lc;", "Lb;", methods = listOf(method("Lc;", "a"), method("Lc;", "s")), fields = listOf(field("Lc;", "x"))),
            cls("Lt;", "Lc;", methods = listOf(method("Lt;", "run", body = listOf(
                ImmutableInstruction22c(Opcode.IGET, 0, 1, read), returnVoid,
            )))),
        )
        assertEquals(setOf("a", "s"), output.getValue("Lc;").methods.map { it.name }.toSet())
        assertEquals("counter", output.getValue("Llibrary/Base;").fields.single().name)
        val reference = (output.getValue("Lt;").methods.single().implementation!!.instructions.first() as ReferenceInstruction)
            .reference as FieldReference
        assertEquals("x", reference.name)
    }

    @Test
    fun rewritesGenericNestedAndInnerClassAnnotations() {
        val mapping = mapping(
            """
            library.Label -> t:
                java.lang.String value() -> a
            library.Payload -> oldpkg.p:
            library.Outer${'$'}Inner -> old${'$'}a:
            """,
        )
        val signature = ImmutableAnnotation(AnnotationVisibility.SYSTEM, "Ldalvik/annotation/Signature;", listOf(
            ImmutableAnnotationElement("value", ImmutableArrayEncodedValue(
                listOf("Ljava/util/List<", "Loldpkg/p;", ">;").map(::ImmutableStringEncodedValue),
            )),
        ))
        val nested = ImmutableAnnotation(AnnotationVisibility.RUNTIME, "Lcontainer;", listOf(
            ImmutableAnnotationElement("nested", ImmutableAnnotationEncodedValue("Lt;", listOf(
                ImmutableAnnotationElement("a", ImmutableStringEncodedValue("oldpkg.p")),
            ))),
        ))
        val inner = ImmutableAnnotation(AnnotationVisibility.SYSTEM, "Ldalvik/annotation/InnerClass;", listOf(
            ImmutableAnnotationElement("name", ImmutableStringEncodedValue("a")),
        ))
        val output = rewrite(
            mapping, cls("Lt;", methods = listOf(method("Lt;", "a", returnType = "Ljava/lang/String;"))),
            cls("Loldpkg/p;"), cls("Lold${'$'}a;", annotations = listOf(signature, nested, inner)),
        ).getValue("Llibrary/Outer${'$'}Inner;")
        val annotations = output.annotations.associateBy { it.type }
        val fragments = annotations.getValue(signature.type).elements.single().value as ArrayEncodedValue
        assertEquals("Ljava/util/List<Llibrary/Payload;>;", (fragments.value.single() as StringEncodedValue).value)
        val value = annotations.getValue(nested.type).elements.single().value as AnnotationEncodedValue
        assertEquals("Llibrary/Label;", value.type)
        assertEquals("value", value.elements.single().name)
        assertEquals("library.Payload", (value.elements.single().value as StringEncodedValue).value)
        assertEquals("Inner", (annotations.getValue(inner.type).elements.single().value as StringEncodedValue).value)
    }

    @Test
    fun rewritesLambdaNamesHandlesAndEncodedPrototypes() {
        val mapping = mapping(
            """
            library.Callback -> i:
                void accept(library.Payload) -> a
            library.Payload -> p:
                void apply(library.Payload) -> b
            """,
        )
        val prototype = ImmutableMethodProtoReference(listOf("Lp;"), "V")
        val implementation = ImmutableMethodHandleReference(MethodHandleType.INVOKE_STATIC,
            ImmutableMethodReference("Lp;", "b", listOf("Lp;"), "V"))
        val bootstrap = ImmutableMethodHandleReference(MethodHandleType.INVOKE_STATIC,
            ImmutableMethodReference("Ljava/lang/invoke/LambdaMetafactory;", "metafactory", emptyList(), "Ljava/lang/invoke/CallSite;"))
        val arguments = listOf(
            ImmutableMethodTypeEncodedValue(prototype), ImmutableMethodHandleEncodedValue(implementation),
            ImmutableMethodTypeEncodedValue(prototype),
        )
        val callSite = ImmutableCallSiteReference("lambda", bootstrap, "a",
            ImmutableMethodProtoReference(emptyList(), "Li;"), arguments.asIterable())
        val caller = cls("Lc;", fields = listOf(field("Lc;", "metadata", "[Ljava/lang/Object;",
            public or AccessFlags.STATIC.value, ImmutableArrayEncodedValue(arguments))),
            methods = listOf(method("Lc;", "run", body = listOf(
                ImmutableInstruction35c(Opcode.INVOKE_CUSTOM, 0, 0, 0, 0, 0, 0, callSite), returnVoid,
            ))))
        val output = rewrite(
            mapping, caller,
            cls("Li;", methods = listOf(method("Li;", "a", listOf("Lp;")))),
            cls("Lp;", methods = listOf(method("Lp;", "b", listOf("Lp;"),
                flags = public or AccessFlags.STATIC.value, body = listOf(returnVoid)))),
        ).getValue("Lc;")
        val site = (output.methods.single().implementation!!.instructions.first() as ReferenceInstruction)
            .reference as CallSiteReference
        assertEquals("accept", site.methodName)
        assertEquals("Llibrary/Callback;", site.methodProto.returnType)
        val values = (output.fields.single().initialValue as ArrayEncodedValue).value
        assertEquals(listOf("Llibrary/Payload;"), (values[0] as MethodTypeEncodedValue).value.parameterTypes)
        val handle = (values[1] as MethodHandleEncodedValue).value.memberReference as MethodReference
        assertEquals("Llibrary/Payload;", handle.definingClass)
        assertEquals("apply", handle.name)
        val types = mutableSetOf<String>()
        caller.forEachReference { if (it is TypeReference) types += it.type }
        assertTrue(setOf("Lp;", "Li;", "Ljava/lang/invoke/LambdaMetafactory;").all { it in types })
    }

    @Test
    fun preservesTypeVariableNamesAndRewritesNestedGenericClassSegments() {
        val mapping = mapping(
            """
            library.Outer -> o:
            library.Outer${'$'}Inner -> o${'$'}a:
            library.Payload -> p:
            """,
        )
        val signature = "<Lp:Ljava/lang/Object;>Lo<TLp;>.a<+Lp;>;"
        val annotation = ImmutableAnnotation(AnnotationVisibility.SYSTEM, "Ldalvik/annotation/Signature;", listOf(
            ImmutableAnnotationElement("value", ImmutableArrayEncodedValue(listOf(ImmutableStringEncodedValue(signature)))),
        ))
        val output = rewrite(mapping, cls("Lo;"), cls("Lo${'$'}a;"), cls("Lp;"), cls("Lc;", annotations = listOf(annotation)))
        val result = output.getValue("Lc;").annotations.single().elements.single().value as ArrayEncodedValue
        assertEquals("<Lp:Ljava/lang/Object;>Llibrary/Outer<TLp;>.Inner<+Llibrary/Payload;>;",
            (result.value.single() as StringEncodedValue).value)
    }

    @Test
    fun keepsUnmappedNestedSuffixesUnderTheRenamedOuterClass() {
        val mapping = mapping("library.Outer -> o:")
        val annotation = ImmutableAnnotation(AnnotationVisibility.SYSTEM, "Ldalvik/annotation/Signature;", listOf(
            ImmutableAnnotationElement("value", ImmutableArrayEncodedValue(listOf(
                ImmutableStringEncodedValue("Lo<Ljava/lang/String;>.a;"),
            ))),
        ))
        val output = rewrite(mapping, cls("Lo;"), cls("Lo${'$'}a;", annotations = listOf(annotation)))
        val inner = output.getValue("Llibrary/Outer${'$'}a;")
        val signature = inner.annotations.single().elements.single().value as ArrayEncodedValue
        assertEquals("Llibrary/Outer<Ljava/lang/String;>.a;", (signature.value.single() as StringEncodedValue).value)
    }

    @Test
    fun preservesAccessWhenPartialMappingSeparatesPackages() {
        val mapping = mapping(
            """
            library.Owner -> o:
                void call() -> a
                int value -> x
            """,
        )
        val output = rewrite(
            mapping,
            cls("Lo;", flags = 0, fields = listOf(field("Lo;", "x", flags = 0)), methods = listOf(
                method("Lo;", "a", flags = 0, body = listOf(returnVoid)),
                method("Lo;", "private", flags = AccessFlags.PRIVATE.value, body = listOf(returnVoid)),
            )),
            cls("Lc;", methods = listOf(method("Lc;", "run", body = listOf(
                ImmutableInstruction22c(Opcode.IGET, 0, 1, ImmutableFieldReference("Lo;", "x", "I")),
                ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
                    ImmutableMethodReference("Lo;", "a", emptyList(), "V")), returnVoid,
            )))),
        ).getValue("Llibrary/Owner;")
        assertTrue(AccessFlags.PUBLIC.isSet(output.accessFlags))
        assertTrue(AccessFlags.PUBLIC.isSet(output.fields.single().accessFlags))
        assertTrue(AccessFlags.PUBLIC.isSet(output.methods.single { it.name == "call" }.accessFlags))
        assertTrue(AccessFlags.PRIVATE.isSet(output.methods.single { it.name == "private" }.accessFlags))
    }

    @Test
    fun rejectsConflictingOverridesNewOverridesCollisionsAndJniRenames() {
        val methods = listOf(cls("Lb;", methods = listOf(method("Lb;", "a"))),
            cls("Lc;", "Lb;", methods = listOf(method("Lc;", "a"))))
        val conflict = mapping("Base -> b:\n    void one() -> a\nChild -> c:\n    void two() -> a")
        assertFailsWith<IllegalArgumentException> { DexMappingRewriter(conflict, methods) }
        val joining = mapping("Base -> b:\n    void same() -> a\nChild -> c:\n    void same() -> z")
        assertFailsWith<IllegalArgumentException> {
            DexMappingRewriter(joining, listOf(methods[0], cls("Lc;", "Lb;", methods = listOf(method("Lc;", "z")))))
        }
        assertFailsWith<IllegalArgumentException> {
            rewrite(ProguardMapping.classNames(mapOf("La;" to "Lb;")), cls("La;"), cls("Lb;"))
        }
        assertFailsWith<IllegalArgumentException> {
            rewrite(mapping("Owner -> a:\n    int same -> x\n    int same -> y"),
                cls("La;", fields = listOf(field("La;", "x"), field("La;", "y"))))
        }
        assertFailsWith<IllegalArgumentException> {
            rewrite(mapping("Owner -> a:"), cls("La;", methods = listOf(
                method("La;", "native", flags = public or AccessFlags.NATIVE.value),
            )))
        }
    }

    @Test
    fun rewritesQualifiedClassNamesButNotBareTokens() {
        val mapping = ProguardMapping.classNames(mapOf(
            "Loldpkg/Widget;" to "Llibrary/Widget;", "La;" to "Llibrary/A;",
        ))
        val names = DexMappingRewriter(mapping, emptyList())
        assertEquals("library.Widget", names.rewriteClassName("oldpkg.Widget"))
        assertEquals("[Llibrary/A;", names.rewriteClassName("[La;"))
        assertEquals("a", names.rewriteClassName("a"))
    }

    @Test
    fun rejectsStaticInstanceMismatches() {
        val definitions = listOf(
            cls("La;", methods = listOf(method("La;", "b", flags = 0, body = listOf(returnVoid)))),
            cls("Le;", methods = listOf(method("Le;", "run", body = listOf(
                ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                    ImmutableMethodReference("La;", "b", emptyList(), "V")), returnVoid,
            )))),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            verifyBoostLibraryReferences(definitions, setOf("Le;"))
        }
        assertContains(error.message!!, "static/instance mismatch")
    }
}
