/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patches.reddit.customclients.baconreader.debug.hookExceptionHandler as baconReaderPatch
import app.morphe.patches.reddit.customclients.baconreader.misc.settings.baconReaderMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.boostforreddit.debug.hookExceptionHandler as boostPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.infinity.debug.hookExceptionHandler as infinityPatch
import app.morphe.patches.reddit.customclients.infinity.misc.settings.infinityMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.joey.debug.hookExceptionHandler as joeyPatch
import app.morphe.patches.reddit.customclients.joey.misc.settings.joeyMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.redditisfun.debug.hookExceptionHandler as redditIsFunPatch
import app.morphe.patches.reddit.customclients.redditisfun.misc.settings.rifMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.relay.debug.hookExceptionHandler as relayPatch
import app.morphe.patches.reddit.customclients.relay.misc.settings.relayMorpheSettingsPatch
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.debug.hookExceptionHandler as lemmyPatch
import app.morphe.patches.reddit.customclients.sync.syncforlemmy.misc.settings.syncMorpheSettingsPatch as lemmySettings
import app.morphe.patches.reddit.customclients.sync.syncforreddit.debug.hookExceptionHandler as syncPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.misc.settings.syncMorpheSettingsPatch as syncSettings
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashLogPatchTest {
    @Test
    fun coversEverySupportedPackageWithMorpheSettingsEnabled() {
        val clients = listOf(
            Triple(baconReaderPatch, AppCompatibility.BaconReader, baconReaderMorpheSettingsPatch),
            Triple(boostPatch, AppCompatibility.Boost, boostMorpheSettingsPatch),
            Triple(infinityPatch, AppCompatibility.Infinity, infinityMorpheSettingsPatch),
            Triple(joeyPatch, AppCompatibility.JoeyForReddit, joeyMorpheSettingsPatch),
            Triple(redditIsFunPatch, AppCompatibility.RedditIsFun, rifMorpheSettingsPatch),
            Triple(relayPatch, AppCompatibility.Relay, relayMorpheSettingsPatch),
            Triple(syncPatch, AppCompatibility.SyncForReddit, syncSettings),
            Triple(lemmyPatch, arrayOf(AppCompatibility.SyncForLemmy), lemmySettings),
        )
        val packages = mutableSetOf<String>()
        for ((patch, compatible, settings) in clients) {
            assertEquals("Log exceptions", patch.name)
            assertTrue(patch.default)
            assertEquals(compatible.toList(), patch.compatibility)
            for (app in compatible) assertTrue(packages.add(assertNotNull(app.packageName)))
            assertTrue(settings in dependencies(patch))
            assertTrue(dependencies(patch).any {
                it.description == "Writes a crash log file to the Documents folder when the app crashes."
            })
            assertEquals("Logs reported errors and fatal crashes, viewable in Morphe settings.", patch.description)
        }
        assertEquals(17, packages.size)
        assertFalse(ExtensionPatches.Sync in dependencies(lemmyPatch))
    }

    @Test
    fun resolvesAllManifestApplicationNameForms() {
        for (name in listOf(".ClientApplication", "ClientApplication", "com.example.ClientApplication")) {
            assertEquals("Lcom/example/ClientApplication;", applicationDescriptor("com.example", name))
        }
        assertEquals("Loriginal/ClientApplication;", applicationDescriptor("com.premium", "original.ClientApplication"))
        assertFailsWith<IllegalArgumentException> { applicationDescriptor("com.example", "") }
    }

    @Test
    fun resolvesLauncherAliasesWithAndWithoutNamespaceAwareParsing() {
        val xml = """
            <manifest package="com.example.premium" xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:name="original.ClientApplication">
                    <activity android:name=".NotTheLauncher">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                    </activity>
                    <activity-alias android:name=".Icon" android:targetActivity="original.MainActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity-alias>
                </application>
            </manifest>
        """.trimIndent()
        for (namespaceAware in listOf(false, true)) {
            val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = namespaceAware }
            val manifest = parser.newDocumentBuilder().parse(xml.byteInputStream())
            assertEquals("Loriginal/MainActivity;", launcherActivityType(manifest))
        }
    }

    @Test
    fun findsInheritedActivityOnCreateOrCreatesAnOverrideForPlatformInheritance() {
        val base = ImmutableClassDef(
            "Lexample/BaseActivity;", AccessFlags.PUBLIC.value, "Landroid/app/Activity;",
            emptyList(), null, emptyList(), emptyList(),
            listOf(method("onCreate", listOf("Landroid/os/Bundle;"), owner = "Lexample/BaseActivity;")),
        )
        val launcher = ImmutableClassDef(
            "Lexample/MainActivity;", AccessFlags.PUBLIC.value, base.type,
            emptyList(), null, emptyList(), emptyList(), emptyList(),
        )
        assertEquals(base.type, activityOnCreateOwner(launcher.type, mapOf(base.type to base, launcher.type to launcher)))
        val platform = MutableClass(ImmutableClassDef(
            "Lexample/PlatformActivity;", AccessFlags.PUBLIC.value, "Landroid/app/Activity;",
            emptyList(), null, emptyList(), emptyList(), emptyList(),
        ))
        assertEquals(platform.type, activityOnCreateOwner(platform.type, mapOf(platform.type to platform)))
        val method = onCreateMethod(platform, listOf("Landroid/os/Bundle;"))
        assertEquals(2, assertNotNull(method.implementation).registerCount)
        val call = method.implementation!!.instructions.first() as Instruction3rc
        assertEquals(2, call.registerCount)
        assertEquals(listOf("Landroid/os/Bundle;"),
            ((call as ReferenceInstruction).reference as MethodReference).parameterTypes.map(CharSequence::toString))
    }

    @Test
    fun createsAnOverrideForInheritedApplicationOnCreateWithoutChangingTheSuperclassCall() {
        val owner = MutableClass(ImmutableClassDef(
            "Lexample/ClientApplication;", AccessFlags.PUBLIC.value, "Lexample/BaseApplication;",
            emptyList(), null, emptyList(), emptyList(), emptyList(),
        ))
        val method = onCreateMethod(owner)
        assertSame(method, onCreateMethod(owner))
        assertEquals(1, owner.virtualMethods.size)
        val call = assertNotNull(method.implementation).instructions.first() as Instruction3rc
        val reference = (call as ReferenceInstruction).reference as MethodReference
        assertEquals(Opcode.INVOKE_SUPER_RANGE, call.opcode)
        assertEquals("Lexample/BaseApplication;", reference.definingClass)
        assertEquals("onCreate", reference.name)
        assertEquals(0, call.startRegister)
        assertEquals(1, call.registerCount)
    }

    @Test
    fun initializesApplicationsWithHighParameterRegistersWithoutAllocatingALocal() {
        val existing = method("onCreate", emptyList(), AccessFlags.PUBLIC.value, 200)
        val owner = MutableClass(ImmutableClassDef(
            "Lexample/ClientApplication;", AccessFlags.PUBLIC.value, "Landroid/app/Application;",
            emptyList(), null, emptyList(), emptyList(), listOf(existing),
        ))
        val target = onCreateMethod(owner)
        target.addInstructions(0,
            "invoke-static/range {p0 .. p0}, $EXCEPTION_HOOK_CLASS_DESCRIPTOR->initialize(Landroid/app/Application;)V")
        val implementation = assertNotNull(target.implementation)
        assertEquals(200, implementation.registerCount)
        val call = implementation.instructions.first() as Instruction3rc
        assertEquals(199, call.startRegister)
        assertEquals(1, call.registerCount)
    }

    @Test
    fun locatesThrowableParametersForStaticInstanceAndWideArgumentHandlers() {
        assertEquals(0, exceptionParameterRegister(method("log", listOf("Ljava/lang/Throwable;"),
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)))
        assertEquals(4, exceptionParameterRegister(method("log",
            listOf("I", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Throwable;"))))
        assertEquals(5, exceptionParameterRegister(method("log", listOf("J", "D", "Ljava/lang/Throwable;"))))
        assertFailsWith<IllegalArgumentException> { exceptionParameterRegister(method("log", emptyList())) }
    }

    @Test
    fun recognizesObfuscatedCrashlyticsReportingWithoutMatchingOtherMethods() {
        val owner = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;"
        for (name in listOf("recordException", "d")) {
            assertTrue(isCrashlyticsExceptionHandler(method(name, listOf("Ljava/lang/Throwable;"), owner = owner)))
        }
        assertFalse(isCrashlyticsExceptionHandler(method("log", listOf("Ljava/lang/String;"), owner = owner)))
        assertFalse(isCrashlyticsExceptionHandler(method("d", listOf("Ljava/lang/Throwable;"),
            flags = AccessFlags.PRIVATE.value, owner = owner)))
        assertFalse(isCrashlyticsExceptionHandler(method("<init>", listOf("Ljava/lang/Throwable;"), owner = owner)))
        assertFalse(isCrashlyticsExceptionHandler(method("d", listOf("Ljava/lang/Throwable;"))))
    }

    @Test
    fun redirectsAndroidErrorLoggingWithoutChangingRegistersOrResults() {
        val parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Throwable;")
        val original = ImmutableInstruction35c(Opcode.INVOKE_STATIC, 3, 7, 2, 9, 0, 0,
            ImmutableMethodReference("Landroid/util/Log;", "e", parameters, "I"))
        val rewritten = assertNotNull(rewriteCrashLogInvocation(original, true) { false }) as Instruction35c
        assertEquals(Opcode.INVOKE_STATIC, rewritten.opcode)
        assertEquals(3, rewritten.registerCount)
        assertEquals(listOf(7, 2, 9), listOf(rewritten.registerC, rewritten.registerD, rewritten.registerE))
        val reference = (rewritten as ReferenceInstruction).reference as MethodReference
        assertEquals(EXCEPTION_HOOK_CLASS_DESCRIPTOR, reference.definingClass)
        assertEquals(parameters, reference.parameterTypes.map(CharSequence::toString))
        assertEquals("I", reference.returnType)
        assertNull(rewriteCrashLogInvocation(original, false) { false })
    }

    @Test
    fun redirectsRangeLoggingButLeavesNativeCrashHandlerInstallationToTheLibrary() {
        val log = ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 200, 2,
            ImmutableMethodReference("Landroid/util/Log;", "w",
                listOf("Ljava/lang/String;", "Ljava/lang/Throwable;"), "I"))
        val rewritten = assertNotNull(rewriteCrashLogInvocation(log, true) { false }) as Instruction3rc
        assertEquals(200, rewritten.startRegister)
        assertEquals(2, rewritten.registerCount)
        val setter = ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 250, 1,
            ImmutableMethodReference("Ljava/lang/Thread;", "setDefaultUncaughtExceptionHandler",
                listOf("Ljava/lang/Thread\$UncaughtExceptionHandler;"), "V"))
        assertNull(rewriteCrashLogInvocation(setter, false) { false })
        assertNull(rewriteCrashLogInvocation(setter, true) { false })
    }

    @Test
    fun redirectsOnlyVirtualThrowableStackTracesAndPreservesSuperCalls() {
        val reference = ImmutableMethodReference("Ljava/lang/Throwable;", "printStackTrace", emptyList(), "V")
        val original = ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 220, 1, reference)
        val rewritten = assertNotNull(rewriteCrashLogInvocation(original, true) { true }) as Instruction3rc
        assertEquals(Opcode.INVOKE_STATIC_RANGE, rewritten.opcode)
        assertEquals(220, rewritten.startRegister)
        assertEquals(listOf("Ljava/lang/Throwable;"),
            ((rewritten as ReferenceInstruction).reference as MethodReference).parameterTypes.map(CharSequence::toString))
        assertNull(rewriteCrashLogInvocation(original, true) { false })
        assertNull(rewriteCrashLogInvocation(original, false) { true })
        assertNull(rewriteCrashLogInvocation(
            ImmutableInstruction3rc(Opcode.INVOKE_SUPER_RANGE, 220, 1, reference), true,
        ) { true })
    }

    private fun dependencies(patch: Patch<*>): Set<Patch<*>> = buildSet {
        fun visit(current: Patch<*>) {
            for (dependency in current.dependencies) if (add(dependency)) visit(dependency)
        }
        visit(patch)
    }

    private fun method(
        name: String,
        parameters: List<String>,
        flags: Int = AccessFlags.PUBLIC.value,
        registers: Int = 8,
        owner: String = "Lexample/ClientApplication;",
    ) = ImmutableMethod(
        owner, name,
        parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, "V", flags,
        emptySet(), emptySet(), ImmutableMethodImplementation(
            registers, listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)), emptyList(), emptyList(),
        ),
    )
}
