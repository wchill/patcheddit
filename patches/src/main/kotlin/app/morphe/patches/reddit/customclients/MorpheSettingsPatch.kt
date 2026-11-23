/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.decode
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.inputStreamFromBundledResource
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val MORPHE_SETTINGS_ACTIVITY = "app.morphe.extension.shared.settings.MorpheSettingsActivity"
internal const val MORPHE_SETTINGS_HOOK = "Lapp/morphe/extension/shared/settings/MorpheSettings;"
internal const val MORPHE_SETTINGS_ENTRY = "morphe_settings_entry"
private const val MORPHE_FRAMEWORK_OVERLAY = "MorpheSettingsFrameworkOverlay"
internal const val MORPHE_SETTINGS_DESCRIPTION =
    "Adds a Morphe settings menu for included patches, with exception and fatal crash log viewers."

internal val morpheSettingsContextPatch = patch(default = false) {
    lateinit var applicationType: String
    lateinit var packageName: String
    dependsOn(patch(default = false) {
        execute {
            document("AndroidManifest.xml").use { manifest ->
                val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
                    "AndroidManifest.xml has no application element."
                }
                packageName = manifest.documentElement.getAttribute("package")
                applicationType = applicationDescriptor(packageName, application.getAttribute("android:name"))
            }
        }
    })
    finalize {
        installMatureContentEnforcement(packageName)
        onCreateMethod(mutableClassDefBy(applicationType)).addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $PATCH_SETTINGS_DESCRIPTOR->initialize(Landroid/app/Application;)V",
        )
    }
}

internal val morpheSettingsIconNames = listOf(
    "palette", "feed", "explore", "link", "comment", "media", "restore", "key", "security",
    "settings", "code", "bug", "info", "download", "error", "forum", "group", "description", "chevron",
)

internal val morpheSettingsResourcesPatch = patch(default = false) {
    dependsOn(addResourcesPatch(
        "settings",
        ResourceGroup("xml", "morphe_settings.xml", "morphe_settings_entry.xml",
            "morphe_exception_log.xml", "morphe_fatal_crash_logs.xml"),
        ResourceGroup("layout", "morphe_settings_entry.xml", "morphe_log_text.xml"),
        ResourceGroup("drawable", "morphe_settings_icon.xml",
            *morpheSettingsIconNames.map { "patcheddit_icon_$it.xml" }.toTypedArray()),
        ResourceGroup("raw", "patcheddit_licenses.json"),
    ))

    execute {
        for ((file, type) in listOf("morphe_diagnostics_strings.xml" to "string",
            "morphe_settings_style.xml" to "style")) {
            val input = checkNotNull(inputStreamFromBundledResource("settings", "values/$file")) {
                "Morphe resource was not bundled: $file"
            }
            input.use {
                document(it).use { source ->
                    document("res/values/${type}s.xml").use { target ->
                        mergeMorpheResources(target, source, type)
                    }
                }
            }
        }
        document("AndroidManifest.xml").use(::registerMorpheSettingsActivity)
    }

    finalize {
        addMorpheApkSettings()
    }
}

internal fun mergeMorpheResources(target: Document, source: Document, type: String) {
    require(target.documentElement.tagName == "resources" && source.documentElement.tagName == "resources") {
        "Unexpected Morphe resource root."
    }
    val entries = source.documentElement.childElements()
    val existing = target.getElementsByTagName(type)
    val names = (0 until existing.length).map { (existing.item(it) as Element).getAttribute("name") }.toMutableSet()
    for (entry in entries) {
        require(entry.tagName == type && names.add(entry.getAttribute("name"))) {
            "Duplicate or invalid Morphe $type: ${entry.getAttribute("name")}"
        }
    }
    for (entry in entries) target.documentElement.appendChild(target.importNode(entry, true))
}

internal fun registerMorpheSettingsActivity(manifest: Document) {
    val application = checkNotNull(manifest.getElementsByTagName("application").item(0) as? Element) {
        "AndroidManifest.xml has no application element."
    }
    require(application.childElements().none {
        it.tagName == "activity" && it.getAttribute("android:name") == MORPHE_SETTINGS_ACTIVITY
    }) { "The app already declares Morphe settings." }
    application.appendChild(manifest.createElement("activity").apply {
        setAttribute("android:name", MORPHE_SETTINGS_ACTIVITY)
        setAttribute("android:label", "@string/morphe_settings_title")
        setAttribute("android:exported", "false")
    })
}

internal fun morphePreferencesPatch(
    name: String,
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    resourceName: String,
    nativeStyle: Boolean = false,
    nativeSettingsBackground: String? = null,
) = patch(
    name = "$name Morphe settings",
    description = MORPHE_SETTINGS_DESCRIPTION,
    default = true,
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*compatible)
    dependsOn(extensionPatch, morpheSettingsResourcesPatch, morpheSettingsContextPatch)

    execute {
        if (nativeSettingsBackground != null) {
            document("res/values/styles.xml").use { styles ->
                val overlay = styles.documentElement.childElements()
                    .single { it.tagName == "style" && it.getAttribute("name") == MORPHE_FRAMEWORK_OVERLAY }
                for (attribute in listOf("android:windowBackground", "colorSurface")) {
                    overlay.appendChild(styles.createElement("item").apply {
                        setAttribute("name", attribute)
                        textContent = nativeSettingsBackground
                    })
                }
            }
        }
        val packageName = document("AndroidManifest.xml").use {
            it.documentElement.getAttribute("package").also { name ->
                require(name.isNotBlank()) { "AndroidManifest.xml has no package name." }
            }
        }
        document("res/xml/morphe_settings_entry.xml").use { source ->
            for (path in settingsResourcePaths("xml", resourceName)) {
                document(path).use { addMorphePreference(it, source, packageName, nativeStyle) }
            }
        }
    }
}

internal fun morpheListSettingsPatch(
    name: String,
    extensionPatch: Patch,
    compatible: Array<Compatibility>,
    menu: Fingerprint,
) = patch(
    name = "$name Morphe settings",
    description = MORPHE_SETTINGS_DESCRIPTION,
    default = true,
) {
    category(PatchCategory.INTERFACE)
    compatibleWith(*compatible)
    dependsOn(extensionPatch, morpheSettingsResourcesPatch, morpheSettingsContextPatch)

    execute {
        addMorpheListEntry(menu.method)
    }
}

internal fun settingsResourcePaths(type: String, name: String): List<String> {
    return decode("res/$type*/$name.xml")
        .also { require(it.isNotEmpty()) { "The app has no $type/$name settings resource." } }
}

internal fun addMorphePreference(
    target: Document,
    source: Document,
    packageName: String,
    nativeStyle: Boolean = false,
) {
    val root = target.documentElement
    require(root.tagName.substringAfterLast('.') == "PreferenceScreen") {
        "Unexpected native settings root: ${root.tagName}"
    }
    require(packageName.isNotBlank()) { "The settings intent requires an app package." }
    val descendants = root.getElementsByTagName("*")
    require((0 until descendants.length).none {
        val element = descendants.item(it) as Element
        element.getAttribute("android:key") == MORPHE_SETTINGS_ENTRY ||
                element.getAttribute("app:key") == MORPHE_SETTINGS_ENTRY
    }) { "The native settings already contain a Morphe entry." }

    val template = if (nativeStyle) {
        checkNotNull(root.childElements().firstOrNull {
            it.tagName.contains("Preference") && !it.tagName.endsWith("PreferenceCategory")
        }) { "The native settings have no preference style to reuse." }
    } else null
    val entry = target.createElement(template?.tagName ?: source.documentElement.tagName)
    template?.let {
        for (attribute in listOf("style", "android:layout", "android:widgetLayout")) {
            if (it.hasAttribute(attribute)) entry.setAttribute(attribute, it.getAttribute(attribute))
        }
        entry.setAttribute("app:top", "true")
        entry.setAttribute("app:bottom", "true")
    }
    val attributes = source.documentElement.attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        if (!attribute.nodeName.startsWith("xmlns")) {
            entry.setAttribute(attribute.nodeName, attribute.nodeValue)
        }
    }
    val intent = source.documentElement.childElements().single { it.tagName == "intent" }
    entry.appendChild(target.importNode(intent, true).also {
        (it as Element).setAttribute("android:targetPackage", packageName)
    })
    val firstCategory = root.childElements().firstOrNull { it.tagName.endsWith("PreferenceCategory") }
    root.insertBefore(entry, firstCategory ?: root.firstChild)
}

internal fun addMorpheListEntry(method: MutableMethod) {
    val instructions = checkNotNull(method.implementation) { "The settings menu has no implementation." }.instructions
    val adapterCalls = instructions.mapIndexedNotNull { index, instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        if (reference?.definingClass != "Landroid/widget/ListView;" || reference.name != "setAdapter" ||
            reference.parameterTypes.map(CharSequence::toString) != listOf("Landroid/widget/ListAdapter;") ||
            instruction.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)) return@mapIndexedNotNull null
        val register = when (instruction) {
            is Instruction35c -> instruction.registerC
            is Instruction3rc -> instruction.startRegister
            else -> error("Unsupported native settings adapter invocation.")
        }
        index to register
    }
    require(adapterCalls.size == 1) { "Expected one native settings list adapter, found ${adapterCalls.size}." }
    val (index, register) = adapterCalls.single()
    method.addInstruction(index,
        "invoke-static/range {v$register .. v$register}, $MORPHE_SETTINGS_HOOK->addListEntry(Landroid/widget/ListView;)V")
}

internal fun addMorpheLayoutEntry(document: Document) {
    val elements = document.getElementsByTagName("*")
    val scroll = (0 until elements.length).map { elements.item(it) as Element }
        .single { it.tagName.endsWith("ScrollView") }
    val container = scroll.childElements().single()
    require(container.tagName.endsWith("LinearLayout") &&
            container.getAttribute("android:orientation") == "vertical") {
        "Unexpected native settings scroll container."
    }
    require((0 until elements.length).none {
        (elements.item(it) as Element).getAttribute("android:id") in
                setOf("@id/$MORPHE_SETTINGS_ENTRY", "@+id/$MORPHE_SETTINGS_ENTRY")
    }) { "The native settings layout already contains a Morphe entry." }
    val card = container.childElements().first { it.tagName.endsWith("CardView") }
    val content = card.childElements().single()
    val row = content.childElements().first {
        it.tagName.endsWith("LinearLayout") && it.hasAttribute("android:id")
    }
    val icon = row.childElements().single { it.tagName.endsWith("ImageView") }
    val label = row.childElements().single { it.tagName.endsWith("TextView") }
    val newCard = card.cloneNode(false) as Element
    val newContent = content.cloneNode(false) as Element
    newCard.removeAttribute("android:id")
    newContent.removeAttribute("android:id")
    val newRow = row.cloneNode(false) as Element
    newRow.setAttribute("android:id", "@id/$MORPHE_SETTINGS_ENTRY")
    newRow.appendChild(icon.cloneNode(false).also {
        (it as Element).removeAttribute("android:id")
        val source = listOf("app:srcCompat", "android:src").firstOrNull(it::hasAttribute) ?: "android:src"
        it.setAttribute(source, "@drawable/morphe_settings_icon")
    })
    newRow.appendChild(label.cloneNode(false).also {
        (it as Element).removeAttribute("android:id")
        it.setAttribute("android:text", "@string/morphe_settings_title")
    })
    newContent.appendChild(newRow)
    newCard.appendChild(newContent)
    container.appendChild(newCard)
}

private fun Element.childElements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
