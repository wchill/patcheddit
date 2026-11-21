/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.addBoostSettings
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.groupBoostLinkSettings
import app.morphe.patches.reddit.customclients.boostforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings.syncMorpheSettingsPatch as lemmySettings
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MorphePatchSettingsTest {
    @Test
    fun registersOnlySelectedControlsAndReusesCategoriesWithoutReplacingDiagnostics() {
        val menu = resource("settings/xml/morphe_settings.xml")
        val diagnostics = menu.documentElement.childElements().single()
        for (key in listOf("morphe_patch_comments", "morphe_patch_more_comments")) {
            addMorphePatchPreference(menu, switch(), spec(key))
        }
        assertSame(diagnostics, menu.documentElement.childElements().first())
        assertEquals(2, menu.documentElement.childElements().size)
        assertTrue(menu.documentElement.childElements().all { it.tagName == "PreferenceScreen" })
        assertEquals(0, menu.getElementsByTagName("PreferenceCategory").length)
        assertEquals(2, menu.getElementsByTagName("Preference").length)
        assertEquals(setOf("morphe_patch_comments", "morphe_patch_more_comments"),
            elements(menu, "CheckBoxPreference").map { it.getAttribute("android:key") }.toSet())
        assertTrue(elements(menu, "CheckBoxPreference").all { it.getAttribute("android:defaultValue") == "true" })
    }

    @Test
    fun keepsSectionsSeparateAndPreservesPreferenceKeysAndDependencies() {
        val menu = resource("settings/xml/morphe_settings.xml")
        for (category in listOf("Links", "Media")) {
            addMorphePatchPreference(menu, switch(),
                spec("morphe_${category.lowercase()}").copy(category = category))
        }
        val sections = menu.documentElement.childElements()
        assertEquals(listOf("morphe_category_diagnostics", "morphe_category_links", "morphe_category_media"),
            sections.map { it.getAttribute("android:key") })
        assertTrue(sections.all { it.tagName == "PreferenceScreen" })
        assertEquals(listOf("morphe_links", "morphe_media"),
            sections.drop(1).map { it.childElements().single().getAttribute("android:key") })
    }

    @Test
    fun loggingSharesDiagnosticsWithItsViewersInBothBoostResourceOrders() {
        for ((boost, viewersFirst) in listOf(false to true, true to true, true to false)) {
            val menu = resource(if (boost) "boost/xml/morphe_boost_settings_skeleton.xml"
                else "settings/xml/morphe_settings.xml")
            val patch = crashLogPatch(
                if (boost) ExtensionPatches.Boost else ExtensionPatches.RIF,
                if (boost) AppCompatibility.Boost else AppCompatibility.RedditIsFun,
            )
            val viewers = resource("boost/xml/morphe_boost_diagnostics_entry.xml")
            if (boost && viewersFirst) addBoostSettings(menu, viewers)
            addMorphePatchPreference(menu, switch(), spec("morphe_patch_log_exceptions")
                .copy(category = assertNotNull(patch.category)))
            if (boost && !viewersFirst) addBoostSettings(menu, viewers)

            val section = menu.documentElement.childElements().single()
            assertEquals("morphe_category_diagnostics", section.getAttribute("android:key"))
            assertEquals(setOf("morphe_patch_log_exceptions", "morphe_exception_log", "morphe_crash_logs"),
                section.childElements().map { it.getAttribute("android:key") }.toSet())
            assertEquals(3, section.childElements().size)
        }
    }

    @Test
    fun usesTheCorrespondingMenuForEverySupportedPackage() {
        val clients = AppCompatibility.BaconReader + AppCompatibility.Boost + AppCompatibility.Infinity +
            AppCompatibility.JoeyForReddit + AppCompatibility.RedditIsFun + AppCompatibility.Relay +
            AppCompatibility.SyncForReddit + arrayOf(AppCompatibility.SyncForLemmy)
        assertEquals(16, clients.size)
        for (client in clients) {
            val packageName = assertNotNull(client.packageName)
            assertEquals(
                if (packageName == "com.rubenmayayo.reddit") "morphe_boost_settings_skeleton" else "morphe_settings",
                morpheMenuResource(packageName),
            )
        }
        assertFailsWith<IllegalArgumentException> { morpheMenuResource("") }
    }

    @Test
    fun keepsBoostControlsOutOfTheSkeletonAndPreservesTheExactSliderRangeAndDefault() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        assertEquals(0, menu.documentElement.childElements().size)
        val options = resource("boost/xml/morphe_boost_image_dismiss_options.xml")
        addMorphePatchPreference(menu, switch(), spec("morphe_patch_image_tuning"), options)
        val slider = elements(menu, "SeekBarPreference").single()
        assertEquals("12", slider.getAttribute("android:min"))
        assertEquals("100", slider.getAttribute("android:max"))
        assertEquals("100", slider.getAttribute("android:defaultValue"))
        assertEquals("true", slider.getAttribute("android:showSeekBarValue"))
        assertEquals("morphe_patch_image_tuning", slider.getAttribute("android:dependency"))
        assertFalse(keys(menu).contains("morphe_boost_search_show_active_subreddits"))
    }

    @Test
    fun archiveSourcesDefaultEnabledAndDependOnTheirMasterSwitch() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        val key = "morphe_patch_add_archive_links_to_context_menu"
        val options = resource("boost/xml/morphe_boost_archive_options.xml")
        addMorphePatchPreference(menu, switch(), spec(key), options)
        val sources = elements(menu, "CheckBoxPreference").filter {
            it.getAttribute("android:key").startsWith("morphe_boost_archive_")
        }
        assertEquals(8, sources.size)
        assertEquals(8, sources.map { it.getAttribute("android:key") }.toSet().size)
        assertTrue(sources.all { it.getAttribute("android:defaultValue") == "true" })
        assertTrue(sources.all { it.getAttribute("android:dependency") == key })
    }

    @Test
    fun preservesChainedMediaDependenciesWhenAddingTheMasterSwitch() {
        val menu = resource("settings/xml/morphe_settings.xml")
        val options = resource("settings/xml/morphe_rif_inline_media.xml")
        val dependencies = options.documentElement.childElements().associate {
            it.getAttribute("android:key") to it.getAttribute("android:dependency")
        }
        addMorphePatchPreference(menu, switch(), spec("morphe_patch_inline_media"), options)
        val controls = menu.documentElement.childElements().last().childElements().drop(1)
        for (control in controls) {
            assertEquals(dependencies.getValue(control.getAttribute("android:key")).ifEmpty { "morphe_patch_inline_media" },
                control.getAttribute("android:dependency"))
        }
    }

    @Test
    fun boostLinksUseThreeOrderedSectionsWithoutChangingControlsOrDependencies() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        val archives = "morphe_patch_add_archive_links_to_context_menu"
        val visibility = "morphe_patch_show_strip_query_parameters_option"
        addMorphePatchPreference(menu, switch(), spec(archives).copy(category = "Links"),
            resource("boost/xml/morphe_boost_archive_options.xml"))
        addMorphePatchPreference(menu, switch(), spec(visibility).copy(category = "Links"),
            resource("boost/xml/morphe_boost_link_options.xml"))
        val controls = elements(menu, "CheckBoxPreference").associateBy { it.getAttribute("android:key") }

        groupBoostLinkSettings(menu)

        val sections = menu.documentElement.childElements().single().childElements()
        assertEquals(listOf("General", "Archive links", "Enabled archive services"),
            sections.map { it.getAttribute("android:title") })
        assertTrue(sections.all { it.tagName == "PreferenceCategory" })
        assertEquals(listOf(visibility, "morphe_boost_strip_query_parameters_default"),
            sections[0].childElements().map { it.getAttribute("android:key") })
        assertEquals(listOf(archives, "morphe_boost_archive_strip_query_parameters"),
            sections[1].childElements().map { it.getAttribute("android:key") })
        val services = sections[2].childElements()
        assertEquals(7, services.size)
        assertTrue(services.all {
            it.getAttribute("android:dependency") == archives &&
                java.net.URI(it.getAttribute("android:summary")).let { uri ->
                    uri.scheme == "https" && !uri.host.isNullOrBlank()
                }
        })
        for (entry in elements(menu, "CheckBoxPreference")) {
            assertSame(controls.getValue(entry.getAttribute("android:key")), entry)
        }
        val default = controls.getValue("morphe_boost_strip_query_parameters_default")
        assertEquals("false", default.getAttribute("android:defaultValue"))
        assertEquals(visibility, default.getAttribute("android:dependency"))
    }

    @Test
    fun boostLinkSectionsOmitControlsFromPatchesThatWereNotIncluded() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        addMorphePatchPreference(menu, switch(),
            spec("morphe_patch_show_strip_query_parameters_option").copy(category = "Links"),
            resource("boost/xml/morphe_boost_link_options.xml"))
        groupBoostLinkSettings(menu)
        val sections = menu.documentElement.childElements().single().childElements()
        assertEquals(listOf("General"), sections.map { it.getAttribute("android:title") })
    }

    @Test
    fun preservesOptInDefaultsIndependentlyOfPatchSelectionDefaults() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        addMorphePatchPreference(menu, switch(),
            spec("morphe_boost_reddit_undelete_enabled").copy(defaultEnabled = false))
        assertEquals("false", elements(menu, "CheckBoxPreference").single().getAttribute("android:defaultValue"))
        val patch = morpheBytecodePatch(
            name = "Example patch", default = false, extensionPatch = ExtensionPatches.Boost,
        ) { compatibleWith(*AppCompatibility.Boost) }
        assertFalse(patch.default)
        assertEquals(AppCompatibility.Boost.toList(), patch.compatibility)
        assertEquals(null, patch.category)
        assertEquals(null, patch.description)
        assertTrue(boostMorpheSettingsPatch in dependencies(patch))
        assertTrue(morpheSettingsContextPatch in dependencies(patch))
    }

    @Test
    fun makesLemmySettingsIndependentOfTheRedditExtensionAndCrashLogging() {
        val patch = morpheBytecodePatch(name = "Example Lemmy feature",
            extensionPatch = ExtensionPatches.SyncForLemmy) {
            compatibleWith(AppCompatibility.SyncForLemmy)
        }
        val dependencies = dependencies(patch)
        assertTrue(lemmySettings in dependencies)
        assertTrue(morpheSettingsContextPatch in dependencies)
        assertFalse(ExtensionPatches.Sync in dependencies)
        assertFalse(dependencies.any { it.name == "Log exceptions" })
    }

    @Test
    fun resolvesBoostsCompositeHttpDependenciesToItsNativeMorpheMenu() {
        val patch = morpheBytecodePatch(
            name = "Example HTTP feature",
            extensionPatch = interceptHttpRequests,
        ) { compatibleWith(*AppCompatibility.Boost) }
        assertTrue(boostMorpheSettingsPatch in dependencies(patch))
        assertFalse(lemmySettings in dependencies(patch))
    }

    @Test
    fun preservesTheLegacyAppAgnosticFactoryWithoutRequiringAClientSettingsMenu() {
        val extension = bytecodePatch {}
        val legacy = fixMoreCommentsPatch(extension, emptyArray())
        assertTrue(legacy.default)
        assertEquals(emptyList(), legacy.compatibility)
        assertEquals(setOf(extension), legacy.dependencies)
    }

    @Test
    fun mapsOAuthOptionsToBoostsNativePreferencesAndLocksEveryInfinityCallbackVariant() {
        for (namespaceAware in listOf(false, true)) {
            val boost = resource("settings/xml/morphe_reddit_api.xml", namespaceAware)
            configureOAuthPreferences(boost, "com.rubenmayayo.reddit")
            assertEquals(setOf("pref_dev_reddit_client_id", "pref_dev_reddit_redirect_url", "pref_dev_reddit_user_agent"),
                keys(boost))
            for (client in AppCompatibility.Infinity) {
                val options = resource("settings/xml/morphe_reddit_api.xml", namespaceAware)
                configureOAuthPreferences(options, assertNotNull(client.packageName))
                assertEquals(2, options.getElementsByTagName("EditTextPreference").length)
                val callback = elements(options, "Preference").single()
                assertEquals("morphe_redirect_uri_pref_key", callback.getAttribute("android:key"))
                assertEquals("false", callback.getAttribute("android:selectable"))
                assertEquals("false", callback.getAttribute("android:persistent"))
                assertFalse(callback.hasAttribute("android:defaultValue"))
            }
        }
    }

    @Test
    fun rejectsDuplicateControlsIncludingNestedOptionsBeforeChangingTheMenu() {
        val menu = resource("settings/xml/morphe_settings.xml")
        val count = menu.getElementsByTagName("*").length
        val options = parse("""
            <PreferenceCategory xmlns:android="http://schemas.android.com/apk/res/android">
                <PreferenceScreen android:key="nested">
                    <Preference android:key="morphe_exception_log" />
                </PreferenceScreen>
            </PreferenceCategory>
        """)
        assertFailsWith<IllegalArgumentException> {
            addMorphePatchPreference(menu, switch(), spec("morphe_patch_conflicting"), options)
        }
        assertEquals(count, menu.getElementsByTagName("*").length)
        addMorphePatchPreference(menu, switch(), spec("morphe_patch_unique"))
        val addedCount = menu.getElementsByTagName("*").length
        assertFailsWith<IllegalArgumentException> {
            addMorphePatchPreference(menu, switch(), spec("morphe_patch_unique"))
        }
        assertEquals(addedCount, menu.getElementsByTagName("*").length)
    }

    @Test
    fun presentsApkOnlyChangesAsReadOnlyWithoutCreatingAStoredBoolean() {
        val menu = resource("boost/xml/morphe_boost_settings_skeleton.xml")
        addMorphePatchPreference(menu, resource("settings/xml/morphe_patch_read_only.xml"),
            spec("morphe_patch_layout").copy(readOnly = true))
        val entry = elements(menu, "Preference").single()
        assertEquals("false", entry.getAttribute("android:selectable"))
        assertEquals("false", entry.getAttribute("android:persistent"))
        assertFalse(entry.hasAttribute("android:defaultValue"))
        assertTrue(entry.getAttribute("android:summary").contains("Repatch"))
        assertFailsWith<IllegalStateException> { MorphePatchSetting("morphe_patch_layout", false).enabledCall }
    }

    @Test
    fun rendersSingleScreenActionsDirectlyWithoutToggleOrDuplicateEntryForEveryMenu() {
        for (path in listOf("settings/xml/morphe_settings.xml", "boost/xml/morphe_boost_settings_skeleton.xml")) {
            val menu = resource(path)
            val key = "morphe_patch_import_and_export_oauth_tokens"
            val options = resource("boost/xml/morphe_boost_oauth_entry.xml")
            addMorphePatchPreference(menu, resource("settings/xml/morphe_patch_read_only.xml"),
                spec(key).copy(title = "Import and export OAuth tokens", category = "Login & API", readOnly = true),
                options)
            val entry = elements(menu, "Preference").single { it.getAttribute("android:key") == key }
            assertEquals("Import and export OAuth tokens", entry.getAttribute("android:title"))
            assertEquals("false", entry.getAttribute("android:persistent"))
            assertFalse(entry.getAttribute("android:selectable") == "false")
            assertFalse(entry.hasAttribute("android:dependency"))
            assertEquals(0, elements(menu, "CheckBoxPreference").size)
            assertFalse(keys(menu).contains("morphe_boost_oauth_tokens"))
            val intent = entry.getElementsByTagName("intent").item(0) as Element
            assertEquals("app.morphe.extension.boost.settings.BoostSettingsActivity",
                intent.getAttribute("android:targetClass"))
            assertEquals("oauth_tokens", (intent.getElementsByTagName("extra").item(0) as Element)
                .getAttribute("android:value"))
        }
    }

    @Test
    fun retainsUnmodifiedNativeInstructionsAndRegisterMapsForTheDisabledPath() {
        val owner = owner(method("enabled", "Z", registers = 300, code = "const/4 v0, 0x0\nreturn v0"))
        val target = owner.methods.single()
        MorphePatchSetting("morphe_patch_example").patchMethod(owner, target) {
            replaceInstruction(0, "const/4 v0, 0x1")
        }
        val native = owner.methods.single { it.name.startsWith("morpheNative_") }
        val enabled = owner.methods.single { it.name.startsWith("morpheEnabled_") }
        assertEquals(300, assertNotNull(native.implementation).registerCount)
        assertEquals(300, assertNotNull(enabled.implementation).registerCount)
        assertEquals(0, (native.implementation!!.instructions.first() as Instruction11n).narrowLiteral)
        assertEquals(1, (enabled.implementation!!.instructions.first() as Instruction11n).narrowLiteral)
        val wrapper = owner.methods.single { it.name == "enabled" }
        assertEquals(target.accessFlags, wrapper.accessFlags)
        assertEquals(listOf("isEnabled_morphe_patch_example", enabled.name, native.name),
            references(wrapper).map { it.name })
    }

    @Test
    fun preservesWideArgumentsReceiversAndResultsWithoutClobberingTheParameterRegisters() {
        val owner = owner(method("wide", "J", listOf("J", "Ljava/lang/String;", "D"),
            static = false, registers = 220, code = "const-wide/16 v0, 0x0\nreturn-wide v0"))
        val target = owner.methods.single()
        MorphePatchSetting("morphe_patch_wide").patchMethod(owner, target) {}
        val wrapper = owner.methods.single { it.name == "wide" }
        assertEquals(8, assertNotNull(wrapper.implementation).registerCount)
        val calls = wrapper.implementation!!.instructions.filter { it.opcode == Opcode.INVOKE_DIRECT_RANGE }
        assertEquals(2, calls.size)
        for (call in calls) {
            call as Instruction3rc
            assertEquals(2, call.startRegister)
            assertEquals(6, call.registerCount)
        }
        assertEquals(2, wrapper.implementation!!.instructions.count { it.opcode == Opcode.RETURN_WIDE })
    }

    @Test
    fun bridgesNativeOAuthGettersWithoutRenamingTheJniEntryPoint() {
        val original = ImmutableMethod(
            "Ltest/Native;", "getId", emptyList(), "Ljava/lang/String;",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.NATIVE.value,
            null, null, null,
        )
        val owner = owner(original)
        val native = owner.methods.single()
        val bridge = bridgeNativeOAuthGetter(owner, native, "getConfiguredClientId")
        assertSame(native, owner.methods.single { it.name == "getId" })
        assertTrue(AccessFlags.NATIVE.isSet(native.accessFlags))
        assertEquals(null, native.implementation)
        assertFalse(AccessFlags.NATIVE.isSet(bridge.accessFlags))
        assertEquals(listOf("isEnabled_morphe_patch_spoof_client", "getConfiguredClientId", "getId"),
            references(bridge).map { it.name })
        assertEquals(2, bridge.implementation!!.instructions.count { it.opcode == Opcode.RETURN_OBJECT })
    }

    @Test
    fun disabledIdentityHooksReturnTheOriginalArgument() {
        val owner = owner(method("normalize", "Ljava/lang/String;", listOf("Ljava/lang/String;", "I"),
            code = "const-string v0, \"normalized\"\nreturn-object v0"))
        MorphePatchSetting("morphe_patch_normalize").guardHook(owner, "normalize", identity = true)
        val wrapper = owner.methods.single { it.name == "normalize" }
        val last = wrapper.implementation!!.instructions.last() as OneRegisterInstruction
        assertEquals(Opcode.RETURN_OBJECT, wrapper.implementation!!.instructions.last().opcode)
        assertEquals(2, last.registerA)
    }

    @Test
    fun supportsHighRegisterOAuthStringsAndKeepsTheirNativeFallback() {
        val target = method("clientId", "Ljava/lang/String;", registers = 250,
            code = "const-string v220, \"native-id\"\nreturn-object v220")
        target.overrideOAuthValue(0, "getClientId")
        val instructions = target.implementation!!.instructions
        assertEquals("native-id", ((instructions[0] as ReferenceInstruction).reference as StringReference).string)
        val call = instructions[1] as Instruction3rc
        assertEquals(Opcode.INVOKE_STATIC_RANGE, call.opcode)
        assertEquals(220, call.startRegister)
        assertEquals(1, call.registerCount)
        assertEquals(220, (instructions[2] as OneRegisterInstruction).registerA)
    }

    @Test
    fun switchesConstructorStringsAtExistingControlFlowLabelsWithoutChangingRegisters() {
        val target = method("<init>", "V", static = false, registers = 250, code = """
            invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V
            goto :assignment
            :assignment
            const-string v220, "u/"
            return-void
        """)
        MorphePatchSetting("morphe_patch_endpoint").replaceString(target, 2, "user/")
        val instructions = target.implementation!!.instructions
        assertEquals(250, target.implementation!!.registerCount)
        val jump = instructions[1] as BuilderInstruction10t
        assertEquals(Opcode.INVOKE_STATIC, instructions[jump.target.location.index].opcode)
        val strings = instructions.mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
        assertEquals(setOf("u/", "user/"), strings.map { it.string }.toSet())
    }

    @Test
    fun independentlyGatesStackedHooksEvenWhenTheFingerprintSignatureIsCached() {
        val signature = method("onResume", "V", static = false, registers = 220, code = "return-void")
        val owner = owner(signature)
        wrapMorpheHook(owner, signature, after = "invoke-static {}, Ltest/Hooks;->first()V",
            setting = MorphePatchSetting("morphe_patch_first"))
        wrapMorpheHook(owner, signature, after = "invoke-static {}, Ltest/Hooks;->second()V",
            setting = MorphePatchSetting("morphe_patch_second"))
        val outer = owner.methods.single { it.name == "onResume" }
        val outerReferences = references(outer)
        assertTrue(outerReferences.first().name.startsWith("morpheOriginal_"))
        assertEquals(listOf("isEnabled_morphe_patch_second", "second"), outerReferences.drop(1).map { it.name })
        val inner = owner.methods.single { it.name == outerReferences.first().name }
        assertEquals(listOf("isEnabled_morphe_patch_first", "first"), references(inner).drop(1).map { it.name })
        assertEquals(Opcode.INVOKE_DIRECT_RANGE, outer.implementation!!.instructions.first().opcode)
    }

    private fun spec(key: String) = MorphePreferenceSpec(key, "Example feature", "Feature description", PatchCategory.COMMENTS)
    private fun switch() = resource("settings/xml/morphe_patch_switch.xml")
    private fun elements(document: Document, tag: String): List<Element> =
        document.getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
    private fun keys(document: Document) = elements(document, "*")
        .mapNotNull { it.getAttribute("android:key").takeIf(String::isNotBlank) }.toSet()

    private fun dependencies(patch: Patch): Set<Patch> = buildSet {
        fun visit(current: Patch) {
            if (add(current)) current.dependencies.forEach(::visit)
        }
        visit(patch)
    }

    private fun method(
        name: String,
        result: String,
        parameters: List<String> = emptyList(),
        static: Boolean = true,
        registers: Int = 20,
        code: String,
    ): MutableMethod = ImmutableMethod(
        "Ltest/Native;", name, parameters.map { ImmutableMethodParameter(it, null, null) },
        result, AccessFlags.PUBLIC.value or if (static) AccessFlags.STATIC.value else 0,
        null, null, MutableMethodImplementation(registers),
    ).toMutable().apply { addInstructions(0, code) }

    private fun owner(method: Method) = MutableClass(ImmutableClassDef(
        method.definingClass, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
        emptyList(), null, emptyList(), emptyList(), listOf(method),
    ))

    private fun references(method: Method) = assertNotNull(method.implementation).instructions
        .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }

    private fun resource(name: String, namespaceAware: Boolean = false): Document =
        assertNotNull(javaClass.classLoader.getResourceAsStream(name)).use {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = namespaceAware }.newDocumentBuilder().parse(it)
        }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(xml.trimIndent().byteInputStream())

    private fun Element.childElements() =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
}
