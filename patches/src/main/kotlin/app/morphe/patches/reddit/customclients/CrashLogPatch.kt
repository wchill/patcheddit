/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.all.misc.crashlog.crashLogPatch as fatalCrashLogPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.w3c.dom.Element
import org.w3c.dom.Document

internal const val EXCEPTION_HOOK_CLASS_DESCRIPTOR = "Lapp/morphe/extension/shared/debug/ExceptionHook;"
private const val THROWABLE = "Ljava/lang/Throwable;"
private const val CRASHLYTICS = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;"

internal fun crashlyticsExceptionFingerprint() = Fingerprint(
    definingClass = CRASHLYTICS,
    custom = { method, _ -> isCrashlyticsExceptionHandler(method) },
)

internal fun isCrashlyticsExceptionHandler(method: Method): Boolean =
    method.definingClass == CRASHLYTICS &&
            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
            !method.name.startsWith('<') &&
            method.parameterTypes.map(CharSequence::toString) == listOf(THROWABLE) &&
            method.returnType == "V" && method.implementation != null

fun crashLogPatch(
    extensionPatch: Patch<*>,
    compatible: Array<Compatibility>,
    mainActivity: Fingerprint? = null,
    exceptionHandler: Fingerprint? = null,
    exceptionHookDescriptor: String = EXCEPTION_HOOK_CLASS_DESCRIPTOR,
    settingsPatch: Patch<*>? = null,
    description: String = "Logs reported errors and fatal crashes, viewable in Morphe settings.",
) = morpheBytecodePatch(
    name = "Log exceptions",
    description = description,
    default = true,
    extensionPatch = extensionPatch,
    category = PatchCategory.ADVANCED,
) { setting ->
    compatibleWith(*compatible)
    dependsOn(extensionPatch)
    settingsPatch?.let { dependsOn(it) }

    lateinit var applicationType: String
    lateinit var mainActivityType: String
    lateinit var crashActivityType: String
    dependsOn(fatalCrashLogPatch(mainActivity ?: Fingerprint(
        name = "onCreate",
        parameters = listOf("Landroid/os/Bundle;"),
        returnType = "V",
        custom = { _, classDef -> classDef.type == crashActivityType },
    )))
    dependsOn(resourcePatch(default = false) {
        execute {
            document("AndroidManifest.xml").use { manifest ->
                val application = checkNotNull(
                    manifest.getElementsByTagName("application").item(0) as? Element,
                ) { "AndroidManifest.xml has no application element." }
                applicationType = applicationDescriptor(
                    manifest.documentElement.getAttribute("package"),
                    androidAttribute(application, "name"),
                )
                if (mainActivity == null) mainActivityType = launcherActivityType(manifest)
            }
        }
    })

    execute {
        setting.guardHook("Lapp/morphe/extension/shared/patches/CrashLogPatch;", "setUncaughtExceptionHandler")
        val classes = buildList { classDefForEach(::add) }.associateBy { it.type }
        val throwableTypes = mutableMapOf<String, Boolean>()
        for (classDef in classes.values) {
            if (classDef.type.startsWith("Lapp/morphe/extension/")) continue
            for (method in classDef.methods) {
                val recordCrashlytics = exceptionHandler == null && isCrashlyticsExceptionHandler(method)
                val replacements = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                    rewriteCrashLogInvocation(instruction, exceptionHandler == null) { type ->
                        throwableTypes.getOrPut(type) { isThrowableType(type, classes) }
                    }?.let { index to it }
                }.orEmpty()
                if (replacements.isEmpty() && !recordCrashlytics) continue
                val target = mutableClassDefBy(classDef).methods.single {
                    it.name == method.name &&
                            it.parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString) &&
                            it.returnType == method.returnType
                }
                for ((index, instruction) in replacements) target.replaceInstruction(index, instruction)
                if (recordCrashlytics) hookExceptionMethod(target)
            }
        }
        onCreateMethod(mutableClassDefBy(applicationType)).addInstructions(
            0,
            "invoke-static/range {p0 .. p0}, $EXCEPTION_HOOK_CLASS_DESCRIPTOR->initialize(Landroid/app/Application;)V",
        )
        if (mainActivity == null) {
            crashActivityType = activityOnCreateOwner(mainActivityType, classes)
            onCreateMethod(mutableClassDefBy(crashActivityType), listOf("Landroid/os/Bundle;"))
        }
        exceptionHandler?.method?.let { method ->
            hookExceptionMethod(method, exceptionHookDescriptor)
        }
    }
}

internal fun applicationDescriptor(packageName: String, name: String): String {
    require(packageName.isNotBlank() && name.isNotBlank()) { "The manifest class name must not be empty." }
    val qualified = when {
        name.startsWith('.') -> packageName + name
        '.' !in name -> "$packageName.$name"
        else -> name
    }
    return "L${qualified.replace('.', '/')};"
}

private fun androidAttribute(element: Element, name: String): String =
    element.getAttributeNS("http://schemas.android.com/apk/res/android", name)
        .ifEmpty { element.getAttribute("android:$name") }

internal fun launcherActivityType(manifest: Document): String {
    val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
        "AndroidManifest.xml has no application element."
    }
    val children = application.childNodes
    for (index in 0 until children.length) {
        val component = children.item(index) as? Element ?: continue
        if (component.tagName != "activity" && component.tagName != "activity-alias") continue
        val filters = component.getElementsByTagName("intent-filter")
        for (filterIndex in 0 until filters.length) {
            val filter = filters.item(filterIndex) as Element
            val actions = filter.getElementsByTagName("action")
            val categories = filter.getElementsByTagName("category")
            val main = (0 until actions.length).any {
                androidAttribute(actions.item(it) as Element, "name") == "android.intent.action.MAIN"
            }
            val launcher = (0 until categories.length).any {
                androidAttribute(categories.item(it) as Element, "name") == "android.intent.category.LAUNCHER"
            }
            if (main && launcher) {
                return applicationDescriptor(
                    manifest.documentElement.getAttribute("package"),
                    androidAttribute(component, if (component.tagName == "activity-alias") "targetActivity" else "name"),
                )
            }
        }
    }
    error("AndroidManifest.xml has no launcher activity.")
}

internal fun activityOnCreateOwner(type: String, classes: Map<String, ClassDef>): String {
    var current: String? = type
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        val definition = classes[current] ?: break
        if (definition.methods.any {
            it.name == "onCreate" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/os/Bundle;") &&
                    it.returnType == "V"
        }) return current
        current = definition.superclass
    }
    return type
}

internal fun onCreateMethod(classDef: MutableClass, parameters: List<String> = emptyList()): MutableMethod =
    classDef.methods.singleOrNull {
        it.name == "onCreate" && it.parameterTypes.map(CharSequence::toString) == parameters && it.returnType == "V"
    }?.also {
        require(!AccessFlags.STATIC.isSet(it.accessFlags) && it.implementation != null) {
            "onCreate must be an implemented instance method."
        }
    } ?: ImmutableMethod(
        classDef.type, "onCreate", parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
        "V", AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(parameters.size + 1),
    ).toMutable().also { method ->
        val parent = checkNotNull(classDef.superclass) { "Lifecycle class has no superclass." }
        method.addInstructions(
            0,
            """
                invoke-super/range {p0 .. p${parameters.size}}, $parent->onCreate(${parameters.joinToString("")})V
                return-void
            """,
        )
        classDef.methods.add(method)
    }

internal fun exceptionParameterRegister(method: Method): Int {
    val parameters = method.parameterTypes.map(CharSequence::toString)
    require(parameters.count { it == THROWABLE } == 1) { "Exception handler must have one Throwable parameter." }
    return parameters.take(parameters.indexOf(THROWABLE)).fold(
        if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1,
    ) { words, type -> words + if (type == "J" || type == "D") 2 else 1 }
}

private fun hookExceptionMethod(method: MutableMethod, descriptor: String = EXCEPTION_HOOK_CLASS_DESCRIPTOR) {
    val parameter = exceptionParameterRegister(method)
    method.addInstructions(
        0,
        "invoke-static/range {p$parameter .. p$parameter}, $descriptor->handleException($THROWABLE)V",
    )
}

private fun isThrowableType(type: String, classes: Map<String, ClassDef>): Boolean {
    var current: String? = type
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        if (current == THROWABLE) return true
        val definition = classes[current]
        if (definition != null) {
            current = definition.superclass
        } else {
            if (!current.startsWith("Ljava/")) return false
            return try {
                Throwable::class.java.isAssignableFrom(Class.forName(
                    current.removePrefix("L").removeSuffix(";").replace('/', '.'),
                    false, Throwable::class.java.classLoader,
                ))
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }
    return false
}

internal fun rewriteCrashLogInvocation(
    instruction: Instruction,
    recordNonFatal: Boolean,
    isThrowable: (String) -> Boolean,
): com.android.tools.smali.dexlib2.builder.BuilderInstruction? {
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return null
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    val static = instruction.opcode == Opcode.INVOKE_STATIC || instruction.opcode == Opcode.INVOKE_STATIC_RANGE
    val virtual = instruction.opcode == Opcode.INVOKE_VIRTUAL || instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE
    val targetParameters = when {
        recordNonFatal && static && reference.definingClass == "Landroid/util/Log;"
                && reference.name in setOf("e", "w", "wtf") && reference.returnType == "I"
                && (parameters == listOf("Ljava/lang/String;", "Ljava/lang/String;", THROWABLE)
                || reference.name != "e" && parameters == listOf("Ljava/lang/String;", THROWABLE)) -> parameters
        recordNonFatal && virtual && reference.name == "printStackTrace"
                && parameters.isEmpty() && reference.returnType == "V"
                && isThrowable(reference.definingClass) -> listOf(THROWABLE)
        else -> return null
    }
    val target = ImmutableMethodReference(
        EXCEPTION_HOOK_CLASS_DESCRIPTOR, reference.name, targetParameters, reference.returnType,
    )
    return when (instruction) {
        is Instruction35c -> BuilderInstruction35c(
            Opcode.INVOKE_STATIC, instruction.registerCount,
            instruction.registerC, instruction.registerD, instruction.registerE,
            instruction.registerF, instruction.registerG, target,
        )
        is Instruction3rc -> BuilderInstruction3rc(
            Opcode.INVOKE_STATIC_RANGE, instruction.startRegister, instruction.registerCount, target,
        )
        else -> null
    }
}
