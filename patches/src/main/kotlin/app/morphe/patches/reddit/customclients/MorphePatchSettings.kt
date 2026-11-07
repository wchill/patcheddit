/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchBuilder
import app.morphe.patcher.patch.ResourcePatchBuilder
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.reddit.customclients.baconreader.misc.settings.baconReaderMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.infinity.misc.settings.infinityMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.joey.misc.settings.joeyMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.redditisfun.misc.settings.rifMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.relay.misc.settings.relayMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings.syncMorpheSettingsPatch as lemmySettingsPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.misc.settings.syncMorpheSettingsPatch as syncSettingsPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.inputStreamFromBundledResource
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.formatter.DexFormatter
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.logging.Logger

internal const val PATCH_SETTINGS_DESCRIPTOR = "Lapp/morphe/extension/shared/settings/PatchSettings;"
internal const val OAUTH_SETTINGS_DESCRIPTOR = "Lapp/morphe/extension/shared/settings/OAuthSettings;"

internal data class MorphePreferenceSpec(
    val key: String,
    val title: String,
    val summary: String,
    val category: String,
    val defaultEnabled: Boolean = true,
    val readOnly: Boolean = false,
)

/**
 * Registers a patch in its client's Morphe menu, including the menu dependency.
 * [optionsResource] is a bundled PreferenceCategory containing additional controls.
 */
fun PatchBuilder<*>.addMorpheSettings(
    extensionPatch: Patch<*>,
    title: String,
    description: String = "",
    category: String = PatchCategory.INTERFACE,
    preferenceKey: String? = null,
    defaultEnabled: Boolean = true,
    readOnly: Boolean = false,
    optionsResource: String? = null,
    resourceDirectory: String = "settings",
    optionsDependOnSwitch: Boolean = true,
): MorphePatchSetting {
    require(title.isNotBlank()) { "Only named patches can register Morphe settings." }
    val key = preferenceKey ?: "morphe_patch_${settingsKey(title)}"
    require(key.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid Morphe preference key: $key" }
    val menu = settingsPatchFor(extensionPatch)
    if (menu == null) {
        require(defaultEnabled || readOnly) { "An unregistered client cannot expose an opt-in Morphe setting." }
        Logger.getLogger("MorpheSettings").warning(
            "$title uses an unregistered extension. Menu integration is unavailable; preserving its legacy always-on behavior.",
        )
        return MorphePatchSetting(key, !readOnly, alwaysEnabled = true)
    }
    val setting = MorphePatchSetting(key, !readOnly)
    val spec = MorphePreferenceSpec(
        key, title, description, category,
        defaultEnabled, readOnly,
    )
    val resources = resourcePatch(default = false) {
        dependsOn(menu)
        optionsResource?.let {
            dependsOn(addResourcesPatch(resourceDirectory, ResourceGroup("xml", "$it.xml")))
        }
        execute {
            val templateName = if (readOnly) "morphe_patch_read_only" else "morphe_patch_switch"
            val input = checkNotNull(inputStreamFromBundledResource("settings", "xml/$templateName.xml")) {
                "Morphe preference template was not bundled: $templateName"
            }
            val packageName = document("AndroidManifest.xml").use {
                it.documentElement.getAttribute("package")
            }
            input.use { stream ->
                document(stream).use { template ->
                    document("res/xml/${morpheMenuResource(packageName)}.xml").use { target ->
                        if (optionsResource == null) {
                            addMorphePatchPreference(target, template, spec)
                        } else {
                            document("res/xml/$optionsResource.xml").use { options ->
                                if (resourceDirectory == "settings" && optionsResource == "morphe_reddit_api") {
                                    configureOAuthPreferences(options, packageName)
                                }
                                addMorphePatchPreference(target, template, spec, options, optionsDependOnSwitch)
                            }
                        }
                    }
                }
            }
        }
    }
    dependsOn(if (readOnly) resources else bytecodePatch(default = false) {
        dependsOn(extensionPatch, resources)
        execute {
            val owner = mutableClassDefBy(PATCH_SETTINGS_DESCRIPTOR)
            require(owner.methods.none { it.name == setting.getterName }) {
                "Morphe switch was already registered: $key"
            }
            owner.methods.add(ImmutableMethod(
                owner.type, setting.getterName, emptyList(), "Z",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(0, """
                    const-string v0, ${DexFormatter.INSTANCE.getQuotedString(key)}
                    const/4 v1, ${if (defaultEnabled) "0x1" else "0x0"}
                    invoke-static {v0, v1}, $PATCH_SETTINGS_DESCRIPTOR->isEnabled(Ljava/lang/String;Z)Z
                    move-result v0
                    return v0
                """)
            })
        }
    })
    return setting
}

/** A bytecode patch whose metadata also supplies its Morphe menu entry. */
fun morpheBytecodePatch(
    name: String,
    description: String? = null,
    default: Boolean = true,
    extensionPatch: Patch<*>,
    category: String? = null,
    preferenceKey: String? = null,
    defaultEnabled: Boolean = true,
    readOnly: Boolean = false,
    optionsResource: String? = null,
    resourceDirectory: String = "settings",
    settingsSummary: String? = null,
    block: BytecodePatchBuilder.(MorphePatchSetting) -> Unit,
) = bytecodePatch(name, description, default) {
    category?.let { category(it) }
    dependsOn(extensionPatch)
    val setting = addMorpheSettings(
        extensionPatch, name, (settingsSummary ?: description).orEmpty(), category ?: "Other patches",
        preferenceKey, defaultEnabled,
        readOnly, optionsResource, resourceDirectory,
    )
    block(setting)
}

/** Resource-only changes can register a read-only entry instead of a nonfunctional switch. */
fun morpheResourcePatch(
    name: String,
    description: String,
    default: Boolean = true,
    extensionPatch: Patch<*>,
    category: String = PatchCategory.INTERFACE,
    readOnly: Boolean = true,
    optionsResource: String? = null,
    resourceDirectory: String = "settings",
    block: ResourcePatchBuilder.(MorphePatchSetting) -> Unit,
) = resourcePatch(name, description, default) {
    category(category)
    dependsOn(extensionPatch)
    val setting = addMorpheSettings(
        extensionPatch, name, description, category, readOnly = readOnly,
        optionsResource = optionsResource, resourceDirectory = resourceDirectory,
    )
    block(setting)
}

private fun settingsPatchFor(extensionPatch: Patch<*>): Patch<*>? {
    val pending = ArrayDeque<Patch<*>>().apply { add(extensionPatch) }
    val visited = mutableSetOf<Patch<*>>()
    val menus = mutableSetOf<Patch<*>>()
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (!visited.add(current)) continue
        val menu = when (current) {
            ExtensionPatches.BaconReader -> baconReaderMorpheSettingsPatch
            ExtensionPatches.Boost -> boostMorpheSettingsPatch
            ExtensionPatches.Infinity -> infinityMorpheSettingsPatch
            ExtensionPatches.Joey -> joeyMorpheSettingsPatch
            ExtensionPatches.RIF -> rifMorpheSettingsPatch
            ExtensionPatches.Relay -> relayMorpheSettingsPatch
            ExtensionPatches.Sync -> syncSettingsPatch
            ExtensionPatches.SyncForLemmy -> lemmySettingsPatch
            else -> null
        }
        if (menu == null) pending.addAll(current.dependencies) else menus.add(menu)
    }
    require(menus.size <= 1) {
        "The extension resolves to multiple Morphe clients."
    }
    return menus.singleOrNull()
}

internal fun morpheMenuResource(packageName: String): String {
    require(packageName.isNotBlank()) { "The Morphe menu requires an app package." }
    return if (packageName == "com.rubenmayayo.reddit") "morphe_boost_settings_skeleton" else "morphe_settings"
}

private fun settingsKey(value: String) = value.lowercase(java.util.Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "_").trim('_')

internal fun addMorphePatchPreference(
    target: Document,
    template: Document,
    spec: MorphePreferenceSpec,
    options: Document? = null,
    optionsDependOnSwitch: Boolean = true,
) {
    require(target.documentElement.tagName == "PreferenceScreen") { "Unexpected Morphe menu root." }
    require(template.documentElement.tagName == "PreferenceCategory") { "Unexpected Morphe preference template." }
    val categoryKey = "morphe_category_${settingsKey(spec.category)}"
    require(spec.key != categoryKey) { "A Morphe preference cannot reuse its category's key." }
    val entries = mutableListOf(target.importNode(template.documentElement.childElements().single(), true) as Element)
    entries.first().apply {
        setAttribute("android:key", spec.key)
        setAttribute("android:title", spec.title)
        val note = if (spec.readOnly) {
            "Included in this APK. Repatch the app to change this setting."
        } else {
            "Restart the app after changing this switch."
        }
        setAttribute("android:summary", listOf(spec.summary, note).filter(String::isNotBlank).joinToString("\n"))
        if (!spec.readOnly) setAttribute("android:defaultValue", spec.defaultEnabled.toString())
    }
    options?.let {
        require(it.documentElement.tagName == "PreferenceCategory") { "Morphe options must be a PreferenceCategory." }
        entries += it.documentElement.childElements().map { entry ->
            (target.importNode(entry, true) as Element).apply {
                if (!spec.readOnly && optionsDependOnSwitch && getAttribute("android:selectable") != "false") {
                    setAttribute("android:dependency", spec.key)
                }
            }
        }
    }
    val elements = target.documentElement.getElementsByTagName("*")
    val keys = (0 until elements.length).mapNotNull {
        (elements.item(it) as Element).getAttribute("android:key").takeIf(String::isNotEmpty)
    }.toMutableSet()
    for (entry in entries) {
        require(entry.getAttribute("android:key").isNotBlank()) { "Morphe preferences must have keys." }
        val descendants = entry.getElementsByTagName("*")
        for (element in listOf(entry) + (0 until descendants.length).map { descendants.item(it) as Element }) {
            val key = element.getAttribute("android:key")
            if (key.isNotBlank()) require(keys.add(key)) { "Duplicate Morphe preference key: $key" }
        }
    }
    val categories = target.getElementsByTagName("PreferenceCategory")
    val matches = (0 until categories.length).map { categories.item(it) as Element }
        .filter { it.getAttribute("android:key") == categoryKey }
    require(matches.size <= 1) { "Duplicate Morphe category: $categoryKey" }
    val category = matches.singleOrNull()
        ?: (target.importNode(template.documentElement, false) as Element).apply {
            setAttribute("android:key", categoryKey)
            setAttribute("android:title", spec.category)
            target.documentElement.appendChild(this)
        }
    for (entry in entries) category.appendChild(entry)
}

internal fun ResourcePatchContext.addMorpheApkSettings() {
    val packageName = document("AndroidManifest.xml").use { manifest ->
        val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
            "AndroidManifest.xml has no application element."
        }
        if (application.getAttribute("android:debuggable") != "true") return
        manifest.documentElement.getAttribute("package")
    }
    val input = checkNotNull(inputStreamFromBundledResource("settings", "xml/morphe_patch_read_only.xml")) {
        "Morphe read-only preference template was not bundled."
    }
    input.use { stream ->
        document(stream).use { template ->
            document("res/xml/${morpheMenuResource(packageName)}.xml").use { target ->
                addMorphePatchPreference(target, template, MorphePreferenceSpec(
                    "morphe_patch_enable_android_debugging", "Enable Android debugging",
                    "Android developer debugging is enabled in this APK and can slow down the app.",
                    PatchCategory.ADVANCED, readOnly = true,
                ))
            }
        }
    }
}

internal fun configureOAuthPreferences(options: Document, packageName: String) {
    for (entry in options.documentElement.childElements()) {
        val key = entry.getAttribute("android:key")
        if (packageName == "com.rubenmayayo.reddit") {
            entry.setAttribute("android:key", when (key) {
                "morphe_client_id_pref_key" -> "pref_dev_reddit_client_id"
                "morphe_redirect_uri_pref_key" -> "pref_dev_reddit_redirect_url"
                "morphe_user_agent_pref_key" -> "pref_dev_reddit_user_agent"
                else -> error("Unknown Morphe OAuth option: $key")
            })
        } else if (packageName.startsWith("ml.docilealligator.infinityforreddit") &&
            key == "morphe_redirect_uri_pref_key"
        ) {
            options.renameNode(entry, null, "Preference")
            entry.removeAttribute("android:defaultValue")
            entry.setAttribute("android:selectable", "false")
            entry.setAttribute("android:persistent", "false")
        }
    }
}

/**
 * Runtime gates for a registered switch. Infrastructure and added data fields stay installed.
 */
class MorphePatchSetting internal constructor(
    val key: String,
    private val runtime: Boolean = true,
    private val alwaysEnabled: Boolean = false,
) {
    internal val getterName = "isEnabled_$key"
    val enabledCall: String get() {
        check(runtime) { "Read-only Morphe entries do not have runtime switches." }
        check(!alwaysEnabled) { "App-agnostic patches do not have a runtime settings getter." }
        return "$PATCH_SETTINGS_DESCRIPTOR->$getterName()Z"
    }

    fun checkEnabled(register: String, disabledLabel: String): String =
        if (alwaysEnabled) "" else """
            invoke-static {}, $enabledCall
            move-result $register
            if-eqz $register, :$disabledLabel
        """

    fun returnEnabled(method: MutableMethod) {
        require(method.returnType == "Z") { "A patch inclusion flag must return boolean." }
        if (alwaysEnabled) {
            method.returnEarly(true)
            return
        }
        method.addInstructions(0, """
            invoke-static {}, $enabledCall
            move-result v0
            return v0
        """)
    }

    /** A const-string's destination is dead at this point, including inside constructors. */
    fun replaceString(method: MutableMethod, index: Int, value: String) {
        val instruction = method.getInstruction(index)
        require(instruction.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) {
            "Morphe string switches require a const-string instruction."
        }
        val register = (instruction as OneRegisterInstruction).registerA
        val original = ((instruction as ReferenceInstruction).reference as StringReference).string
        if (alwaysEnabled) {
            method.replaceInstruction(index, "const-string v$register, ${DexFormatter.INSTANCE.getQuotedString(value)}")
            return
        }
        method.replaceInstruction(index, "nop")
        method.addInstructionsAtControlFlowLabel(index, """
            invoke-static {}, $enabledCall
            move-result v$register
            if-eqz v$register, :morphe_native_string
            const-string v$register, ${DexFormatter.INSTANCE.getQuotedString(value)}
            goto :morphe_string_done
            :morphe_native_string
            const-string v$register, ${DexFormatter.INSTANCE.getQuotedString(original)}
            :morphe_string_done
            nop
        """)
    }

    /** Gate an extension hook, returning its first argument unchanged for identity transforms. */
    context(patchContext: BytecodePatchContext)
    fun guardHook(type: String, name: String, identity: Boolean = false) {
        val owner = patchContext.mutableClassDefBy(type)
        guardHook(owner, name, identity)
    }

    fun guardHook(owner: MutableClass, name: String, identity: Boolean = false) {
        if (alwaysEnabled) return
        val targets = owner.methods.filter { it.name == name }
        require(targets.isNotEmpty()) { "Missing Morphe hook: ${owner.type}->$name" }
        for (target in targets) {
            val disabled = if (identity) {
                require(target.parameterTypes.firstOrNull()?.toString() == target.returnType) {
                    "Identity hook must return its first argument: $target"
                }
                returnInstruction(target.returnType, if (AccessFlags.STATIC.isSet(target.accessFlags)) "p0" else "p1")
            } else defaultReturn(target.returnType)
            gateMethod(owner, target, disabled)
        }
    }

    /** Preserve a native implementation before applying a reversible, method-local change. */
    context(patchContext: BytecodePatchContext)
    fun patchMethod(fingerprint: Fingerprint, block: MutableMethod.() -> Unit) {
        val owner = fingerprint.classDef
        val target = currentMethod(owner, fingerprint.method)
        patchMethod(owner, target, block)
    }

    fun patchMethod(owner: MutableClass, target: MutableMethod, block: MutableMethod.() -> Unit) {
        require(!target.name.startsWith('<') && target.implementation != null) {
            "Morphe switches require implemented methods other than constructors."
        }
        require(target.definingClass == owner.type && owner.methods.any { it === target }) {
            "The native method must belong to its mutable class."
        }
        if (alwaysEnabled) {
            target.block()
            return
        }
        val native = copyPrivateMethod(owner, ImmutableMethod.of(target), "morpheNative")
        target.block()
        gateMethod(owner, target, invokeAndReturn(native))
    }

    private fun gateMethod(owner: MutableClass, target: MutableMethod, disabled: String) {
        require(!target.name.startsWith('<') && target.implementation != null) { "Morphe hooks must be implemented methods." }
        val enabled = copyPrivateMethod(owner, target, "morpheEnabled")
        val wrapper = ImmutableMethod(
            target.definingClass, target.name, target.parameters, target.returnType,
            target.accessFlags, target.annotations, target.hiddenApiRestrictions,
            MutableMethodImplementation(parameterWords(target) + 2),
        ).toMutable().apply {
            addInstructions(0, """
                ${checkEnabled("v0", "morphe_disabled")}
                ${invokeAndReturn(enabled)}
                :morphe_disabled
                $disabled
            """)
        }
        check(owner.methods.remove(target))
        check(owner.methods.add(wrapper))
    }
}

internal fun currentMethod(owner: MutableClass, signature: Method): MutableMethod = owner.methods.single {
    it.name == signature.name && it.parameterTypes == signature.parameterTypes && it.returnType == signature.returnType
}

internal fun parameterWords(method: Method): Int = method.parameterTypes.fold(
    if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1,
) { words, type -> words + if (type == "J" || type == "D") 2 else 1 }

internal fun copyPrivateMethod(owner: MutableClass, method: Method, prefix: String): MutableMethod {
    var suffix = 0
    var name: String
    do {
        name = "${prefix}_${method.name}_${suffix++}"
    } while (owner.methods.any { it.name == name })
    return ImmutableMethod(
        method.definingClass, name, method.parameters, method.returnType,
        AccessFlags.PRIVATE.value or (if (AccessFlags.STATIC.isSet(method.accessFlags)) AccessFlags.STATIC.value else 0),
        null, null, method.implementation,
    ).toMutable().also { owner.methods.add(it) }
}

private fun invokeAndReturn(method: Method): String {
    val words = parameterWords(method)
    val arguments = if (words == 0) "{}" else "{p0 .. p${words - 1}}"
    val invoke = if (AccessFlags.STATIC.isSet(method.accessFlags)) "invoke-static" else "invoke-direct"
    val range = if (words == 0) "" else "/range"
    val result = resultKind(method.returnType)
    return """
        $invoke$range $arguments, ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}
        ${result?.let { "move-result$it v0" }.orEmpty()}
        ${returnInstruction(method.returnType, "v0")}
    """
}

internal fun resultKind(type: String): String? = when (type.first()) {
    'V' -> null
    'L', '[' -> "-object"
    'J', 'D' -> "-wide"
    else -> ""
}

internal fun bridgeNativeOAuthGetter(
    owner: MutableClass,
    target: MutableMethod,
    configuredGetter: String,
): MutableMethod {
    require(AccessFlags.NATIVE.isSet(target.accessFlags) && AccessFlags.STATIC.isSet(target.accessFlags) &&
        target.parameterTypes.isEmpty() && target.returnType == "Ljava/lang/String;"
    ) { "Native OAuth bridges require a static, no-argument string getter." }
    var suffix = 0
    var name: String
    do {
        name = "morpheOAuth_${target.name}_${suffix++}"
    } while (owner.methods.any { it.name == name })
    return ImmutableMethod(
        owner.type, name, emptyList(), target.returnType,
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        null, null, MutableMethodImplementation(1),
    ).toMutable().apply {
        addInstructions(0, """
            ${spoofClientSetting.checkEnabled("v0", "morphe_native_oauth")}
            invoke-static {}, $OAUTH_SETTINGS_DESCRIPTOR->$configuredGetter()Ljava/lang/String;
            move-result-object v0
            return-object v0
            :morphe_native_oauth
            invoke-static {}, $target
            move-result-object v0
            return-object v0
        """)
        owner.methods.add(this)
    }
}

private fun returnInstruction(type: String, register: String): String =
    resultKind(type)?.let { "return$it $register" } ?: "return-void"

private fun defaultReturn(type: String): String = when (type.first()) {
    'V' -> "return-void"
    'J', 'D' -> "const-wide/16 v0, 0x0\nreturn-wide v0"
    else -> "const/4 v0, 0x0\n${returnInstruction(type, "v0")}"
}

context(patchContext: BytecodePatchContext)
internal fun Fingerprint.wrapMorpheHook(
    before: String = "",
    after: String = "",
    setting: MorphePatchSetting? = null,
) {
    wrapMorpheHook(classDef, method, before, after, setting)
}

internal fun wrapMorpheHook(
    owner: MutableClass,
    signature: Method,
    before: String = "",
    after: String = "",
    setting: MorphePatchSetting? = null,
) {
    val target = currentMethod(owner, signature)
    require(!target.name.startsWith('<') && target.implementation != null) {
        "Morphe hooks require implemented methods other than constructors."
    }
    val native = copyPrivateMethod(owner, target, "morpheOriginal")
    val words = parameterWords(target)
    val arguments = if (words == 0) "{}" else "{p0 .. p${words - 1}}"
    val invoke = if (AccessFlags.STATIC.isSet(target.accessFlags)) "invoke-static" else "invoke-direct"
    val range = if (words == 0) "" else "/range"
    val result = resultKind(target.returnType)
    val wrapper = ImmutableMethod(
        target.definingClass, target.name, target.parameters, target.returnType,
        target.accessFlags, target.annotations, target.hiddenApiRestrictions,
        MutableMethodImplementation(words + if (setting == null) 3 else 4),
    ).toMutable().apply {
        addInstructions(0, """
            ${if (before.isNotBlank()) setting?.checkEnabled("v3", "morphe_skip_before").orEmpty() else ""}
            $before
            :morphe_skip_before
            $invoke$range $arguments, ${native.definingClass}->${native.name}(${target.parameterTypes.joinToString("")})${target.returnType}
            ${result?.let { "move-result$it v0" }.orEmpty()}
            ${if (after.isNotBlank()) setting?.checkEnabled("v3", "morphe_skip_after").orEmpty() else ""}
            $after
            :morphe_skip_after
            ${returnInstruction(target.returnType, "v0")}
        """)
    }
    check(owner.methods.remove(target))
    check(owner.methods.add(wrapper))
}

context(patchContext: BytecodePatchContext)
internal fun Fingerprint.overrideOAuthReturn(getter: String): MutableMethod {
    require(method.returnType == "Ljava/lang/String;") { "OAuth getters must return a string." }
    val configuredGetter = when (getter) {
        "getClientId" -> "getConfiguredClientId"
        "getRedirectUri" -> "getConfiguredRedirectUri"
        "getUserAgent" -> "getConfiguredUserAgent"
        "getBasicAuthorization" -> "getConfiguredBasicAuthorization"
        else -> error("Unknown OAuth getter: $getter")
    }
    val owner = classDef
    val target = currentMethod(owner, method)
    if (AccessFlags.NATIVE.isSet(target.accessFlags)) {
        return bridgeNativeOAuthGetter(owner, target, configuredGetter)
    }
    wrapMorpheHook(
        setting = spoofClientSetting,
        before = """
            invoke-static {}, $OAUTH_SETTINGS_DESCRIPTOR->$configuredGetter()Ljava/lang/String;
            move-result-object v0
            return-object v0
        """,
    )
    return currentMethod(owner, target)
}

internal fun Match.overrideOAuthStrings(getter: String) {
    for (match in stringMatches.sortedByDescending { it.index }) {
        method.overrideOAuthValue(match.index, getter)
    }
}

internal fun MutableMethod.overrideOAuthValue(index: Int, getter: String) {
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    addInstructions(index + 1, """
        invoke-static/range {v$register .. v$register}, $OAUTH_SETTINGS_DESCRIPTOR->$getter(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v$register
    """)
}

private fun Element.childElements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
