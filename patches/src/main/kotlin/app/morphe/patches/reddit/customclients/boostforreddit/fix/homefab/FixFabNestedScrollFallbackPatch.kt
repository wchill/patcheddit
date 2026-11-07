/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from https://github.com/brealorg/breal-morphe-patches (0226c2e).
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import app.morphe.patches.all.misc.PatchCategory

private const val FAB_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_NESTED_SCROLL_FALLBACK_ISSUE97_V1"
private const val FAB_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_NESTED_PRE_SCROLL_ISSUE97_V2"
private const val FAB_MENU_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_MENU_NESTED_SCROLL_FALLBACK_ISSUE97_V3"
private const val FAB_MENU_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_MENU_NESTED_PRE_SCROLL_ISSUE97_V3"
private const val FAB_MINI_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_MINI_NESTED_SCROLL_FALLBACK_ISSUE97_V3"
private const val FAB_MINI_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_MINI_NESTED_PRE_SCROLL_ISSUE97_V3"

private const val VIEW = "Landroid/view/View;"
private val FLOATING_ACTION_BUTTON =
    BoostApi.FloatingActionButton.type
private val FLOATING_ACTION_MENU =
    BoostApi.FloatingActionMenu.type
private data class ScrollAwareTarget(
    val fingerprint: Fingerprint,
    val childType: String,
    val nestedScrollMarker: String,
    val preScrollMarker: String,
)

@Suppress("unused")
val fixFabNestedScrollFallbackPatch = morpheBytecodePatch(
    name = "Fix Boost FAB nested scroll",
    description =
        "Hides and shows the floating action button in step with scrolling, also on pages with a collapsing header.",
    default = true,
    extensionPatch = ExtensionPatches.Boost,
    category = PatchCategory.NAVIGATION,
) { setting ->
    compatibleWith(*AppCompatibility.Boost)

    execute {
        val preScroll = BoostApi.CoordinatorLayoutBehavior.onNestedPreScroll
        val preScrollParameterTypes = preScroll.parameters

        val scrollAwareTargets = listOf(
            ScrollAwareTarget(
                fabNestedScrollFingerprint,
                FLOATING_ACTION_BUTTON,
                FAB_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_NESTED_PRE_SCROLL_MARKER,
            ),
            ScrollAwareTarget(
                fabMenuNestedScrollFingerprint,
                FLOATING_ACTION_MENU,
                FAB_MENU_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_MENU_NESTED_PRE_SCROLL_MARKER,
            ),
            ScrollAwareTarget(
                fabMiniNestedScrollFingerprint,
                FLOATING_ACTION_BUTTON,
                FAB_MINI_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_MINI_NESTED_PRE_SCROLL_MARKER,
            ),
        )

        scrollAwareTargets.forEach { target ->
            setting.patchMethod(target.fingerprint) {
                // All three native Boost implementations only react to
                // dyConsumed (p5). At a scroll edge, the return delta may be
                // reported as dyUnconsumed (p7), so use it only when p5 is 0.
                addInstructionsWithLabels(
                    0,
                    """
                        if-nez p5, :morphe_fab_scroll_delta_ready
                        move p5, p7
                        :morphe_fab_scroll_delta_ready
                        const-string p3, "${target.nestedScrollMarker}"
                    """,
                )
            }

            val behaviorClass = target.fingerprint.classDef
            val nestedScroll = target.fingerprint.method
            check(
                behaviorClass.methods.none { method ->
                    method.name == preScroll.name &&
                        method.returnType == "V" &&
                        method.parameters.map { it.type } == preScrollParameterTypes
                },
            ) {
                "Boost pre-scroll override already exists for ${behaviorClass.type}"
            }

            behaviorClass.methods.add(
                ImmutableMethod(
                    behaviorClass.type,
                    preScroll.name,
                    preScrollParameterTypes.map { type ->
                        ImmutableMethodParameter(type, null, null)
                    },
                    "V",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(8),
                ).toMutable().apply {
                    // Collapsing headers consume motion before nested scroll.
                    // Forward pre-scroll dx/dy to each component's own native
                    // implementation so its original animation/state policy
                    // remains authoritative across every Boost surface.
                    addInstructions(
                        0,
                        """
                            invoke-super/range {p0 .. p7}, $preScroll
                            ${setting.checkEnabled("p6", "morphe_native_pre_scroll")}
                            check-cast p2, ${target.childType}
                            const/4 p6, 0x0
                            const/4 p7, 0x0
                            invoke-virtual/range {p0 .. p7}, $nestedScroll
                            const-string p3, "${target.preScrollMarker}"
                            :morphe_native_pre_scroll
                            return-void
                        """,
                    )
                },
            )
        }
    }
}
