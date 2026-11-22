/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.platinum

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifAdBindings
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifAdBindings
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

internal fun applyPlatinumAdSlotSafeguard(
    bindings: RifAdBindings,
    replaceGate: (Method, MutableMethod) -> Unit,
) {
    if (bindings.sku == RifNativeSku.FREE) return
    val original = bindings.adSlotGate
    val replacement = ImmutableMethod(
        original.definingClass, original.name, original.parameters, original.returnType,
        original.accessFlags, original.annotations, original.hiddenApiRestrictions, MutableMethodImplementation(2),
    ).toMutable().apply {
        addInstructions(0, """
            const/4 v0, 0x0
            return v0
        """)
    }
    replaceGate(original, replacement)
}

internal val platinumAdSlotSafeguardPatch = patch(default = false) {
    execute {
        val bindings = resolveRifAdBindings(buildList { classDefForEach(::add) })
        applyPlatinumAdSlotSafeguard(bindings) { target, replacement ->
            val owner = mutableClassDefBy(target.definingClass)
            val original = owner.methods.single {
                it.name == target.name && it.parameterTypes.isEmpty() && it.returnType == "Z"
            }
            check(owner.methods.remove(original))
            check(owner.methods.add(replacement))
        }
    }
}
