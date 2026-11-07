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
import app.morphe.patches.reddit.customclients.boostforreddit.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.boostforreddit.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.boostforreddit.fix.feed.fixRAll
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
        val diagnostics = menu.getElementsByTagName("PreferenceCategory").item(0)
        for (key in listOf("morphe_patch_comments", "morphe_patch_more_comments")) {
            addMorphePatchPreference(menu, switch(), spec(key))
        }
        assertSame(diagnostics, menu.getElementsByTagName("PreferenceCategory").item(0))
        assertEquals(2, menu.getElementsByTagName("PreferenceCategory").length)
        assertEquals(2, menu.getElementsByTagName("Preference").length)
        assertEquals(setOf("morphe_patch_comments", "morphe_patch_more_comments"),
            elements(menu, "CheckBoxPreference").map { it.getAttribute("android:key") }.toSet())
        assertTrue(elements(menu, "CheckBoxPreference").all { it.getAttribute("android:defaultValue") == "true" })
    }

    @Test
    fun usesTheCorrespondingMenuForEverySupportedPackage() {
        val clients = AppCompatibility.BaconReader + AppCompatibility.Boost + AppCompatibility.Infinity +
            AppCompatibility.JoeyForReddit + AppCompatibility.RedditIsFun + AppCompatibility.Relay +
            AppCompatibility.SyncForReddit + arrayOf(AppCompatibility.SyncForLemmy)
        assertEquals(17, clients.size)
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
        val slider = elements(menu, "androidx.preference.SeekBarPreference").single()
        assertEquals("12", slider.getAttribute("android:min"))
        assertEquals("100", slider.getAttribute("android:max"))
        assertEquals("100", slider.getAttribute("android:defaultValue"))
        assertEquals("12", slider.getAttribute("app:min"))
        assertEquals("true", slider.getAttribute("app:showSeekBarValue"))
        assertEquals("morphe_patch_image_tuning", slider.getAttribute("android:dependency"))
        assertFalse(keys(menu).contains("morphe_boost_search_show_active_subreddits"))
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
        for (patch in listOf(fixRedditApiLookups, fixMoreComments, fixRAll)) {
            assertTrue(boostMorpheSettingsPatch in dependencies(patch))
            assertFalse(lemmySettings in dependencies(patch))
        }
        assertFalse(fixMoreComments.default)
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

    private fun dependencies(patch: Patch<*>): Set<Patch<*>> = buildSet {
        fun visit(current: Patch<*>) {
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
