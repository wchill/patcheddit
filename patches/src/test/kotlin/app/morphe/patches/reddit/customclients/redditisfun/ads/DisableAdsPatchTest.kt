/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.MorphePatchSetting
import app.morphe.patches.reddit.customclients.PATCH_SETTINGS_DESCRIPTOR
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DisableAdsPatchTest {
    private val setting = MorphePatchSetting("morphe_patch_block_ads")
    private val manager = "${RIF_ADS_PACKAGE}RifNativeAdLoaderWaitListManager;"
    private val helper = "${RIF_ADS_PACKAGE}AdViewHelper;"
    private val album = "${RIF_ADS_PACKAGE}ImageAlbumAdViewHelper;"
    private val wrapper = "${RIF_ADS_PACKAGE}RifAppLovinImageAlbumRecyclerAdapter;"
    private val nativeLoad = "Lcom/applovin/mediation/nativeAds/MaxNativeAdLoader;->loadAd()V"
    private val premiumKey = "morphe_patch_fake_reddit_premium"

    @Test
    fun blocksEveryBoundaryAndRestoresNativeDispatchWithoutChangingPremium() {
        val classes = fixture().associateBy { it.type }
        val targets = resolveRifAdsTargets(classes.values.toList())
        val premium = classes.getValue("Lo5/i0;")
        MorphePatchSetting(premiumKey).patchMethod(premium, premium.methods.single()) {
            addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        }
        suppressRifAds(targets, setting, classes::getValue)
        val adapter = Any()
        val allTargets = targets.disabledGates + targets.disabledLoaders +
            listOf(targets.dequeueNativeAd, targets.albumAdapterFactory)
        for (blocking in listOf(true, false, true)) {
            for (fakePremium in listOf(false, true)) {
                val switches = mapOf(setting.key to blocking, premiumKey to fakePremium)
                for (target in allTargets) {
                    val machine = Machine(classes, switches)
                    val arguments = listOf(Any()) + target.parameterTypes.map {
                        if (it.toString() == RIF_RECYCLER_ADAPTER) adapter else Any()
                    }
                    val result = machine.run(target, arguments)
                    if (blocking) {
                        assertTrue(machine.nativeCalls.isEmpty(), target.toString())
                        when {
                            target in targets.disabledGates -> assertEquals(0, result)
                            target.sameAdsMethod(targets.albumAdapterFactory) -> assertSame(adapter, result)
                            else -> assertEquals(null, result)
                        }
                    } else {
                        assertFalse(machine.nativeCalls.isEmpty(), target.toString())
                        when {
                            target in targets.disabledGates -> {
                                assertTrue(machine.nativeCalls.any { "Lk4/v;->t0()Z" == it })
                                assertEquals(if (fakePremium) 1 else 0, result)
                            }
                            target.sameAdsMethod(targets.albumAdapterFactory) -> {
                                assertTrue("new:$wrapper" in machine.nativeCalls)
                                assertFalse(result === adapter)
                            }
                            else -> assertTrue(machine.nativeCalls.any { it.endsWith("->loadAd()V") })
                        }
                    }
                    assertEquals(if (fakePremium) 1 else 0,
                        Machine(classes, switches).run(premium.methods.single { it.name == "a" }, emptyList()))
                }
            }
        }
    }

    @Test
    fun rejectsMissingOrChangedTargetsInsteadOfPartiallyPatching() {
        val classes = fixture()
        assertFailsWith<IllegalArgumentException> { resolveRifAdsTargets(classes.filter { it.type != album }) }
        assertFailsWith<IllegalArgumentException> { resolveRifAdsTargets(classes + classes.single { it.type == helper }) }
        val changed = fixture()
        val owner = changed.single { it.type == album }
        owner.methods.removeAll { it.name == "newRecyclerAdapter" }
        owner.methods.add(method(album, "newRecyclerAdapter",
            listOf("Landroid/app/Activity;", RIF_RECYCLER_ADAPTER), RIF_RECYCLER_ADAPTER,
            body = "return-object p2"))
        assertFailsWith<IllegalArgumentException> { resolveRifAdsTargets(changed) }
    }

    @Test
    fun validatesAndPreservesStockFreeImplementationsAndExcludesPlatinum() {
        val free = System.getenv("RIF_FREE_APK")
        val paid = System.getenv("RIF_PLATINUM_APK")
        assumeTrue("Set RIF_FREE_APK and RIF_PLATINUM_APK to the stock RIF 5.6.22 APKs", free != null && paid != null)
        fun definitions(path: String): List<ClassDef> {
            val dex = DexFileFactory.loadDexContainer(File(path), null)
            return dex.dexEntryNames.flatMap { checkNotNull(dex.getEntry(it)).dexFile.classes }
        }
        val nativeDefinitions = definitions(checkNotNull(free))
        val targets = resolveRifAdsTargets(nativeDefinitions)
        val originals = targets.disabledGates + targets.disabledLoaders +
            listOf(targets.dequeueNativeAd, targets.albumAdapterFactory)
        val classes = nativeDefinitions.filter { cls -> originals.any { it.definingClass == cls.type } }
            .associate { it.type to MutableClass(it) }
        suppressRifAds(targets, setting, classes::getValue)
        for (original in originals) {
            val native = classes.getValue(original.definingClass).methods.single {
                it.name.startsWith("morpheNative_${original.name}_")
            }
            val originalInstructions = checkNotNull(original.implementation).instructions.toList()
            val preserved = checkNotNull(native.implementation).instructions.toList()
            assertEquals(originalInstructions.map { it.opcode }, preserved.map { it.opcode })
            assertEquals(originalInstructions.map { (it as? ReferenceInstruction)?.reference?.toString() },
                preserved.map { (it as? ReferenceInstruction)?.reference?.toString() })
            assertEquals(originalInstructions.map { (it as? OffsetInstruction)?.codeOffset },
                preserved.map { (it as? OffsetInstruction)?.codeOffset })
            assertEquals(original.implementation!!.registerCount, native.implementation!!.registerCount)
            val machine = Machine(classes, mapOf(setting.key to true))
            val adapter = Any()
            val result = machine.run(original, listOf(Any()) + original.parameterTypes.map { adapter })
            assertTrue(machine.nativeCalls.isEmpty(), original.toString())
            if (original.sameAdsMethod(targets.albumAdapterFactory)) assertSame(adapter, result)
        }
        assertEquals(listOf(AppCompatibility.RedditIsFunFree), disableAdsPatch.compatibility)
        assertFailsWith<IllegalArgumentException> { resolveRifAdsTargets(definitions(checkNotNull(paid))) }
    }

    private fun fixture(): List<MutableClass> {
        val eligibility = """
            const/4 v1, 0x0
            invoke-virtual {v1}, Lk4/v;->t0()Z
            move-result v0
            invoke-virtual {v1}, Lk4/v;->U0()Z
            move-result v0
            invoke-static {}, Lo5/i0;->a()Z
            move-result v0
            return v0
        """
        val load = "invoke-virtual {p0}, $nativeLoad\nreturn-void"
        return listOf(
            cls("Lo5/i0;", listOf(method("Lo5/i0;", "a", result = "Z",
                flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, body = "const/4 v0, 0x0\nreturn v0"))),
            cls("Lb5/y0;", listOf(method("Lb5/y0;", "b0", listOf("Ljava/io/InputStream;"), "Ljava/util/List;",
                AccessFlags.PROTECTED.value, """
                    invoke-virtual {p0}, Lb5/y0;->d0()Z
                    move-result v0
                    new-instance v0, Lcom/andrewshu/android/reddit/things/objects/NativeAdThreadThing;
                    const/4 v0, 0x0
                    return-object v0
                """))),
            cls("Le5/e0;", listOf(method("Le5/e0;", "d0", result = "Z",
                flags = AccessFlags.PROTECTED.value, body = eligibility)), "Lb5/y0;"),
            cls("Lu1/b;", listOf(method("Lu1/b;", "b", listOf("Lcom/andrewshu/android/reddit/MainActivity;"),
                result = "Z", flags = AccessFlags.PRIVATE.value, body = eligibility))),
            *listOf(helper, album).map { owner ->
                cls(owner, listOf(
                    method(owner, "isAdsEnabledAndUnblocked", result = "Z",
                        flags = AccessFlags.PRIVATE.value, body = eligibility),
                    method(owner, "checkConditionsAndLoadInitialAds", flags = AccessFlags.PRIVATE.value, body = """
                        invoke-virtual {p0, p0}, $manager->loadAds(Landroid/app/Activity;)V
                        return-void
                    """),
                ) + if (owner == album) listOf(method(album, "newRecyclerAdapter",
                    listOf("Landroid/app/Activity;", RIF_RECYCLER_ADAPTER), RIF_RECYCLER_ADAPTER, body = """
                        new-instance v0, $wrapper
                        invoke-direct {v0, p0, p2}, $wrapper-><init>($album$RIF_RECYCLER_ADAPTER)V
                        return-object v0
                    """)) else emptyList())
            }.toTypedArray(),
            cls(manager, listOf(
                method(manager, "loadAds", listOf("Landroid/app/Activity;"), body = load),
                method(manager, "dequeueAd", result = "Ljava/lang/Object;", body = """
                    invoke-virtual {p0}, $nativeLoad
                    const/4 v0, 0x0
                    return-object v0
                """),
            )),
            cls("${RIF_ADS_PACKAGE}RifNativeAdLoaderWaitListManager\$RetryLoadRunnable;", listOf(
                method("${RIF_ADS_PACKAGE}RifNativeAdLoaderWaitListManager\$RetryLoadRunnable;", "run", body = load))),
            cls("${RIF_ADS_PACKAGE}BannerAdViewHelper;", listOf(
                method("${RIF_ADS_PACKAGE}BannerAdViewHelper;", "loadAd", listOf("Landroid/view/View;", "Z"),
                    body = "invoke-virtual {p1}, Lcom/applovin/mediation/ads/MaxAdView;->loadAd()V\nreturn-void"))),
        )
    }

    private fun method(
        owner: String,
        name: String,
        parameters: List<String> = emptyList(),
        result: String = "V",
        flags: Int = AccessFlags.PUBLIC.value,
        body: String,
    ): MutableMethod = ImmutableMethod(owner, name,
        parameters.map { ImmutableMethodParameter(it, null, null) }, result, flags,
        null, null, MutableMethodImplementation(10),
    ).toMutable().apply { addInstructions(0, body) }

    private fun cls(type: String, methods: List<Method>, parent: String = "Ljava/lang/Object;") =
        MutableClass(ImmutableClassDef(type, AccessFlags.PUBLIC.value, parent,
            emptyList(), null, emptyList(), emptyList(), methods))

    private class Machine(
        private val classes: Map<String, MutableClass>,
        private val switches: Map<String, Boolean>,
    ) {
        val nativeCalls = mutableListOf<String>()

        fun run(signature: MethodReference, arguments: List<Any?>): Any? {
            if (signature.definingClass == PATCH_SETTINGS_DESCRIPTOR) {
                return if (switches.getValue(signature.name.removePrefix("isEnabled_"))) 1 else 0
            }
            val method = classes[signature.definingClass]?.methods?.singleOrNull { it.sameAdsMethod(signature) }
            if (method == null) {
                nativeCalls += signature.toString()
                return if (signature.returnType == "Z") 1 else null
            }
            val implementation = checkNotNull(method.implementation)
            val registers = arrayOfNulls<Any>(implementation.registerCount)
            arguments.forEachIndexed { index, value ->
                registers[registers.size - arguments.size + index] = value
            }
            val code = implementation.instructions.toList()
            var address = 0
            val addresses = code.map { instruction -> address.also { address += instruction.codeUnits } }
            var result: Any? = null
            var index = 0
            repeat(1000) {
                val instruction = code[index]
                val register = (instruction as? OneRegisterInstruction)?.registerA
                when (instruction.opcode) {
                    Opcode.CONST_4 -> registers[checkNotNull(register)] =
                        (instruction as NarrowLiteralInstruction).narrowLiteral
                    Opcode.NEW_INSTANCE -> {
                        nativeCalls += "new:${(instruction as ReferenceInstruction).reference}"
                        registers[checkNotNull(register)] = Any()
                    }
                    Opcode.INVOKE_STATIC, Opcode.INVOKE_DIRECT, Opcode.INVOKE_VIRTUAL,
                    Opcode.INVOKE_DIRECT_RANGE, Opcode.INVOKE_STATIC_RANGE -> {
                        val reference = (instruction as ReferenceInstruction).reference as MethodReference
                        val indices = when (instruction) {
                            is RegisterRangeInstruction ->
                                (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
                            is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD,
                                instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
                            else -> error("Unsupported invoke")
                        }
                        result = run(reference, indices.map { registers[it] })
                    }
                    Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT -> registers[checkNotNull(register)] = result
                    Opcode.IF_EQZ -> if (registers[checkNotNull(register)] == null || registers[register] == 0) {
                        index = addresses.indexOf(addresses[index] + (instruction as OffsetInstruction).codeOffset)
                        return@repeat
                    }
                    Opcode.RETURN -> return registers[checkNotNull(register)]
                    Opcode.RETURN_OBJECT -> return registers[checkNotNull(register)].takeUnless { it == 0 }
                    Opcode.RETURN_VOID -> return null
                    else -> error("Unexpected opcode ${instruction.opcode} in $method")
                }
                index++
            }
            error("Method did not return: $method")
        }
    }
}
