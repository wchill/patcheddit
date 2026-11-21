/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.bindings

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal enum class RifNativeSku { FREE, PLATINUM }

internal data class RifAdBindings(val sku: RifNativeSku, val adSlotGate: Method)

private const val AD_HELPER = "Lcom/andrewshu/android/reddit/ads/AdViewHelper;"
private const val AD_CONTRACT = "Lcom/andrewshu/android/reddit/ads/IAdViewHelper;"
private const val AD_ROW = "Lcom/andrewshu/android/reddit/things/objects/NativeAdThreadThing;"
private const val ACTIVITY = "Lcom/andrewshu/android/reddit/theme/BaseThemedActivity;"
private const val THEME = "Lcom/andrewshu/android/reddit/theme/manifest/ThemeManifest;"
private const val HOLDER = "Landroidx/recyclerview/widget/RecyclerView\$d0;"
private const val ERROR = "Unsupported or ambiguous RIF native ad-slot integration. " +
        "Use an unmodified RIF 5.6.22 free or Golden Platinum APK."

private data class NativeLayout(
    val sku: RifNativeSku,
    val feed: String,
    val parser: String,
    val preferences: String,
    val premium: String,
    val activityTheme: String,
    val themeAds: String,
)

private val layouts = listOf(
    NativeLayout(RifNativeSku.FREE, "Le5/e0;", "Lb5/y0;", "Lk4/v;", "Lo5/i0;", "Q0", "E"),
    NativeLayout(RifNativeSku.PLATINUM, "Lf5/e0;", "Lc5/y0;", "Ll4/v;", "Lp5/i0;", "R0", "G"),
)

private fun Method.hasPrototype(name: String, parameters: List<String>, returns: String) =
    this.name == name && parameterTypes.map(CharSequence::toString) == parameters && returnType == returns

private fun Method.references() =
    (implementation?.instructions ?: emptyList()).mapNotNull { (it as? ReferenceInstruction)?.reference }

private fun MethodReference.isAdConstructor() =
    definingClass == AD_ROW && name == "<init>" && parameterTypes.isEmpty() && returnType == "V"

private fun nativeLayout(classes: Map<String, ClassDef>): NativeLayout {
    val layout = layouts.filter { it.feed in classes || it.parser in classes }.singleOrNull()
    require(layout != null) { ERROR }
    require(layout.feed in classes && layout.parser in classes) { ERROR }
    val helper = classes[AD_HELPER]
    val contract = classes[AD_CONTRACT]
    if (layout.sku == RifNativeSku.PLATINUM) {
        require(helper == null && contract == null) { ERROR }
    } else {
        require(helper != null && contract != null && AD_CONTRACT in helper.interfaces &&
                AccessFlags.INTERFACE.isSet(contract.accessFlags)) { ERROR }
        val bind = helper.methods.singleOrNull {
            it.hasPrototype("onBindNativeAdViewHolder", listOf(HOLDER, AD_ROW, "Z"), "V")
        }
        val create = helper.methods.singleOrNull {
            it.hasPrototype("onCreateNativeAdViewHolder", listOf("Landroid/view/ViewGroup;", "I", "Z"), HOLDER)
        }
        require(listOf(bind, create).all {
            it != null && it.accessFlags == AccessFlags.PUBLIC.value && it.implementation != null
        }) { ERROR }
    }
    return layout
}

// Detection remains valid after the paid gate has been rewritten.
internal fun resolveRifNativeSku(definitions: List<ClassDef>): RifNativeSku =
    nativeLayout(definitions.associateBy { it.type }).sku

internal fun rifHttpLibraryResourceNamespace(definitions: List<ClassDef>): String =
    when (resolveRifNativeSku(definitions)) {
        RifNativeSku.FREE -> "redditisfun/free"
        RifNativeSku.PLATINUM -> "redditisfun/donation"
    }

// These bindings were independently traced from the two stock 5.6.22 APKs, not their manifests.
internal fun resolveRifAdBindings(definitions: List<ClassDef>): RifAdBindings {
    val classes = definitions.associateBy { it.type }
    require(classes.size == definitions.size) { ERROR }
    val layout = nativeLayout(classes)
    val feed = classes.getValue(layout.feed)
    val parser = classes.getValue(layout.parser)
    require(feed.superclass == parser.type) { ERROR }
    val gate = feed.methods.singleOrNull { it.hasPrototype("d0", emptyList(), "Z") }
    val baseGate = parser.methods.singleOrNull { it.hasPrototype("d0", emptyList(), "Z") }
    val listing = parser.methods.singleOrNull {
        it.hasPrototype("b0", listOf("Ljava/io/InputStream;"), "Ljava/util/List;")
    }
    require(gate != null && baseGate != null && listing != null &&
            listOf(gate, baseGate, listing).all { it.accessFlags == AccessFlags.PROTECTED.value }) { ERROR }

    val expectedReferences = listOf(
        "${layout.preferences}->C()${layout.preferences}",
        "${layout.feed}->A:Ljava/lang/ref/WeakReference;",
        "Ljava/lang/ref/Reference;->get()Ljava/lang/Object;",
        ACTIVITY,
        "${layout.preferences}->t0()Z",
        "${layout.preferences}->U0()Z",
        "${layout.premium}->a()Z",
        "$ACTIVITY->${layout.activityTheme}()$THEME",
        "$ACTIVITY->${layout.activityTheme}()$THEME",
        "$THEME->${layout.themeAds}()Z",
    )
    val expectedOpcodes = listOf(
        Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT_OBJECT, Opcode.IGET_OBJECT, Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT, Opcode.CHECK_CAST, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT,
        Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.CONST_4, Opcode.CONST_4, Opcode.IF_EQZ,
        Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.CONST_4, Opcode.GOTO,
        Opcode.CONST_4, Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT,
        Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.CONST_4, Opcode.GOTO, Opcode.CONST_4,
        Opcode.IF_EQZ, Opcode.IF_NEZ, Opcode.IF_NEZ, Opcode.GOTO, Opcode.CONST_4, Opcode.RETURN,
    )
    require(gate.implementation?.registerCount == 7 &&
            gate.implementation?.instructions?.map { it.opcode } == expectedOpcodes &&
            gate.references().map { it.toString() } == expectedReferences) { ERROR }
    val baseCode = baseGate.implementation?.instructions?.toList().orEmpty()
    require(baseCode.map { it.opcode } == listOf(Opcode.CONST_4, Opcode.RETURN) &&
            (baseCode.first() as WideLiteralInstruction).wideLiteral == 0L &&
            (baseCode.first() as OneRegisterInstruction).registerA ==
            (baseCode.last() as OneRegisterInstruction).registerA) { ERROR }

    // The parser must guard its only ad-row allocation with this virtual gate.
    val constructors = definitions.flatMap { it.methods }.filter { method ->
        method.references().filterIsInstance<MethodReference>().any { it.isAdConstructor() }
    }
    require(constructors.size == 1 && constructors.single() == listing) { ERROR }
    val code = listing.implementation?.instructions?.toList().orEmpty()
    val gateIndex = code.indices.singleOrNull { index ->
        val reference = (code[index] as? ReferenceInstruction)?.reference as? MethodReference
        code[index].opcode == Opcode.INVOKE_VIRTUAL && reference?.definingClass == parser.type &&
                reference.name == gate.name && reference.parameterTypes.isEmpty() && reference.returnType == "Z"
    }
    require(gateIndex != null && gateIndex + 2 < code.size) { ERROR }
    val result = code[gateIndex + 1]
    val branch = code[gateIndex + 2]
    val constructorIndex = code.indices.single {
        ((code[it] as? ReferenceInstruction)?.reference as? MethodReference)?.isAdConstructor() == true
    }
    val branchTarget = code.take(gateIndex + 2).sumOf { it.codeUnits } +
            ((branch as? OffsetInstruction)?.codeOffset ?: 0)
    val constructorAddress = code.take(constructorIndex).sumOf { it.codeUnits }
    require(result.opcode == Opcode.MOVE_RESULT && branch.opcode == Opcode.IF_EQZ &&
            (result as OneRegisterInstruction).registerA == (branch as OneRegisterInstruction).registerA &&
            (code[gateIndex] as FiveRegisterInstruction).registerCount == 1 &&
            gateIndex < constructorIndex && branchTarget > constructorAddress) { ERROR }
    // Reject additional overrides: a second feed path could otherwise bypass the correction.
    val family = mutableSetOf(parser.type)
    do {
        val previousSize = family.size
        definitions.filter { it.superclass in family }.forEach { family += it.type }
    } while (family.size != previousSize)
    require(definitions.filter { it.type in family }.flatMap { it.methods }.filter {
        it.hasPrototype("d0", emptyList(), "Z")
    }.toSet() == setOf(baseGate, gate)) { ERROR }

    return RifAdBindings(layout.sku, gate)
}
