/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.boostforreddit

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostBindings
import app.morphe.patches.reddit.customclients.boostforreddit.fix.morecomments.fixMoreComments
import app.morphe.patches.reddit.customclients.boostforreddit.fix.profileabout.fixProfileAboutScrollingPatch
import app.morphe.patches.reddit.customclients.fixMoreCommentsPatch
import app.morphe.patches.reddit.customclients.misc.extension.mapping.FieldId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.MethodId
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
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
        assertEquals("1.14.4", version)
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

        val widgetMethod = native.asSequence().flatMap { it.methods.asSequence() }
            .single { BoostApi.UpdateImageWidget.imageWidgetProviderUpdateWithSubmission.matches(it) }
        val instructions = checkNotNull(widgetMethod.implementation).instructions.toList()
        val pendingIntentCall = instructions.withIndex().single { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference?.toString() ==
                "Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"
        }
        val flagOr = instructions[pendingIntentCall.index - 1]
        val flagConstant = instructions[pendingIntentCall.index - 2]
        assertEquals(Opcode.OR_INT_2ADDR, flagOr.opcode)
        assertEquals(0x08000000L, (flagConstant as WideLiteralInstruction).wideLiteral)
        assertEquals((flagConstant as OneRegisterInstruction).registerA, (flagOr as TwoRegisterInstruction).registerB)
        assertEquals((pendingIntentCall.value as FiveRegisterInstruction).registerF, flagOr.registerA)

        val redgifsHelper = native.single { it.type == BoostApi.Utils.type }
        val redgifsInitializer = redgifsHelper.methods.single { it.name == "<clinit>" }
        assertTrue(checkNotNull(redgifsInitializer.implementation).instructions.any {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "[?&]boost_expires=(\\d+)"
        })
        val redgifsReader = redgifsHelper.methods.single {
            it.name == "w" && it.parameterTypes.singleOrNull() == "Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;"
        }
        val strippingCalls = listOf(
            "Ljava/util/regex/Matcher;->start()I",
            "Ljava/lang/String;->substring(II)Ljava/lang/String;",
            "Ljava/util/regex/Matcher;->end()I",
            "Ljava/lang/String;->substring(I)Ljava/lang/String;",
            "Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;",
        )
        assertEquals(strippingCalls, checkNotNull(redgifsReader.implementation).instructions
            .mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
            .filter { it in strippingCalls })

        val avatarClass = native.single { it.type == BoostApi.ProfileAvatarDrawable.type }
        val avatarConstructor = avatarClass.methods.single { BoostApi.ProfileAvatarDrawable.construct.matches(it) }
        val avatarDraw = avatarClass.methods.single { it.name == "draw" && it.parameterTypes.singleOrNull() == "Landroid/graphics/Canvas;" }
        fun referencesAvatarBitmap(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction) =
            ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                ?.let(BoostApi.ProfileAvatarDrawable.bitmap::matches) == true
        assertTrue(checkNotNull(avatarConstructor.implementation).instructions.any {
            it.opcode == Opcode.IPUT_OBJECT && referencesAvatarBitmap(it)
        })
        assertTrue(checkNotNull(avatarDraw.implementation).instructions.any {
            it.opcode == Opcode.IGET_OBJECT && referencesAvatarBitmap(it)
        })
        assertTrue(checkNotNull(avatarDraw.implementation).instructions.any {
            (it as? ReferenceInstruction)?.reference?.toString() ==
                "Landroid/graphics/Canvas;->drawBitmap(Landroid/graphics/Bitmap;Landroid/graphics/Rect;Landroid/graphics/Rect;Landroid/graphics/Paint;)V"
        })

        val avatarSetter = native.asSequence().flatMap { it.methods.asSequence() }
            .single { BoostApi.DrawerActivity.setProfileAvatar.matches(it) }
        val avatarSetterReferences = checkNotNull(avatarSetter.implementation).instructions
            .mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.toSet()
        assertTrue("Landroid/view/MenuItem;->setIcon(Landroid/graphics/drawable/Drawable;)Landroid/view/MenuItem;" in avatarSetterReferences)
        assertTrue("Landroid/view/MenuItem;->setIcon(I)Landroid/view/MenuItem;" in avatarSetterReferences)
    }
}
