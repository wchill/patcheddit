/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val RIF_ADS_PACKAGE = "Lcom/andrewshu/android/reddit/ads/"
internal const val RIF_RECYCLER_ADAPTER = "Landroidx/recyclerview/widget/RecyclerView\$h;"
private const val NATIVE_THREAD = "Lcom/andrewshu/android/reddit/things/objects/NativeAdThreadThing;"
private const val NATIVE_LOADER = "Lcom/applovin/mediation/nativeAds/MaxNativeAdLoader;"

internal data class RifAdsTargets(
    val slotGate: Method,
    val eligibilityGates: List<Method>,
    val initialLoaders: List<Method>,
    val nativeLoader: Method,
    val dequeueNativeAd: Method,
    val retryNativeLoad: Method,
    val bannerLoader: Method,
    val albumAdapterFactory: Method,
) {
    val disabledGates get() = listOf(slotGate) + eligibilityGates
    val disabledLoaders get() = initialLoaders + listOf(nativeLoader, retryNativeLoad, bannerLoader)
}

internal fun Method.sameAdsMethod(other: MethodReference) =
    definingClass == other.definingClass && name == other.name && returnType == other.returnType &&
        parameterTypes.map(CharSequence::toString) == other.parameterTypes.map(CharSequence::toString)

private fun Method.calls(owner: String, name: String, parameters: List<String>, result: String): Boolean =
    implementation?.instructions?.any {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
            reference.definingClass == owner && reference.name == name &&
                reference.parameterTypes.map(CharSequence::toString) == parameters && reference.returnType == result
        } == true
    } == true

internal fun resolveRifAdsTargets(definitions: List<ClassDef>): RifAdsTargets {
    val error = "Unsupported RIF advertising integration. Block ads requires the unmodified free RIF 5.6.22 APK."
    val classes = definitions.groupBy { it.type }
    fun type(name: String): ClassDef {
        val matches = classes[name].orEmpty()
        require(matches.size == 1) { error }
        return matches.single()
    }
    fun method(
        owner: String,
        name: String,
        parameters: List<String> = emptyList(),
        result: String = "V",
        visibility: AccessFlags = AccessFlags.PUBLIC,
    ): Method {
        val methods = type(owner).methods.filter {
            it.name == name && it.returnType == result &&
                it.parameterTypes.map(CharSequence::toString) == parameters
        }
        require(methods.size == 1) { error }
        return methods.single().also {
            require(it.implementation != null && !AccessFlags.STATIC.isSet(it.accessFlags) &&
                visibility.isSet(it.accessFlags)) { error }
        }
    }
    val manager = "${RIF_ADS_PACKAGE}RifNativeAdLoaderWaitListManager;"
    val albumWrapper = "${RIF_ADS_PACKAGE}RifAppLovinImageAlbumRecyclerAdapter;"
    val slotGate = method("Le5/e0;", "d0", result = "Z", visibility = AccessFlags.PROTECTED)
    val parser = method("Lb5/y0;", "b0", listOf("Ljava/io/InputStream;"), "Ljava/util/List;",
        AccessFlags.PROTECTED)
    require(type(slotGate.definingClass).superclass == parser.definingClass &&
        parser.calls(parser.definingClass, slotGate.name, emptyList(), "Z") &&
        parser.implementation!!.instructions.any {
            it.opcode == Opcode.NEW_INSTANCE && (it as? ReferenceInstruction)?.reference?.toString() == NATIVE_THREAD
        }) { error }
    val helpers = listOf("${RIF_ADS_PACKAGE}AdViewHelper;", "${RIF_ADS_PACKAGE}ImageAlbumAdViewHelper;")
    // The layout gate selects RIF's native hide/destroy path instead of reserving an empty banner container.
    val eligibility = helpers.map { method(it, "isAdsEnabledAndUnblocked", result = "Z",
        visibility = AccessFlags.PRIVATE) } + method("Lu1/b;", "b",
        listOf("Lcom/andrewshu/android/reddit/MainActivity;"), "Z", AccessFlags.PRIVATE)
    for (gate in eligibility + slotGate) {
        require(gate.calls("Lk4/v;", "t0", emptyList(), "Z") &&
            gate.calls("Lk4/v;", "U0", emptyList(), "Z") &&
            gate.calls("Lo5/i0;", "a", emptyList(), "Z")) { error }
    }
    val initialLoaders = helpers.map { method(it, "checkConditionsAndLoadInitialAds",
        visibility = AccessFlags.PRIVATE) }
    require(initialLoaders.all { it.calls(manager, "loadAds", listOf("Landroid/app/Activity;"), "V") }) { error }
    val nativeLoader = method(manager, "loadAds", listOf("Landroid/app/Activity;"))
    val dequeue = method(manager, "dequeueAd", result = "Ljava/lang/Object;")
    val retry = method("${RIF_ADS_PACKAGE}RifNativeAdLoaderWaitListManager\$RetryLoadRunnable;", "run")
    require(listOf(nativeLoader, dequeue, retry).all { it.calls(NATIVE_LOADER, "loadAd", emptyList(), "V") }) { error }
    val banner = method("${RIF_ADS_PACKAGE}BannerAdViewHelper;", "loadAd", listOf("Landroid/view/View;", "Z"))
    require(banner.calls("Lcom/applovin/mediation/ads/MaxAdView;", "loadAd", emptyList(), "V")) { error }
    val adapter = method(helpers.last(), "newRecyclerAdapter",
        listOf("Landroid/app/Activity;", RIF_RECYCLER_ADAPTER), RIF_RECYCLER_ADAPTER)
    require(adapter.calls(albumWrapper, "<init>", listOf(helpers.last(), RIF_RECYCLER_ADAPTER), "V") &&
        adapter.implementation!!.instructions.any {
            it.opcode == Opcode.NEW_INSTANCE && (it as? ReferenceInstruction)?.reference?.toString() == albumWrapper
        }) { error }
    return RifAdsTargets(slotGate, eligibility, initialLoaders, nativeLoader, dequeue, retry, banner, adapter)
}
