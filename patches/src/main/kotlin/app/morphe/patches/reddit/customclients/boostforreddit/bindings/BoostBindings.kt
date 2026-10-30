/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.bindings

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.misc.extension.mapping.FieldId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.MethodId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

internal class BoostMethodBinding internal constructor(
    val source: MethodId,
    val target: MethodId,
) {
    val type get() = target.owner
    val name get() = target.name
    val parameters get() = target.parameters
    val returnType get() = target.returnType

    fun matches(reference: MethodReference): Boolean =
        reference.definingClass == type && reference.name == name &&
            reference.parameterTypes.map(CharSequence::toString) == parameters &&
            reference.returnType == returnType

    fun fingerprint(
        strings: List<String>? = null,
        accessFlags: List<AccessFlags>? = null,
    ) = Fingerprint(
        definingClass = type, name = name, parameters = parameters, returnType = returnType,
        strings = strings, accessFlags = accessFlags,
    )

    override fun toString() = "$type->$name(${parameters.joinToString("")})$returnType"
}

internal class BoostFieldBinding internal constructor(
    val source: FieldId,
    val target: FieldId,
) {
    val type get() = target.owner
    val name get() = target.name
    val fieldType get() = target.type

    fun matches(reference: FieldReference): Boolean =
        reference.definingClass == type && reference.name == name && reference.type == fieldType

    override fun toString() = "$type->$name:$fieldType"
}

internal open class BoostTypeBinding(private val source: String) {
    val type: String get() = BoostBindings.table.classes.getValue(source)
    val className: String get() = type.removePrefix("L").removeSuffix(";").replace('/', '.')

    protected fun method(signature: String): BoostMethodBinding =
        BoostBindings.table.method("$source->$signature")

    protected fun field(signature: String): BoostFieldBinding =
        BoostBindings.table.field("$source->$signature")
}

internal object BoostBindings {
    val table: BoostBindingTable by lazy {
        val input = checkNotNull(javaClass.classLoader.getResourceAsStream("boost/bindings.json")) {
            "Bundled Boost adapter bindings were not found"
        }
        input.reader().use(BoostBindingTable::read)
    }
}

internal class BoostBindingTable private constructor(
    val version: String,
    val classes: Map<String, String>,
    val methods: Map<MethodId, MethodId>,
    val fields: Map<FieldId, FieldId>,
    private val staticMethods: Set<MethodId>,
    private val staticFields: Set<FieldId>,
    private val injectedMethods: Map<MethodId, MethodId>,
) {
    fun method(descriptor: String): BoostMethodBinding {
        val source = methodId(descriptor)
        return BoostMethodBinding(source, methods.getValue(source))
    }

    fun field(descriptor: String): BoostFieldBinding {
        val source = fieldId(descriptor)
        return BoostFieldBinding(source, fields.getValue(source))
    }

    fun mapping(libraries: ProguardMapping): ProguardMapping =
        libraries.withBindings(classes, methods, fields)

    fun sourceDefinitions(native: Collection<ClassDef>, libraries: ProguardMapping): List<ClassDef> {
        val nativeClasses = native.associateBy { it.type }
        val nativeMethods = native.flatMap { it.methods }.associateBy { it.id() }
        val nativeFields = native.flatMap { it.fields }.associateBy { FieldId(it.definingClass, it.name, it.type) }
        val mapping = mapping(libraries)
        val sourceTypes = libraries.classes.entries.associate { (source, target) -> target to source } +
            classes.entries.associate { (source, target) -> target to source }
        fun sourceType(type: String): String = sourceTypes[type] ?: type
        fun nativeMethod(source: MethodId): Method {
            val target = methods.getValue(source)
            val original = injectedMethods[source]?.let { methods.getValue(it) } ?: target
            return checkNotNull(nativeMethods[original]) { "Missing Boost $version adapter method: $original" }
        }
        fun receiverSupports(source: String, target: String): Boolean {
            val pending = ArrayDeque(listOf(mapping.type(source)))
            val seen = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val owner = pending.removeFirst()
                if (!seen.add(owner)) continue
                if (owner == target) return true
                nativeClasses[owner]?.let {
                    it.superclass?.let(pending::add)
                    pending.addAll(it.interfaces)
                }
            }
            return false
        }
        for ((source, target) in classes) {
            require(target in nativeClasses) { "Missing Boost $version adapter class: $source -> $target" }
        }
        for ((source, target) in methods) {
            val declaration = nativeMethod(source)
            require(AccessFlags.STATIC.isSet(declaration.accessFlags) == (source in staticMethods)) {
                "Boost $version adapter method changed static/instance dispatch: $source -> $target"
            }
            require(source in staticMethods || receiverSupports(source.owner, target.owner)) {
                "Boost adapter method has an incompatible receiver: $source -> $target"
            }
            if (source.name == "<init>") {
                require(target.name == "<init>" && mapping.type(source.owner) == target.owner) {
                    "Boost adapter constructor changes its owner: $source -> $target"
                }
            }
            if (source in injectedMethods) {
                require(target !in nativeMethods) { "Injected Boost adapter method already exists: $target" }
                require(source in staticMethods && target.parameters == declaration.parameterTypes.map(CharSequence::toString) &&
                    target.returnType == declaration.returnType && target.owner == declaration.definingClass
                ) { "Injected Boost adapter bridge changes its source prototype: $source" }
            }
        }
        for ((source, target) in fields) {
            val declaration = checkNotNull(nativeFields[target]) {
                "Missing Boost $version adapter field: $source -> $target"
            }
            require(AccessFlags.STATIC.isSet(declaration.accessFlags) == (source in staticFields)) {
                "Boost $version adapter field changed static/instance access: $source -> $target"
            }
            require(source in staticFields || receiverSupports(source.owner, target.owner)) {
                "Boost adapter field has an incompatible receiver: $source -> $target"
            }
        }
        return classes.map { (source, target) ->
            val cls = nativeClasses.getValue(target)
            ImmutableClassDef(
                source, cls.accessFlags, cls.superclass?.let(::sourceType), cls.interfaces.map(::sourceType),
                null, emptyList(),
                fields.filterKeys { it.owner == source }.map { (key, value) ->
                    ImmutableField(
                        source, key.name, key.type, nativeFields.getValue(value).accessFlags,
                        null, emptyList(), emptySet(),
                    )
                },
                methods.keys.filter { it.owner == source }.map { key ->
                    ImmutableMethod(
                        source, key.name, key.parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
                        key.returnType, nativeMethod(key).accessFlags, emptySet(), emptySet(), null,
                    )
                },
            )
        }
    }

    companion object {
        private val methodPattern = Regex("""(L[^;\s]+;)->([^\s(:]+)\(([^)]*)\)(.+)""")
        private val fieldPattern = Regex("""(L[^;\s]+;)->([^\s(:]+):(.+)""")
        private val typePattern = Regex("""\[*(?:[ZBSCIJFD]|L[^;\s\[\]().:]+;)""")

        private fun parameters(value: String): List<String> {
            val types = typePattern.findAll(value).map { it.value }.toList()
            require(types.joinToString("") == value) { "Invalid Boost adapter parameter list: $value" }
            return types
        }

        private fun type(value: String, void: Boolean = false): String {
            require(void && value == "V" || typePattern.matches(value)) { "Invalid Boost adapter type: $value" }
            return value
        }

        private fun methodId(value: String): MethodId {
            val match = checkNotNull(methodPattern.matchEntire(value)) { "Invalid Boost adapter method: $value" }
            return MethodId(
                type(match.groupValues[1]), match.groupValues[2], parameters(match.groupValues[3]),
                type(match.groupValues[4], void = true),
            ).also {
                require(!it.name.startsWith("<") || it.name == "<init>" && it.returnType == "V") {
                    "Invalid Boost adapter constructor: $value"
                }
            }
        }

        private fun fieldId(value: String): FieldId {
            val match = checkNotNull(fieldPattern.matchEntire(value)) { "Invalid Boost adapter field: $value" }
            return FieldId(type(match.groupValues[1]), match.groupValues[2], type(match.groupValues[3]))
        }

        private fun Method.id() =
            MethodId(definingClass, name, parameterTypes.map(CharSequence::toString), returnType)

        fun read(reader: Reader): BoostBindingTable {
            val input = JsonReader(reader)
            fun string(): String {
                require(input.peek() == JsonToken.STRING) { "Expected a Boost adapter string at ${input.path}" }
                return input.nextString()
            }
            fun strings(): Map<String, String> {
                val result = linkedMapOf<String, String>()
                input.beginObject()
                while (input.hasNext()) {
                    val name = input.nextName()
                    require(name !in result) { "Duplicate Boost adapter key at ${input.path}: $name" }
                    result[name] = string()
                }
                input.endObject()
                return result
            }
            fun list(): List<String> {
                val result = mutableListOf<String>()
                input.beginArray()
                while (input.hasNext()) result += string()
                input.endArray()
                require(result.size == result.toSet().size) { "Duplicate Boost adapter dispatch entries" }
                return result
            }
            val sections = mutableSetOf<String>()
            val objects = mutableMapOf<String, Map<String, String>>()
            val lists = mutableMapOf<String, List<String>>()
            var version: String? = null
            input.beginObject()
            while (input.hasNext()) {
                val name = input.nextName()
                require(sections.add(name)) { "Duplicate Boost adapter section: $name" }
                when (name) {
                    "version" -> version = string()
                    "classes", "methods", "fields", "injectedMethods" -> objects[name] = strings()
                    "staticMethods", "staticFields" -> lists[name] = list()
                    else -> error("Unknown Boost adapter section: $name")
                }
            }
            input.endObject()
            require(input.peek() == JsonToken.END_DOCUMENT) { "Trailing data after Boost adapter bindings" }
            require(sections == setOf("version", "classes", "methods", "fields", "staticMethods", "staticFields", "injectedMethods")) {
                "Invalid Boost adapter binding sections"
            }
            val classes = objects.getValue("classes")
            require(classes.isNotEmpty()) { "Boost adapter bindings contain no classes" }
            for ((source, target) in classes) {
                require(type(source).startsWith("L") && type(target).startsWith("L")) {
                    "Boost adapter class must be a reference type: $source -> $target"
                }
            }
            val methods = objects.getValue("methods").map { (source, target) ->
                methodId(source) to methodId(target)
            }.toMap()
            val fields = objects.getValue("fields").map { (source, target) ->
                fieldId(source) to fieldId(target)
            }.toMap()
            val staticMethods = lists.getValue("staticMethods").map(::methodId).toSet()
            val staticFields = lists.getValue("staticFields").map(::fieldId).toSet()
            val injected = objects.getValue("injectedMethods").map { (source, original) ->
                methodId(source) to methodId(original)
            }.toMap()
            require((methods.keys.map { it.owner } + fields.keys.map { it.owner }).all(classes::containsKey)) {
                "Boost adapter member has no class binding"
            }
            require(methods.keys.containsAll(staticMethods) && fields.keys.containsAll(staticFields)) {
                "Boost adapter dispatch metadata references an unknown member"
            }
            require(methods.keys.containsAll(injected.keys + injected.values)) {
                "Boost adapter injection references an unknown member"
            }
            require(injected.values.none { it in injected }) {
                "Boost adapter bridges must clone native methods, not other bridges"
            }
            val declaredVersion = checkNotNull(version)
            require(declaredVersion.isNotBlank()) { "Boost adapter version is empty" }
            return BoostBindingTable(declaredVersion, classes, methods, fields, staticMethods, staticFields, injected)
        }
    }
}
