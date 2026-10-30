/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension.mapping

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.AnnotationElement
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference
import com.android.tools.smali.dexlib2.iface.reference.MethodHandleReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.iface.value.EncodedValue
import com.android.tools.smali.dexlib2.iface.value.MethodHandleEncodedValue
import com.android.tools.smali.dexlib2.iface.value.MethodTypeEncodedValue
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotationElement
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableCallSiteReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodHandleReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodProtoReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.value.ImmutableArrayEncodedValue
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue
import com.android.tools.smali.dexlib2.immutable.value.ImmutableMethodHandleEncodedValue
import com.android.tools.smali.dexlib2.immutable.value.ImmutableMethodTypeEncodedValue
import com.android.tools.smali.dexlib2.immutable.value.ImmutableAnnotationEncodedValue
import com.android.tools.smali.dexlib2.rewriter.DexRewriter
import com.android.tools.smali.dexlib2.rewriter.InstructionRewriter
import com.android.tools.smali.dexlib2.rewriter.EncodedValueRewriter
import com.android.tools.smali.dexlib2.rewriter.Rewriter
import com.android.tools.smali.dexlib2.rewriter.RewriterModule
import com.android.tools.smali.dexlib2.rewriter.Rewriters

internal class DexMappingRewriter(
    sourceMapping: ProguardMapping,
    definitions: Collection<ClassDef>,
    private val resolveReflectiveFields: Boolean = true,
) {
    private val mapping = sourceMapping.withNestedClasses(definitions.map { it.type })
    private val classes = definitions.associateBy { it.type }
    private val methods = classes.values.associate { cls ->
        cls.type to cls.methods.associateBy { it.id() }
    }
    private val fields = classes.values.associate { cls ->
        cls.type to cls.fields.associateBy { it.id() }
    }
    private val methodNames = mutableMapOf<MethodId, String>()
    private val fieldNames = mutableMapOf<FieldId, String>()
    private val families = mutableMapOf<MethodId, MethodId>()
    private val familyNames = mutableMapOf<MethodId, String>()
    private val publicClasses = mutableSetOf<String>()
    private val publicMethods = mutableSetOf<MethodId>()
    private val publicFields = mutableSetOf<FieldId>()
    private val reflectiveFields = mapping.fields.entries.associate { (field, name) ->
        "@boost-field:${field.owner}->${field.name}:${field.type}" to name
    }

    private fun MethodReference.id() =
        MethodId(definingClass, name, parameterTypes.map(CharSequence::toString), returnType)

    private fun FieldReference.id() = FieldId(definingClass, name, type)

    private fun parents(owner: String): List<String> =
        classes[owner]?.let { listOfNotNull(it.superclass) + it.interfaces } ?: emptyList()

    private fun ancestors(owner: String): List<String> {
        val pending = ArrayDeque(listOf(owner))
        val seen = linkedSetOf<String>()
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (seen.add(type)) pending.addAll(parents(type))
        }
        return seen.toList()
    }

    private fun packageName(type: String) = type.substringBeforeLast('/', "")

    private fun visible(flags: Int, owner: String, caller: String, relocated: Boolean = false): Boolean {
        if (AccessFlags.PUBLIC.isSet(flags)) return true
        if (AccessFlags.PRIVATE.isSet(flags)) return owner == caller
        val ownerType = if (relocated) mapping.type(owner) else owner
        val callerType = if (relocated) mapping.type(caller) else caller
        return packageName(ownerType) == packageName(callerType) ||
            AccessFlags.PROTECTED.isSet(flags) && owner in ancestors(caller)
    }

    private fun Method.virtual() = !name.startsWith("<") &&
        !AccessFlags.STATIC.isSet(accessFlags) && !AccessFlags.PRIVATE.isSet(accessFlags)

    private fun family(key: MethodId): MethodId {
        val parent = families[key] ?: return key
        if (parent == key) return key
        return family(parent).also { families[key] = it }
    }

    private fun inheritedMethods(key: MethodId): List<Method> {
        val result = mutableListOf<Method>()
        for (parent in ancestors(key.owner).drop(1)) {
            val method = methods[parent]?.get(key.copy(owner = parent)) ?: continue
            if (visible(method.accessFlags, parent, key.owner)) result += method
        }
        return result
    }

    private fun resolveField(key: FieldId, seen: MutableSet<String> = mutableSetOf()): Field? {
        if (!seen.add(key.owner)) return null
        fields[key.owner]?.get(key)?.let { return it }
        val definition = classes[key.owner] ?: return null
        for (parent in definition.interfaces + listOfNotNull(definition.superclass)) {
            resolveField(key.copy(owner = parent), seen)?.let { return it }
        }
        return null
    }

    init {
        // A mapped override also renames its unmapped base declaration and sibling overrides.
        for (cls in classes.values) {
            val slots = mutableMapOf<MethodId, MethodId>()
            for (owner in ancestors(cls.type)) {
                for (method in methods[owner].orEmpty().values) {
                    if (!method.virtual() || !visible(method.accessFlags, owner, cls.type)) continue
                    val key = method.id()
                    families.putIfAbsent(key, key)
                    val other = slots.putIfAbsent(key.copy(owner = ""), key) ?: continue
                    families[family(key)] = family(other)
                }
            }
        }
        val targets = families.keys.groupBy(::family)
        val conflicts = mutableListOf<String>()
        for ((root, members) in targets) {
            val names = members.mapNotNull { key ->
                mapping.methods[key]?.takeIf { it != key.name }
            }.distinct()
            if (names.size > 1) conflicts += "${names.joinToString()} for ${members.joinToString()}"
            names.singleOrNull()?.let { familyNames[root] = it }
        }
        require(conflicts.isEmpty()) { "Conflicting virtual method mappings:\n${conflicts.joinToString("\n")}" }
        for (cls in classes.values) {
            cls.forEachReference { reference ->
                val owner = when (reference) {
                    is TypeReference -> reference.type.trimStart('[')
                    is MethodReference -> reference.definingClass
                    is FieldReference -> reference.definingClass
                    else -> return@forEachReference
                }
                val definition = classes[owner] ?: return@forEachReference
                if (!AccessFlags.PUBLIC.isSet(definition.accessFlags) &&
                    visible(definition.accessFlags, owner, cls.type) &&
                    !visible(definition.accessFlags, owner, cls.type, relocated = true)
                ) publicClasses += owner
                when (reference) {
                    is FieldReference -> resolveField(reference.id())?.let { field ->
                        if (visible(field.accessFlags, field.definingClass, cls.type) &&
                            !visible(field.accessFlags, field.definingClass, cls.type, relocated = true)
                        ) publicFields += field.id()
                    }
                    is MethodReference -> {
                        val key = reference.id()
                        val method = methods[owner]?.get(key) ?: inheritedMethods(key).firstOrNull()
                        if (method != null && visible(method.accessFlags, method.definingClass, cls.type) &&
                            !visible(method.accessFlags, method.definingClass, cls.type, relocated = true)
                        ) publicMethods += method.id()
                    }
                }
            }
        }
        for (cls in classes.values) {
            val slots = mutableMapOf<MethodId, MethodId>()
            for (owner in ancestors(cls.type)) {
                for (method in methods[owner].orEmpty().values) {
                    val key = method.id()
                    val flags = access(method.accessFlags, key in publicMethods)
                    if (!method.virtual() || !visible(flags, owner, cls.type, relocated = true)) continue
                    val slot = MethodId(
                        "", methodName(key), key.parameters.map(mapping::type), mapping.type(key.returnType),
                    )
                    val other = slots.putIfAbsent(slot, key)
                    require(other == null || family(other) == family(key)) {
                        "Extension mapping joins distinct virtual methods in ${cls.type}: $key and $other"
                    }
                }
            }
        }
    }

    private fun access(flags: Int, widen: Boolean) = if (widen) {
        (flags and (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv()) or AccessFlags.PUBLIC.value
    } else {
        flags
    }

    private fun methodName(key: MethodId): String = methodNames.getOrPut(key) {
        val declared = methods[key.owner]?.get(key)
        if (declared != null) {
            familyNames[family(key)] ?: mapping.methods[key] ?: key.name
        } else if (key.name.startsWith("<")) {
            key.name
        } else {
            val names = inheritedMethods(key).map {
                familyNames[family(it.id())] ?: mapping.methods[it.id()] ?: it.name
            }.distinct()
            require(names.size <= 1) { "Ambiguous inherited method mapping for $key: $names" }
            names.singleOrNull() ?: mapping.methods[key] ?: key.name
        }
    }

    private fun fieldName(key: FieldId): String = fieldNames.getOrPut(key) {
        val declared = resolveField(key)?.id() ?: key
        mapping.fields[declared] ?: declared.name
    }

    private fun methodOwner(key: MethodId): String {
        mapping.methodOwners[key]?.let { return it }
        if (key.owner in mapping.classes && methods[key.owner]?.containsKey(key) != true) {
            val owners = inheritedMethods(key).mapNotNull { mapping.methodOwners[it.id()] }.distinct()
            require(owners.size <= 1) { "Ambiguous inherited adapter method owner for $key: $owners" }
            owners.singleOrNull()?.let { return it }
        }
        return mapping.type(key.owner)
    }

    private fun fieldOwner(key: FieldId): String {
        mapping.fieldOwners[key]?.let { return it }
        if (key.owner in mapping.classes) {
            resolveField(key)?.id()?.let { mapping.fieldOwners[it] }?.let { return it }
        }
        return mapping.type(key.owner)
    }

    fun rewriteClassName(value: String): String {
        if (resolveReflectiveFields && value.startsWith("@boost-field:")) {
            return checkNotNull(reflectiveFields[value]) { "Missing reflective Boost adapter field: $value" }
        }
        mapping.classes[value]?.let { return it }
        if (value.startsWith("[")) return mapping.type(value)
        // A bare obfuscated token can also be a preference key, field name or ordinary text.
        if ('.' !in value) return value
        val descriptor = "L${value.replace('.', '/')};"
        return mapping.classes[descriptor]?.removePrefix("L")?.removeSuffix(";")
            ?.replace('/', '.') ?: value
    }

    private fun signature(value: String): String = object {
        var position = 0

        fun token(delimiters: String): String {
            val start = position
            while (position < value.length && value[position] !in delimiters) position++
            require(position > start && position < value.length) { "Invalid generic signature: $value" }
            return value.substring(start, position)
        }

        fun arguments(): String {
            position++
            val result = StringBuilder("<")
            while (position < value.length && value[position] != '>') result.append(type())
            require(position < value.length) { "Unclosed generic arguments: $value" }
            position++
            return result.append('>').toString()
        }

        fun classType(): String {
            var observed = "L${token("<.;")};"
            var target = mapping.type(observed)
            val result = StringBuilder(target.removeSuffix(";"))
            if (value[position] == '<') result.append(arguments())
            while (position < value.length && value[position] == '.') {
                position++
                observed = observed.removeSuffix(";") + "$" + token("<.;") + ";"
                val nested = mapping.type(observed)
                val prefix = target.removeSuffix(";") + "$"
                require(nested.startsWith(prefix)) {
                    "Incompatible enclosing/inner class mapping in generic signature: $value"
                }
                result.append('.').append(nested.removePrefix(prefix).removeSuffix(";"))
                target = nested
                if (value[position] == '<') result.append(arguments())
            }
            require(position < value.length && value[position] == ';') { "Invalid class signature: $value" }
            position++
            return result.append(';').toString()
        }

        fun type(): String {
            require(position < value.length) { "Incomplete generic signature: $value" }
            return when (val kind = value[position++]) {
                'L' -> classType()
                'T' -> "T${token(";")};".also { position++ }
                '[', '+', '-', '^' -> "$kind${type()}"
                '*', 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 'V', '(', ')' -> kind.toString()
                else -> error("Invalid generic signature token $kind in $value")
            }
        }

        fun rewrite(): String {
            val result = StringBuilder()
            if (value.startsWith('<')) {
                position++
                result.append('<')
                while (position < value.length && value[position] != '>') {
                    result.append(token(":"))
                    while (position < value.length && value[position] == ':') {
                        position++
                        result.append(':')
                        if (position < value.length && value[position] !in ":>") result.append(type())
                    }
                }
                require(position < value.length) { "Unclosed type parameters: $value" }
                position++
                result.append('>')
            }
            while (position < value.length) result.append(type())
            return result.toString()
        }
    }.rewrite()

    private val rewriter = DexRewriter(object : RewriterModule() {
        private fun elements(
            rewriters: Rewriters,
            type: String,
            elements: Collection<AnnotationElement>,
        ): List<ImmutableAnnotationElement> {
            val methods = classes[type]?.methods?.toList().orEmpty()
                .filter { it.parameterTypes.isEmpty() }.associateBy { it.name }
            return elements.map { element ->
                val name = methods[element.name]?.let { methodName(it.id()) } ?: element.name
                val value = if (type == "Ldalvik/annotation/Signature;" && element.value is ArrayEncodedValue) {
                    val fragments = (element.value as ArrayEncodedValue).value
                    require(fragments.all { it is StringEncodedValue }) { "Invalid generic signature annotation" }
                    val original = fragments.joinToString("") { (it as StringEncodedValue).value }
                    ImmutableArrayEncodedValue(listOf(ImmutableStringEncodedValue(signature(original))))
                } else {
                    rewriters.encodedValueRewriter.rewrite(element.value)
                }
                ImmutableAnnotationElement(name, value)
            }
        }

        private fun handle(rewriters: Rewriters, value: MethodHandleReference): ImmutableMethodHandleReference {
            val member = when (val reference = value.memberReference) {
                is MethodReference -> rewriters.methodReferenceRewriter.rewrite(reference)
                is FieldReference -> rewriters.fieldReferenceRewriter.rewrite(reference)
                else -> error("Unsupported method-handle member: ${value.memberReference}")
            }
            return ImmutableMethodHandleReference(value.methodHandleType, member)
        }

        override fun getTypeRewriter(rewriters: Rewriters): Rewriter<String> =
            Rewriter(mapping::type)

        override fun getFieldReferenceRewriter(rewriters: Rewriters): Rewriter<FieldReference> =
            Rewriter { field ->
                ImmutableFieldReference(
                    fieldOwner(field.id()), fieldName(field.id()), mapping.type(field.type),
                )
            }

        override fun getMethodReferenceRewriter(rewriters: Rewriters): Rewriter<MethodReference> =
            Rewriter { method ->
                ImmutableMethodReference(
                    methodOwner(method.id()), methodName(method.id()),
                    method.parameterTypes.map { mapping.type(it.toString()) }, mapping.type(method.returnType),
                )
            }

        override fun getFieldRewriter(rewriters: Rewriters): Rewriter<Field> =
            Rewriter { field ->
                ImmutableField(
                    mapping.type(field.definingClass), fieldName(field.id()), mapping.type(field.type),
                    access(field.accessFlags, field.id() in publicFields),
                    field.initialValue?.let { rewriters.encodedValueRewriter.rewrite(it) },
                    field.annotations.map { rewriters.annotationRewriter.rewrite(it) },
                    field.hiddenApiRestrictions,
                )
            }

        override fun getMethodRewriter(rewriters: Rewriters): Rewriter<Method> =
            Rewriter { method ->
                ImmutableMethod(
                    mapping.type(method.definingClass), methodName(method.id()),
                    method.parameters.map { ImmutableMethodParameter.of(rewriters.methodParameterRewriter.rewrite(it)) },
                    mapping.type(method.returnType), access(method.accessFlags, method.id() in publicMethods),
                    method.annotations.map { ImmutableAnnotation.of(rewriters.annotationRewriter.rewrite(it)) }.toSet(),
                    method.hiddenApiRestrictions,
                    method.implementation?.let {
                        ImmutableMethodImplementation.of(rewriters.methodImplementationRewriter.rewrite(it))
                    },
                )
            }

        override fun getInstructionRewriter(rewriters: Rewriters): Rewriter<Instruction> =
            object : InstructionRewriter(rewriters) {
                override fun rewrite(instruction: Instruction): Instruction {
                    if (instruction.opcode == Opcode.CONST_STRING ||
                        instruction.opcode == Opcode.CONST_STRING_JUMBO
                    ) {
                        val string = ((instruction as ReferenceInstruction).reference as StringReference).string
                        val replacement = rewriteClassName(string)
                        if (replacement != string) {
                            val register = (instruction as OneRegisterInstruction).registerA
                            val reference = ImmutableStringReference(replacement)
                            return if (instruction.opcode == Opcode.CONST_STRING) {
                                ImmutableInstruction21c(instruction.opcode, register, reference)
                            } else {
                                ImmutableInstruction31c(instruction.opcode, register, reference)
                            }
                        }
                    }
                    return super.rewrite(instruction)
                }
            }

        override fun getEncodedValueRewriter(rewriters: Rewriters): Rewriter<EncodedValue> =
            object : EncodedValueRewriter(rewriters) {
                override fun rewrite(value: EncodedValue): EncodedValue = when (value) {
                    is MethodHandleEncodedValue -> ImmutableMethodHandleEncodedValue(handle(rewriters, value.value))
                    is MethodTypeEncodedValue -> ImmutableMethodTypeEncodedValue(
                        ImmutableMethodProtoReference(
                            value.value.parameterTypes.map { mapping.type(it.toString()) },
                            mapping.type(value.value.returnType),
                        ),
                    )
                    is AnnotationEncodedValue -> ImmutableAnnotationEncodedValue(
                        mapping.type(value.type), elements(rewriters, value.type, value.elements),
                    )
                    is StringEncodedValue -> ImmutableStringEncodedValue(rewriteClassName(value.value))
                    else -> super.rewrite(value)
                }
            }

        override fun getCallSiteReferenceRewriter(rewriters: Rewriters): Rewriter<CallSiteReference> {
            val original = super.getCallSiteReferenceRewriter(rewriters)
            return Rewriter { callSite ->
                val rewritten = original.rewrite(callSite)
                val sam = callSite.extraArguments.firstOrNull() as? MethodTypeEncodedValue
                val name = if (sam != null &&
                    (callSite.methodHandle.memberReference as? MethodReference)?.definingClass ==
                    "Ljava/lang/invoke/LambdaMetafactory;"
                ) {
                    methodName(
                        MethodId(
                            callSite.methodProto.returnType, callSite.methodName,
                            sam.value.parameterTypes.map(CharSequence::toString), sam.value.returnType,
                        ),
                    )
                } else {
                    callSite.methodName
                }
                ImmutableCallSiteReference(
                    rewritten.name, rewritten.methodHandle, name, rewritten.methodProto,
                    rewritten.extraArguments.asIterable(),
                )
            }
        }

        override fun getAnnotationRewriter(rewriters: Rewriters): Rewriter<Annotation> =
            Rewriter { annotation ->
                ImmutableAnnotation(
                    annotation.visibility, mapping.type(annotation.type),
                    elements(rewriters, annotation.type, annotation.elements),
                )
            }
    })

    fun rewrite(definitions: Collection<ClassDef>): List<ImmutableClassDef> {
        val targets = definitions.groupBy { mapping.type(it.type) }
        val duplicate = targets.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Extension mapping class collision at ${duplicate?.key}: " +
                duplicate?.value?.joinToString { it.type }
        }
        return definitions.map { original ->
            val rewritten = rewriter.classDefRewriter.rewrite(original)
            val newMethods = rewritten.methods.toList()
            val newFields = rewritten.fields.toList()
            require(original.methods.none {
                AccessFlags.NATIVE.isSet(it.accessFlags) &&
                    (original.type != rewritten.type || methodName(it.id()) != it.name)
            }) { "Cannot rename JNI-bound class or method in ${original.type}" }
            require(newMethods.map { it.id() }.distinct().size == newMethods.size) {
                "Extension mapping creates duplicate methods in ${rewritten.type}"
            }
            require(newFields.map { it.id() }.distinct().size == newFields.size) {
                "Extension mapping creates duplicate fields in ${rewritten.type}"
            }
            val annotations = rewritten.annotations.map { annotation ->
                val elements = annotation.elements.map { element ->
                    val value = when {
                        annotation.type == "Ldalvik/annotation/InnerClass;" &&
                            element.name == "name" && element.value is StringEncodedValue &&
                            rewritten.type != original.type -> {
                            ImmutableStringEncodedValue(
                                rewritten.type.removeSuffix(";").substringAfterLast('/').substringAfterLast('$'),
                            )
                        }
                        else -> element.value
                    }
                    ImmutableAnnotationElement(element.name, value)
                }
                ImmutableAnnotation(annotation.visibility, annotation.type, elements)
            }
            ImmutableClassDef(
                rewritten.type, access(rewritten.accessFlags, original.type in publicClasses), rewritten.superclass,
                rewritten.interfaces, rewritten.sourceFile, annotations, newFields, newMethods,
            )
        }
    }
}
