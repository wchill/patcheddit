/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.misc.extension.mapping

import java.io.Reader

internal data class MethodId(
    val owner: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
)

internal data class FieldId(val owner: String, val name: String, val type: String)

internal class ProguardMapping private constructor(
    val classes: Map<String, String>,
    val methods: Map<MethodId, String>,
    val fields: Map<FieldId, String>,
    val methodOwners: Map<MethodId, String> = emptyMap(),
    val fieldOwners: Map<FieldId, String> = emptyMap(),
    private val referenceAliases: Boolean = false,
) {
    fun type(type: String): String {
        val dimensions = type.takeWhile { it == '[' }.length
        val element = type.substring(dimensions)
        return "[".repeat(dimensions) + (classes[element] ?: element)
    }

    fun withNestedClasses(types: Collection<String>): ProguardMapping {
        val names = LinkedHashMap(classes)
        for (type in types.sortedBy(String::length)) {
            if (names[type]?.let { it != type } == true) continue
            var separator = type.lastIndexOf('$')
            while (separator >= 0) {
                val parent = type.substring(0, separator) + ";"
                val target = names[parent]
                if (target != null && target != parent) {
                    names[type] = target.removeSuffix(";") + type.substring(separator)
                    break
                }
                separator = type.lastIndexOf('$', separator - 1)
            }
        }
        require(referenceAliases || names.values.toSet().size == names.size) {
            "Nested class relocation creates a class collision"
        }
        return ProguardMapping(names, methods, fields, methodOwners, fieldOwners, referenceAliases)
    }

    fun reversed(): ProguardMapping {
        require(!referenceAliases && methodOwners.isEmpty() && fieldOwners.isEmpty()) {
            "Reference-only adapter bindings cannot be reversed"
        }
        val reverseClasses = classes.entries.associate { (source, target) -> target to source }
        val reverseMethods = methods.entries.associate { (source, target) ->
            MethodId(
                type(source.owner),
                target,
                source.parameters.map(::type),
                type(source.returnType),
            ) to source.name
        }
        val reverseFields = fields.entries.associate { (source, target) ->
            FieldId(type(source.owner), target, type(source.type)) to source.name
        }
        check(reverseMethods.size == methods.size && reverseFields.size == fields.size) {
            "Mapping has conflicting reverse member names"
        }
        return ProguardMapping(reverseClasses, reverseMethods, reverseFields)
    }

    fun mapTargets(transform: (String) -> String): ProguardMapping = ProguardMapping(
        classes.mapValues { transform(it.value) },
        methods,
        fields,
        methodOwners.mapValues { transform(it.value) },
        fieldOwners.mapValues { transform(it.value) },
        referenceAliases,
    )

    fun withBindings(
        types: Map<String, String>,
        boundMethods: Map<MethodId, MethodId>,
        boundFields: Map<FieldId, FieldId>,
    ): ProguardMapping {
        val names = LinkedHashMap(classes)
        for ((source, target) in types) {
            val previous = names.put(source, target)
            require(previous == null || previous == target) { "Conflicting adapter type binding for $source" }
        }
        fun type(value: String): String {
            val element = value.trimStart('[')
            return value.take(value.length - element.length) + (names[element] ?: element)
        }
        val newMethods = LinkedHashMap(methods)
        val newMethodOwners = LinkedHashMap(methodOwners)
        for ((source, target) in boundMethods) {
            require(source.parameters.map(::type) == target.parameters && type(source.returnType) == target.returnType) {
                "Adapter method changes its prototype: $source -> $target"
            }
            val previous = newMethods.put(source, target.name)
            require(previous == null || previous == target.name) { "Conflicting adapter method binding for $source" }
            newMethodOwners[source] = target.owner
        }
        val newFields = LinkedHashMap(fields)
        val newFieldOwners = LinkedHashMap(fieldOwners)
        for ((source, target) in boundFields) {
            require(type(source.type) == target.type) { "Adapter field changes its type: $source -> $target" }
            val previous = newFields.put(source, target.name)
            require(previous == null || previous == target.name) { "Conflicting adapter field binding for $source" }
            newFieldOwners[source] = target.owner
        }
        return ProguardMapping(names, newMethods, newFields, newMethodOwners, newFieldOwners, true)
    }

    companion object {
        fun classNames(names: Map<String, String>): ProguardMapping {
            require(names.values.toSet().size == names.size) { "Conflicting class relocation targets" }
            return ProguardMapping(names, emptyMap(), emptyMap())
        }

        private val classLine = Regex("""(\S+)\s+->\s+(\S+):""")
        private val memberLine = Regex("""(.+?)\s+->\s+(\S+)""")
        private val methodLine =
            Regex("""(?:\d+:\d+:)?(\S+)\s+([^\s(]+)\(([^)]*)\)(?::\d+(?::\d+)?)?""")
        private val fieldLine = Regex("""(\S+)\s+(\S+)""")
        private val primitiveTypes = mapOf(
            "void" to "V", "boolean" to "Z", "byte" to "B", "char" to "C",
            "short" to "S", "int" to "I", "long" to "J", "float" to "F", "double" to "D",
        )

        private data class Member(
            val observedOwner: String,
            val declaration: String,
            val observedName: String,
            val line: Int,
        )

        fun read(reader: Reader): ProguardMapping {
            val originalToObserved = linkedMapOf<String, String>()
            val observedToOriginal = linkedMapOf<String, String>()
            val members = mutableListOf<Member>()
            var owner: Pair<String, String>? = null
            reader.buffered().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val number = index + 1
                    if (line.isBlank() || line.trimStart().startsWith('#')) return@forEachIndexed
                    if (!line.first().isWhitespace()) {
                        val match = classLine.matchEntire(line.trim())
                            ?: error("Invalid class mapping at line $number")
                        val original = javaType(match.groupValues[1])
                        val observed = javaType(match.groupValues[2])
                        require(original.startsWith('L') && observed.startsWith('L')) {
                            "Class mapping must use reference types at line $number"
                        }
                        require(originalToObserved.putIfAbsent(original, observed) == null) {
                            "Duplicate original class mapping at line $number: $original"
                        }
                        require(observedToOriginal.putIfAbsent(observed, original) == null) {
                            "Merged class mappings are ambiguous at line $number: $observed"
                        }
                        owner = original to observed
                    } else {
                        val current = checkNotNull(owner) { "Member without a class at line $number" }
                        val match = memberLine.matchEntire(line.trim())
                            ?: error("Invalid member mapping at line $number")
                        members += Member(
                            current.second, match.groupValues[1],
                            match.groupValues[2], number,
                        )
                    }
                }
            }
            require(observedToOriginal.isNotEmpty()) { "Mapping file contains no classes" }
            fun observedType(type: String): String {
                val dimensions = type.takeWhile { it == '[' }.length
                val element = type.substring(dimensions)
                return "[".repeat(dimensions) + (originalToObserved[element] ?: element)
            }
            val methods = linkedMapOf<MethodId, String>()
            val fields = linkedMapOf<FieldId, String>()
            for (member in members) {
                val method = methodLine.matchEntire(member.declaration)
                if (method != null) {
                    val originalName = method.groupValues[2]
                    require('.' !in originalName) {
                        "Inlined or qualified method mappings are unsupported at line ${member.line}"
                    }
                    if (originalName.startsWith("<") || member.observedName.startsWith("<")) {
                        require(originalName == member.observedName &&
                            originalName in setOf("<init>", "<clinit>") && method.groupValues[1] == "void"
                        ) { "Invalid constructor mapping at line ${member.line}" }
                    }
                    val parameters = method.groupValues[3].takeIf { it.isNotBlank() }
                        ?.split(',')?.map { observedType(javaType(it.trim())) } ?: emptyList()
                    require(parameters.none { it == "V" }) {
                        "Void method parameter at line ${member.line}"
                    }
                    val key = MethodId(
                        member.observedOwner, member.observedName, parameters,
                        observedType(javaType(method.groupValues[1])),
                    )
                    val previous = methods.putIfAbsent(key, originalName)
                    require(previous == null || previous == originalName) {
                        "Ambiguous/inlined method mapping at line ${member.line}: $key"
                    }
                } else {
                    val field = fieldLine.matchEntire(member.declaration)
                        ?: error("Invalid member declaration at line ${member.line}")
                    val type = observedType(javaType(field.groupValues[1]))
                    require(type != "V") { "Void field at line ${member.line}" }
                    val key = FieldId(member.observedOwner, member.observedName, type)
                    val previous = fields.putIfAbsent(key, field.groupValues[2])
                    require(previous == null || previous == field.groupValues[2]) {
                        "Conflicting field mapping at line ${member.line}: $key"
                    }
                }
            }
            return ProguardMapping(observedToOriginal, methods, fields)
        }

        private fun javaType(value: String): String {
            var name = value
            var dimensions = 0
            while (name.endsWith("[]")) {
                name = name.dropLast(2)
                dimensions++
            }
            require(name.isNotBlank() &&
                name.none { it.isWhitespace() || it in "[]();<>/\\:#" } &&
                name.split('.').none(String::isEmpty)
            ) {
                "Invalid Java type in mapping: $value"
            }
            val type = primitiveTypes[name] ?: "L${name.replace('.', '/')};"
            require(type != "V" || dimensions == 0) { "Invalid void array in mapping: $value" }
            return "[".repeat(dimensions) + type
        }
    }
}
