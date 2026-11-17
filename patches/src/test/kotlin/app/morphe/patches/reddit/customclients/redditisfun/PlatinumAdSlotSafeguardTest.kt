/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.http.httpLibraryResourceNamespace
import app.morphe.patches.reddit.customclients.redditisfun.api.imgurAlbumsPatch
import app.morphe.patches.reddit.customclients.redditisfun.api.legacySpoofClientPatch
import app.morphe.patches.reddit.customclients.redditisfun.api.redditApiPatch
import app.morphe.patches.reddit.customclients.redditisfun.api.spoofClientPatch
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifAdBindings
import app.morphe.patches.reddit.customclients.redditisfun.debug.hookExceptionHandler
import app.morphe.patches.reddit.customclients.redditisfun.fix.apilookups.fixRedditApiLookups
import app.morphe.patches.reddit.customclients.redditisfun.fix.gallery.fixFailedGalleryImagesPatch
import app.morphe.patches.reddit.customclients.redditisfun.fix.links.fixEscapedUrlsPatch
import app.morphe.patches.reddit.customclients.redditisfun.fix.login.modifyLoginWebView
import app.morphe.patches.reddit.customclients.redditisfun.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.redditisfun.fix.platinum.applyPlatinumAdSlotSafeguard
import app.morphe.patches.reddit.customclients.redditisfun.fix.platinum.platinumAdSlotSafeguardPatch
import app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs.fixRedgifsApi
import app.morphe.patches.reddit.customclients.redditisfun.misc.settings.rifMorpheSettingsPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PlatinumAdSlotSafeguardTest {
    private fun stock(sku: RifNativeSku): List<ClassDef> {
        val name = if (sku == RifNativeSku.FREE) "RIF_FREE_APK" else "RIF_PLATINUM_APK"
        val path = System.getenv(name)
        assumeTrue("Set $name to the original RIF 5.6.22 APK", path != null)
        val apk = File(checkNotNull(path))
        val checksum = if (sku == RifNativeSku.FREE) {
            "88d39f859c850f51542c6a12d8bd3c79706debe0f1dff061ed0fe32b3dc688e1"
        } else {
            "57aacb5b6fe6f6b2987f87234492027e98a575e753342e3341dd5137d65c62b9"
        }
        assertEquals(checksum, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(apk.readBytes())))
        val container = DexFileFactory.loadDexContainer(apk, null)
        return container.dexEntryNames.flatMap { checkNotNull(container.getEntry(it)).dexFile.classes }
    }

    @Test
    fun nativeContentsIdentifyBothSkusWithoutConsultingRenameableManifestPackages() {
        val freeClasses = stock(RifNativeSku.FREE)
        val paidClasses = stock(RifNativeSku.PLATINUM)
        val free = resolveRifAdBindings(freeClasses)
        val paid = resolveRifAdBindings(paidClasses)
        assertEquals(RifNativeSku.FREE, free.sku)
        assertEquals("Le5/e0;->d0()Z", free.adSlotGate.toString())
        assertEquals(RifNativeSku.PLATINUM, paid.sku)
        assertEquals("Lf5/e0;->d0()Z", paid.adSlotGate.toString())
        assertEquals(AccessFlags.PROTECTED.value, paid.adSlotGate.accessFlags)
        for (packageName in listOf("com.andrewshu.android.reddit", "com.andrewshu.android.redditdonation", "renamed.rif")) {
            assertEquals("redditisfun/free", httpLibraryResourceNamespace("redditisfun", packageName, freeClasses))
            assertEquals("redditisfun/donation", httpLibraryResourceNamespace("redditisfun", packageName, paidClasses))
        }
    }

    @Test
    fun platinumAlwaysReturnsFalseWithoutPremiumOrSettingBranchesAndReapplicationDoesNotAccumulateCode() {
        val bindings = resolveRifAdBindings(stock(RifNativeSku.PLATINUM))
        var gate = bindings.adSlotGate.toMutable()
        repeat(2) {
            applyPlatinumAdSlotSafeguard(bindings) { _, replacement -> gate = replacement }
            val implementation = checkNotNull(gate.implementation)
            assertEquals(2, implementation.registerCount)
            val code = implementation.instructions.toList()
            assertEquals(listOf(Opcode.CONST_4, Opcode.RETURN), code.map { it.opcode })
            assertEquals(0L, (code.first() as WideLiteralInstruction).wideLiteral)
            assertEquals(AccessFlags.PROTECTED.value, gate.accessFlags)
        }
    }

    @Test
    fun genuineFreeBuildIsValidatedButNeverEvenRequestsAMutableGate() {
        val bindings = resolveRifAdBindings(stock(RifNativeSku.FREE))
        val original = checkNotNull(bindings.adSlotGate.implementation).instructions.map { it.opcode }
        applyPlatinumAdSlotSafeguard(bindings) { _, _ ->
            fail("Free native ads must not be modified by the paid safeguard")
        }
        assertEquals(original, checkNotNull(bindings.adSlotGate.implementation).instructions.map { it.opcode })
    }

    @Test
    fun rejectsMissingAndConflictingNativeMarkers() {
        assertFailsWith<IllegalArgumentException> { resolveRifAdBindings(emptyList()) }
        val paid = stock(RifNativeSku.PLATINUM)
        val free = stock(RifNativeSku.FREE)
        val adClasses = free.filter { it.type.startsWith("Lcom/andrewshu/android/reddit/ads/") }
        assertFailsWith<IllegalArgumentException> { resolveRifAdBindings(paid + adClasses) }
        assertFailsWith<IllegalArgumentException> {
            resolveRifAdBindings(free.filterNot { it.type == "Lcom/andrewshu/android/reddit/ads/AdViewHelper;" })
        }
        assertFailsWith<IllegalArgumentException> {
            resolveRifAdBindings(paid + free.filter { it.type in setOf("Le5/e0;", "Lb5/y0;") })
        }
    }

    @Test
    fun rejectsChangedDispatchOrBodyMissingInsertionGateAndUnexpectedOverrides() {
        val paid = stock(RifNativeSku.PLATINUM)
        val bindings = resolveRifAdBindings(paid)
        assertFailsWith<IllegalArgumentException> {
            resolveRifAdBindings(replaceMethod(paid, "Lf5/e0;", "d0") {
                copyMethod(it, flags = it.accessFlags or AccessFlags.STATIC.value)
            })
        }
        assertFailsWith<IllegalArgumentException> {
            resolveRifAdBindings(replaceMethod(paid, "Lf5/e0;", "d0") {
                copyMethod(it, implementation = null)
            })
        }
        assertFailsWith<IllegalArgumentException> {
            resolveRifAdBindings(replaceMethod(paid, "Lc5/y0;", "b0") {
                copyMethod(it, implementation = null)
            })
        }
        val bypass = ImmutableClassDef(
            "Lfixture/UnexpectedFeed;", AccessFlags.PUBLIC.value, "Lc5/y0;", emptyList(),
            null, emptyList(), emptyList(), listOf(ImmutableMethod(
                "Lfixture/UnexpectedFeed;", "d0", emptyList(), "Z", AccessFlags.PROTECTED.value,
                emptySet(), emptySet(), bindings.adSlotGate.implementation,
            )),
        )
        assertFailsWith<IllegalArgumentException> { resolveRifAdBindings(paid + bypass) }
    }

    @Test
    fun everyPaidCompatibleRifFeatureIncludesTheSameHiddenUnconditionalSafeguard() {
        val features = listOf(
            redditApiPatch, legacySpoofClientPatch, spoofClientPatch, imgurAlbumsPatch,
            hookExceptionHandler, fixRedditApiLookups, fixFailedGalleryImagesPatch, fixEscapedUrlsPatch,
            modifyLoginWebView, fixMoreComments, fixRedgifsApi, rifMorpheSettingsPatch,
        )
        fun dependencies(vararg roots: Patch<*>) = buildSet {
            fun visit(patch: Patch<*>) {
                if (add(patch)) patch.dependencies.forEach(::visit)
            }
            roots.forEach(::visit)
        }
        for (feature in features) {
            assertTrue(platinumAdSlotSafeguardPatch in dependencies(feature), feature.name)
        }
        assertEquals(1, dependencies(*features.toTypedArray()).count { it === platinumAdSlotSafeguardPatch })
        assertTrue(platinumAdSlotSafeguardPatch in ExtensionPatches.RIF.dependencies)
        assertNull(platinumAdSlotSafeguardPatch.name)
        assertFalse(platinumAdSlotSafeguardPatch.default)
        assertTrue(platinumAdSlotSafeguardPatch.options.isEmpty())
        assertTrue(platinumAdSlotSafeguardPatch.dependencies.isEmpty())
    }

    private fun copyMethod(
        method: Method,
        flags: Int = method.accessFlags,
        implementation: com.android.tools.smali.dexlib2.iface.MethodImplementation? = method.implementation,
    ) = ImmutableMethod(
        method.definingClass, method.name, method.parameters, method.returnType, flags,
        method.annotations, method.hiddenApiRestrictions, implementation,
    )

    private fun replaceMethod(
        classes: List<ClassDef>,
        owner: String,
        name: String,
        replacement: (Method) -> Method,
    ): List<ClassDef> = classes.map { cls ->
        if (cls.type != owner) cls else ImmutableClassDef(
            cls.type, cls.accessFlags, cls.superclass, cls.interfaces, cls.sourceFile, cls.annotations,
            cls.fields, cls.methods.map { if (it.name == name) replacement(it) else it },
        )
    }
}
