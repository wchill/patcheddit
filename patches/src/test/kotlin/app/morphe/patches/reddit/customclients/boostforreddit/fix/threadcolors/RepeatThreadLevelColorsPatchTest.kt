/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.threadcolors

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatThreadLevelColorsPatchTest {
    private fun lookup() = ImmutableMethod(
        "Ltest/IndentView;", "color",
        listOf("I", "[I").map { ImmutableMethodParameter(it, emptySet(), null) }, "I",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
        ImmutableMethodImplementation(4, listOf(
            ImmutableInstruction12x(Opcode.ARRAY_LENGTH, 0, 3),
            ImmutableInstruction22t(Opcode.IF_GE, 2, 0, 5),
            ImmutableInstruction23x(Opcode.AGET, 1, 3, 2),
            ImmutableInstruction10t(Opcode.GOTO, 2),
            ImmutableInstruction11n(Opcode.CONST_4, 1, 0),
            ImmutableInstruction11x(Opcode.RETURN, 1),
        ), emptyList(), emptyList()),
    ).toMutable().apply {
        repeatThreadLevelColors()
    }

    @Test
    fun repeatsAllTenColorsWithoutRepeatingTheTransparentRootEntry() {
        val method = lookup()
        val colors = intArrayOf(0, 0xffa00043.toInt(), 0xffd63e51.toInt(), 0xfff56c42.toInt(),
            0xfffcad62.toInt(), 0xffffe08b.toInt(), 0xffe8f698.toInt(), 0xffabdea3.toInt(),
            0xff69c2a6.toInt(), 0xff3587bd.toInt(), 0xff5f4fa2.toInt())
        val cycle = colors.drop(1)
        assertEquals(cycle + cycle + cycle, (1..30).map { color(method, colors, it) })
        assertEquals(colors[1], color(method, colors, 1001))
        assertEquals(colors[10], color(method, colors, 1000))
        assertEquals(colors[7], color(method, colors, Int.MAX_VALUE))
    }

    @Test
    fun usesCustomPaletteLengthsAndPreservesSingleColorIndicators() {
        val method = lookup()
        val colors = intArrayOf(0, 0xff112233.toInt(), 0xff445566.toInt(), 0xff778899.toInt())
        assertEquals(List(4) { colors.drop(1) }.flatten(), (1..12).map { color(method, colors, it) })
        for (level in listOf(1, 2, 10, 11, 1000)) {
            assertEquals(-1, color(method, intArrayOf(0, -1), level))
            assertEquals(-1, color(method, IntArray(10) { -1 }, level))
        }
    }

    @Test
    fun keepsTheTransparentFallbackWhenThereAreNoThreadColors() {
        val method = lookup()
        for (colors in listOf(intArrayOf(), intArrayOf(0))) {
            for (level in listOf(1, 10, 1000)) assertEquals(0, color(method, colors, level))
        }
    }

    @Test
    fun patchesTheOfficialDrawingLoopWithoutChangingItsDepthRegisterCountOrGeometry() {
        val path = System.getenv("BOOST_APK")
        assumeTrue("Set BOOST_APK to the official Boost APK", path != null)
        val container = DexFileFactory.loadDexContainer(File(checkNotNull(path)), null)
        val native = container.dexEntryNames.asSequence()
            .flatMap { checkNotNull(container.getEntry(it)).dexFile.classes.asSequence() }
            .flatMap { it.methods.asSequence() }.single { BoostApi.IndentView.onDraw.matches(it) }
        val original = checkNotNull(native.implementation)
        val method = native.toMutable()
        val unchanged = method.implementation!!.instructions.dropWhile { it.opcode != Opcode.AGET }.drop(3)
        method.repeatThreadLevelColors()
        assertEquals(original.registerCount, method.implementation!!.registerCount)
        val colors = intArrayOf(0, -1, -2, -3)
        assertEquals(List(20) { colors.drop(1) }.flatten(), (1..60).map { color(method, colors, it) })
        val patched = method.implementation!!.instructions.dropWhile { it.opcode != Opcode.AGET }.drop(3)
        assertEquals(unchanged, patched)
    }

    private fun color(method: MutableMethod, colors: IntArray, level: Int): Int {
        val implementation = checkNotNull(method.implementation)
        val instructions = implementation.instructions.toList()
        val start = instructions.indexOfFirst { it.opcode == Opcode.ARRAY_LENGTH }
        val length = instructions[start] as TwoRegisterInstruction
        val remainder = instructions.single { it.opcode == Opcode.REM_INT } as ThreeRegisterInstruction
        val lookup = instructions.first { it.opcode == Opcode.AGET } as ThreeRegisterInstruction
        val registers = arrayOfNulls<Any>(implementation.registerCount)
        registers[length.registerB] = colors
        registers[remainder.registerB] = level
        fun int(register: Int) = registers[register] as Int
        var index = start
        repeat(20) {
            val instruction = instructions[index]
            var next = index + 1
            when (instruction.opcode) {
                Opcode.ARRAY_LENGTH -> (instruction as TwoRegisterInstruction).let {
                    registers[it.registerA] = (registers[it.registerB] as IntArray).size
                }
                Opcode.ADD_INT_LIT8 -> (instruction as TwoRegisterInstruction).let {
                    registers[it.registerA] = int(it.registerB) + (instruction as NarrowLiteralInstruction).narrowLiteral
                }
                Opcode.REM_INT -> (instruction as ThreeRegisterInstruction).let {
                    registers[it.registerA] = int(it.registerB) % int(it.registerC)
                }
                Opcode.IF_LEZ, Opcode.IF_NEZ, Opcode.GOTO -> {
                    val branch = instruction as BuilderOffsetInstruction
                    val take = when (instruction.opcode) {
                        Opcode.IF_LEZ -> int((instruction as OneRegisterInstruction).registerA) <= 0
                        Opcode.IF_NEZ -> int((instruction as OneRegisterInstruction).registerA) != 0
                        else -> true
                    }
                    if (take) next = branch.target.location.index
                }
                Opcode.AGET -> (instruction as ThreeRegisterInstruction).let {
                    registers[it.registerA] = (registers[it.registerB] as IntArray)[int(it.registerC)]
                }
                Opcode.CONST_4 -> registers[(instruction as OneRegisterInstruction).registerA] =
                    (instruction as NarrowLiteralInstruction).narrowLiteral
                Opcode.RETURN, Opcode.IGET_OBJECT -> {
                    assertEquals(level, int(remainder.registerB), "Color wrapping changed the indentation depth")
                    return int(lookup.registerA)
                }
                else -> error("Unexpected color lookup instruction: ${instruction.opcode}")
            }
            index = next
        }
        error("Color lookup did not terminate")
    }
}
