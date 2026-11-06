/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.baconreader.misc.settings.baconReaderMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.infinity.misc.settings.infinityMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.joey.misc.settings.joeyMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.redditisfun.misc.settings.rifMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.relay.misc.settings.relayMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings.syncMorpheSettingsPatch as lemmySettings
import app.morphe.patches.reddit.customclients.sync.syncforreddit.misc.settings.syncMorpheSettingsPatch as syncSettings
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
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

class MorpheSettingsPatchTest {
    @Test
    fun mergesDiagnosticStringsIntoTheStandardValuesFileWithoutReplacingNativeStrings() {
        val target = parse("<resources><string name=\"native_title\">Native title</string></resources>")
        val original = target.getElementsByTagName("string").item(0)
        val source = resource("settings/values/morphe_diagnostics_strings.xml")
        addMorpheStrings(target, source)
        assertSame(original, target.getElementsByTagName("string").item(0))
        assertEquals("Native title", original.textContent)
        val strings = target.getElementsByTagName("string")
        assertEquals(source.getElementsByTagName("string").length + 1, strings.length)
        val title = (0 until strings.length).map { strings.item(it) as Element }
            .single { it.getAttribute("name") == "morphe_settings_title" }
        assertEquals("Morphe", title.textContent)
        assertFailsWith<IllegalArgumentException> { addMorpheStrings(target, source) }
    }

    @Test
    fun coversAllSixteenPackagesWithoutReplacingBoostSettings() {
        val clients = listOf(
            baconReaderMorpheSettingsPatch to AppCompatibility.BaconReader,
            infinityMorpheSettingsPatch to AppCompatibility.Infinity,
            joeyMorpheSettingsPatch to AppCompatibility.JoeyForReddit,
            rifMorpheSettingsPatch to AppCompatibility.RedditIsFun,
            relayMorpheSettingsPatch to AppCompatibility.Relay,
            syncSettings to AppCompatibility.SyncForReddit,
            lemmySettings to arrayOf(AppCompatibility.SyncForLemmy),
        )
        val packages = mutableSetOf<String>()
        for ((patch, compatible) in clients) {
            assertTrue(patch.default)
            assertEquals(MORPHE_SETTINGS_DESCRIPTION, patch.description)
            assertEquals(compatible.toList(), patch.compatibility)
            for (app in compatible) assertTrue(packages.add(assertNotNull(app.packageName)))
        }
        assertEquals(16, packages.size)
        assertFalse(packages.contains("com.rubenmayayo.reddit"))
        assertFalse(ExtensionPatches.Sync in lemmySettings.dependencies)
    }

    @Test
    fun registersAPrivateActivityWithoutChangingNativeComponents() {
        for (namespaceAware in listOf(false, true)) {
            val manifest = parse("""
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
                    <application android:name=".Application">
                        <activity android:name=".SettingsActivity" android:exported="true" />
                    </application>
                </manifest>
            """, namespaceAware)
            val native = manifest.getElementsByTagName("activity").item(0)
            registerMorpheSettingsActivity(manifest)
            val activities = manifest.getElementsByTagName("activity")
            assertSame(native, activities.item(0))
            val entry = activities.item(1) as Element
            assertEquals(MORPHE_SETTINGS_ACTIVITY, entry.getAttribute("android:name"))
            assertEquals("false", entry.getAttribute("android:exported"))
            assertEquals(0, entry.getElementsByTagName("intent-filter").length)
            assertFailsWith<IllegalArgumentException> { registerMorpheSettingsActivity(manifest) }
        }
    }

    @Test
    fun addsAnExplicitIntentToPlainAndQualifiedPreferenceScreensForEveryPackageVariant() {
        val packages = AppCompatibility.RedditIsFun + AppCompatibility.Infinity + AppCompatibility.SyncForReddit +
                arrayOf(AppCompatibility.SyncForLemmy)
        for (namespaceAware in listOf(false, true)) {
            for (root in listOf("PreferenceScreen", "androidx.preference.PreferenceScreen")) {
                for (app in packages) {
                    val target = parse("""
                        <$root xmlns:android="http://schemas.android.com/apk/res/android">
                            <Preference android:key="native_setting" android:fragment="native.SettingsFragment" />
                        </$root>
                    """, namespaceAware)
                    val native = target.getElementsByTagName("Preference").item(0)
                    addMorphePreference(target, entry(), assertNotNull(app.packageName))
                    assertSame(native, target.getElementsByTagName("Preference").item(1))
                    val preference = target.getElementsByTagName("Preference").item(0) as Element
                    assertEquals(MORPHE_SETTINGS_ENTRY, preference.getAttribute("android:key"))
                    assertEquals("", preference.getAttribute("android:fragment"))
                    val intent = preference.getElementsByTagName("intent").item(0) as Element
                    assertEquals(app.packageName, intent.getAttribute("android:targetPackage"))
                    assertEquals(MORPHE_SETTINGS_ACTIVITY, intent.getAttribute("android:targetClass"))
                    assertFailsWith<IllegalArgumentException> {
                        addMorphePreference(target, entry(), app.packageName!!)
                    }
                }
            }
        }
    }

    @Test
    fun reusesInfinitysPreferenceClassAndLayoutButNotItsNavigationOrEnabledState() {
        val target = parse("""
            <androidx.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <native.CustomFontPreferenceWithBackground
                    style="@style/native_card"
                    android:layout="@layout/native_card"
                    android:enabled="false"
                    android:dependency="native_dependency"
                    app:fragment="native.NotificationFragment"
                    app:key="native_notifications"
                    app:title="Notifications"
                    app:top="true" />
            </androidx.preference.PreferenceScreen>
        """)
        val original = target.getElementsByTagName("native.CustomFontPreferenceWithBackground").item(0) as Element
        addMorphePreference(target, entry(), "com.example.premium", nativeStyle = true)
        val added = target.getElementsByTagName(original.tagName).item(0) as Element
        assertEquals("@style/native_card", added.getAttribute("style"))
        assertEquals("@layout/native_card", added.getAttribute("android:layout"))
        for (attribute in listOf("app:fragment", "app:key", "app:title", "android:enabled", "android:dependency")) {
            assertFalse(added.hasAttribute(attribute))
        }
        assertEquals("true", added.getAttribute("app:top"))
        assertEquals("true", added.getAttribute("app:bottom"))
        assertEquals("false", original.getAttribute("android:enabled"))
    }

    @Test
    fun keepsSyncsNativeHeaderAndSearchAboveTheMorpheEntry() {
        val target = parse("""
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                <native.HeaderPreference />
                <native.SearchPreference />
                <PreferenceCategory android:key="native_category"><Preference android:key="native_item" /></PreferenceCategory>
            </PreferenceScreen>
        """)
        addMorphePreference(target, entry(), "com.example")
        val children = target.documentElement.childNodes
        val elements = (0 until children.length).mapNotNull { children.item(it) as? Element }
        assertEquals(listOf("native.HeaderPreference", "native.SearchPreference", "Preference", "PreferenceCategory"),
            elements.map { it.tagName })
        assertEquals(MORPHE_SETTINGS_ENTRY, elements[2].getAttribute("android:key"))
        assertEquals("native_category", elements[3].getAttribute("android:key"))
    }

    @Test
    fun keepsTheSharedMenuLimitedToTwoDiagnosticViewers() {
        val menu = resource("settings/xml/morphe_settings.xml")
        val preferences = menu.getElementsByTagName("Preference")
        assertEquals(2, preferences.length)
        assertEquals(setOf("morphe_exception_log", "morphe_crash_logs"),
            (0 until preferences.length).map {
                (preferences.item(it) as Element).getAttribute("android:key")
            }.toSet())
    }

    @Test
    fun appendsAJoeyStyleCardInsideTheScrollContentWithoutCopyingNativeViewIds() {
        val layout = joeyLayout()
        val oldCard = layout.getElementsByTagName("native.CCardView").item(0)
        addMorpheLayoutEntry(layout)
        val cards = layout.getElementsByTagName("native.CCardView")
        assertEquals(2, cards.length)
        assertSame(oldCard, cards.item(0))
        val card = cards.item(1) as Element
        assertEquals("@style/native_card", card.getAttribute("style"))
        assertFalse(card.hasAttribute("android:id"))
        val labels = card.getElementsByTagName("native.CTextView")
        assertEquals(1, labels.length)
        val label = labels.item(0) as Element
        assertEquals("@string/morphe_settings_title", label.getAttribute("android:text"))
        assertFalse(label.hasAttribute("android:id"))
        val row = label.parentNode as Element
        assertEquals("@id/$MORPHE_SETTINGS_ENTRY", row.getAttribute("android:id"))
        assertEquals("@style/native_row", row.getAttribute("style"))
        assertSame(oldCard.parentNode, card.parentNode)
        assertFailsWith<IllegalArgumentException> { addMorpheLayoutEntry(layout) }
    }

    @Test
    fun rejectsUnexpectedNativeResourceShapesAndEmptyIntentPackages() {
        assertFailsWith<IllegalArgumentException> {
            addMorphePreference(parse("<LinearLayout />"), entry(), "com.example")
        }
        assertFailsWith<IllegalArgumentException> {
            addMorphePreference(parse("<PreferenceScreen />"), entry(), "")
        }
        val layout = joeyLayout()
        val scroll = layout.getElementsByTagName("native.CNestedScrollView").item(0) as Element
        val container = (0 until scroll.childNodes.length).mapNotNull { scroll.childNodes.item(it) as? Element }.single()
        container.setAttribute("android:orientation", "horizontal")
        assertFailsWith<IllegalArgumentException> { addMorpheLayoutEntry(layout) }
    }

    @Test
    fun hooksNormalAndHighRegisterListAdaptersWithoutAllocatingOrRenumberingRegisters() {
        val reference = ImmutableMethodReference(
            "Landroid/widget/ListView;", "setAdapter", listOf("Landroid/widget/ListAdapter;"), "V",
        )
        val calls = listOf(
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 7, 9, 0, 0, 0, reference) to 7,
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 220, 2, reference) to 220,
        )
        for ((call, receiver) in calls) {
            val method = menuMethod(listOf(call))
            addMorpheListEntry(method)
            val implementation = assertNotNull(method.implementation)
            assertEquals(300, implementation.registerCount)
            val hook = implementation.instructions[0] as Instruction3rc
            assertEquals(Opcode.INVOKE_STATIC_RANGE, hook.opcode)
            assertEquals(receiver, hook.startRegister)
            assertEquals(1, hook.registerCount)
            val target = (hook as ReferenceInstruction).reference as MethodReference
            assertEquals(MORPHE_SETTINGS_HOOK, target.definingClass)
            assertEquals("addListEntry", target.name)
            assertEquals(listOf("Landroid/widget/ListView;"), target.parameterTypes.map(CharSequence::toString))
            val original = implementation.instructions[1]
            assertEquals(call.opcode, original.opcode)
            when (original) {
                is Instruction35c -> assertEquals(9, original.registerD)
                is Instruction3rc -> assertEquals(2, original.registerCount)
            }
        }
    }

    @Test
    fun rejectsMissingAndAmbiguousNativeAdapterCalls() {
        assertFailsWith<IllegalArgumentException> { addMorpheListEntry(menuMethod(emptyList())) }
        val call = ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 220, 2,
            ImmutableMethodReference("Landroid/widget/ListView;", "setAdapter",
                listOf("Landroid/widget/ListAdapter;"), "V"))
        assertFailsWith<IllegalArgumentException> { addMorpheListEntry(menuMethod(listOf(call, call))) }
    }

    private fun menuMethod(calls: List<Instruction>) = ImmutableMethod(
        "Lnative/SettingsMenu;", "initialize", emptyList(), "V", AccessFlags.PUBLIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(300,
            calls + ImmutableInstruction10x(Opcode.RETURN_VOID), emptyList(), emptyList()),
    ).toMutable()

    private fun joeyLayout() = parse("""
        <native.CLinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <native.CNestedScrollView>
                <native.CLinearLayout android:orientation="vertical">
                    <native.CCardView style="@style/native_card" android:id="@id/native_card">
                        <native.CLinearLayout android:orientation="vertical" android:id="@id/native_content">
                            <native.CTextView android:text="Native category" />
                            <native.CLinearLayout android:id="@id/native_setting" style="@style/native_row">
                                <native.CImageView />
                                <native.CTextView android:id="@id/native_label" android:text="Native setting" />
                            </native.CLinearLayout>
                        </native.CLinearLayout>
                    </native.CCardView>
                </native.CLinearLayout>
            </native.CNestedScrollView>
        </native.CLinearLayout>
    """)

    private fun entry() = resource("settings/xml/morphe_settings_entry.xml")

    private fun resource(name: String): Document =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)).use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }

    private fun parse(xml: String, namespaceAware: Boolean = false): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = namespaceAware }
            .newDocumentBuilder().parse(xml.trimIndent().byteInputStream())
}
