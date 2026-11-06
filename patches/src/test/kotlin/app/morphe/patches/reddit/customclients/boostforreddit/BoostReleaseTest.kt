/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostBindings
import app.morphe.patches.reddit.customclients.boostforreddit.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.boostforreddit.fix.profileabout.fixProfileAboutScrollingPatch
import app.morphe.patches.reddit.customclients.fixMoreCommentsPatch
import app.morphe.patches.reddit.customclients.misc.extension.mapping.FieldId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.MethodId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import com.android.tools.smali.dexlib2.DexFileFactory
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoostReleaseTest {
    private fun mappingText() = checkNotNull(javaClass.classLoader.getResourceAsStream("boost/mapping.txt"))
        .bufferedReader().use { it.readText() }

    @Test
    fun compatibilityAndMappingTargetTheSameRelease() {
        val version = BoostBindings.table.version
        assertEquals("1.14.3", version)
        assertEquals(listOf(version), AppCompatibility.Boost.single().targets.map { it.version })
        assertTrue(mappingText().lineSequence().first().endsWith("Boost $version APK."))
        BoostBindings.table.mapping(ProguardMapping.read(mappingText().reader()).reversed())
    }

    @Test
    fun nativeFixesAreOptInWithoutChangingOtherClients() {
        assertFalse(fixMoreComments.default)
        assertFalse(fixProfileAboutScrollingPatch.default)
        assertTrue(fixMoreCommentsPatch(bytecodePatch {}, emptyArray()).default)
    }

    @Test
    fun allMappingsAndBindingsResolveAgainstOfficialApk() {
        val path = System.getenv("BOOST_APK")
        assumeTrue("Set BOOST_APK to the official Boost ${BoostBindings.table.version} APK", path != null)
        val apk = File(checkNotNull(path))
        val text = mappingText()
        val checksum = text.lineSequence().single { it.startsWith("# APK SHA-256: ") }.substringAfter(": ")
        assertEquals(checksum, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(apk.readBytes())))

        val container = DexFileFactory.loadDexContainer(apk, null)
        val native = container.dexEntryNames.flatMap { checkNotNull(container.getEntry(it)).dexFile.classes }
        val classes = native.mapTo(HashSet()) { it.type }
        val methods = native.flatMap { cls ->
            cls.methods.map { MethodId(it.definingClass, it.name, it.parameterTypes.map(CharSequence::toString), it.returnType) }
        }.toSet()
        val fields = native.flatMap { cls ->
            cls.fields.map { FieldId(it.definingClass, it.name, it.type) }
        }.toSet()
        val libraries = ProguardMapping.read(text.reader())
        assertEquals(emptySet(), libraries.classes.keys - classes, "Missing library classes")
        assertEquals(emptySet(), libraries.methods.keys - methods, "Missing library methods")
        assertEquals(emptySet(), libraries.fields.keys - fields, "Missing library fields")
        BoostBindings.table.sourceDefinitions(native, libraries.reversed())
    }
}
