/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun

import app.morphe.patches.reddit.customclients.redditisfun.fix.commentvideo.resolveRifCommentVideoTargets
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommentVideoTargetsTest {
    private fun stock(name: String): List<ClassDef> {
        val path = System.getenv(name)
        assumeTrue("Set $name to the original RIF 5.6.22 APK", path != null)
        val dex = DexFileFactory.loadDexContainer(File(checkNotNull(path)), null)
        return dex.dexEntryNames.flatMap { checkNotNull(dex.getEntry(it)).dexFile.classes }
    }

    @Test
    fun bindsBothPostResolutionPathsAndNativeVideoSettersInEachSku() {
        for ((environment, prefix) in listOf("RIF_FREE_APK" to "Lj2/", "RIF_PLATINUM_APK" to "Lk2/")) {
            val targets = resolveRifCommentVideoTargets(stock(environment))
            assertEquals(listOf("${prefix}a\$b;", "${prefix}c\$b;"),
                targets.callbacks.map { it.method.definingClass })
            assertEquals(listOf("h0", "f0"), targets.callbacks.map { it.method.name })
            assertEquals(if (environment == "RIF_FREE_APK") listOf("k", "o", "r") else listOf("l", "p", "t"),
                listOf(targets.dashSetter, targets.hlsSetter, targets.gifSetter).map { it.name })
        }
    }

    @Test
    fun rejectsMissingVideoTypesAndChangedSetterDispatch() {
        for (environment in listOf("RIF_FREE_APK", "RIF_PLATINUM_APK")) {
            val original = stock(environment)
            val targets = resolveRifCommentVideoTargets(original)
            val owner = targets.dashSetter.definingClass
            assertFailsWith<IllegalStateException> {
                resolveRifCommentVideoTargets(original.filterNot { it.type == owner })
            }
            val changed = original.map { cls ->
                if (cls.type != owner) cls else ImmutableClassDef(
                    cls.type, cls.accessFlags, cls.superclass, cls.interfaces, cls.sourceFile, cls.annotations,
                    cls.fields, cls.methods.map { method ->
                        if (method.name != targets.dashSetter.name) method else ImmutableMethod(
                            method.definingClass, method.name, method.parameters, method.returnType,
                            method.accessFlags or AccessFlags.STATIC.value, method.annotations,
                            method.hiddenApiRestrictions, method.implementation,
                        )
                    },
                )
            }
            assertFailsWith<IllegalArgumentException> { resolveRifCommentVideoTargets(changed) }
        }
    }
}
