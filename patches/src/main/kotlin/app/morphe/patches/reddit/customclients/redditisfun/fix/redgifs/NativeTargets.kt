/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val ITEM = "Lcom/andrewshu/android/reddit/browser/gfycat/redgifs/RedgifsItem;"
private const val FRAGMENT = "Landroidx/fragment/app/Fragment;"

internal data class RedgifsNativeTargets(
    val authorization: Method,
    val tokenGetter: Method,
    val callField: FieldReference,
    val statusField: FieldReference,
    val requestField: FieldReference,
    val callback: Method,
    val failure: MethodReference,
    val viewer: ClassDef,
    val activeUri: FieldReference,
    val isAdded: MethodReference,
    val activity: Method,
    val view: Method,
)

internal fun Method.methodReferences() = (implementation?.instructions ?: emptyList()).mapNotNull {
    (it as? ReferenceInstruction)?.reference as? MethodReference
}

private fun Method.fieldReferences() = (implementation?.instructions ?: emptyList()).mapNotNull {
    (it as? ReferenceInstruction)?.reference as? FieldReference
}

internal fun MethodReference.sameMethod(other: MethodReference) =
    definingClass == other.definingClass && name == other.name && returnType == other.returnType &&
            parameterTypes.map(CharSequence::toString) == other.parameterTypes.map(CharSequence::toString)

// Follow native call/model relationships: free and paid APKs use different obfuscated names.
internal fun resolveRedgifsTargets(definitions: List<ClassDef>): RedgifsNativeTargets {
    val error = "Unsupported or ambiguous RIF Redgifs integration. " +
            "Use an unmodified RIF 5.6.22 free or Golden Platinum APK."
    val classes = definitions.associateBy { it.type }
    val methods = definitions.flatMap { it.methods }
    fun <T> Collection<T>.only(): T {
        require(size == 1) { error }
        return single()
    }
    fun definition(reference: MethodReference): Method =
        (classes[reference.definingClass]?.methods ?: emptyList()).filter {
            it.sameMethod(reference)
        }.only()
    // Start at the legacy OAuth task, then trace its caller to the saved-token getter.
    val authorization = methods.filter { method ->
        method.returnType == "Ljava/lang/String;" && method.parameterTypes.isEmpty() &&
                AccessFlags.PRIVATE.isSet(method.accessFlags) &&
                (method.implementation?.instructions ?: emptyList()).any {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                            "https://api.redgifs.com/v2/oauth/client"
                }
    }.only()
    val base = classes.getValue(authorization.definingClass)
    val cached = base.methods.filter { method ->
        method.returnType == "Ljava/lang/String;" && method.parameterTypes.isEmpty() &&
                method.methodReferences().any { it.sameMethod(authorization) }
    }.only()
    val tokenGetter = definition(cached.methodReferences().filter {
        it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty() &&
                it.definingClass != base.type
    }.only())
    // Locate the task's HTTP status, requested URI and active Call for errors and cancellation.
    val network = base.methods.filter { method ->
        method.returnType == "Ljava/lang/Object;" &&
                method.parameterTypes.map(CharSequence::toString) == listOf("[Ljava/lang/Void;") &&
                method.fieldReferences().any { it.type == "I" && it.definingClass == base.type }
    }.only()
    val status = network.fieldReferences().filter { it.type == "I" && it.definingClass == base.type }
        .distinctBy { it.toString() }.only()
    val request = base.fields.filter { it.type == "Landroid/net/Uri;" }.only()
    val callTypes = authorization.methodReferences().filter {
        it.returnType.startsWith("Lokhttp3/") && it.parameterTypes.size == 1
    }.map { it.returnType }.toSet()
    val call = base.fields.filter { it.type in callTypes }.only()
    require(AccessFlags.PROTECTED.isSet(request.accessFlags) &&
            AccessFlags.PROTECTED.isSet(base.fields.filter { it.name == status.name }.only().accessFlags)) { error }
    // Find the RedgifsItem parser task and its player callback, excluding preview-only consumers.
    val metadata = definitions.filter { cls ->
        cls.superclass == base.type && cls.methods.any { method ->
            method.returnType == ITEM && method.parameterTypes.map(CharSequence::toString) ==
                    listOf("Ljava/io/InputStream;")
        }
    }.only()
    val callbackClass = definitions.filter { cls ->
        cls.superclass == metadata.type && cls.fields.any { field ->
            val contract = classes[field.type]
            contract != null && AccessFlags.INTERFACE.isSet(contract.accessFlags) &&
                    contract.methods.count { it.returnType == "V" && it.parameterTypes.isEmpty() } == 2
        }
    }.only()
    val callback = callbackClass.methods.filter {
        it.returnType == "V" && it.parameterTypes.map(CharSequence::toString) == listOf(ITEM) &&
                !AccessFlags.BRIDGE.isSet(it.accessFlags)
    }.only()
    val contract = callbackClass.fields.mapNotNull { classes[it.type] }.filter {
        AccessFlags.INTERFACE.isSet(it.accessFlags) &&
                it.methods.count { method -> method.returnType == "V" && method.parameterTypes.isEmpty() } == 2
    }.only()
    val calls = callback.methodReferences().filter { it.definingClass == contract.type }
    require(calls.size == 2 && calls.all { it.returnType == "V" && it.parameterTypes.isEmpty() }) { error }
    // The result callback dispatches success first, then failure; failure leads to browser fallback.
    val failure = calls.last()
    val viewer = definitions.filter { contract.type in it.interfaces }.only()
    require(AccessFlags.PUBLIC.isSet(viewer.accessFlags)) { error }
    val failureMethod = viewer.methods.filter { it.name == failure.name && it.parameterTypes.isEmpty() }.only()
    val added = failureMethod.methodReferences().filter {
        it.definingClass == FRAGMENT && it.parameterTypes.isEmpty() && it.returnType == "Z"
    }.only()
    val fallback = definition(failureMethod.methodReferences().filter {
        it.definingClass == viewer.type && it.parameterTypes.isEmpty() && it.returnType == "V"
    }.only())
    // The player's fallback reads the current source URI; use it to reject stale video failures.
    val activeUri = fallback.fieldReferences().filter { it.type == "Landroid/net/Uri;" }
        .distinctBy { it.toString() }.only()
    val activeField = (classes[activeUri.definingClass]?.fields ?: emptyList()).filter {
        it.name == activeUri.name && it.type == activeUri.type
    }.only()
    require(AccessFlags.PROTECTED.isSet(activeField.accessFlags)) { error }
    val fragment = checkNotNull(classes[FRAGMENT]) { error }
    // Resolve nullable getActivity()/getView(), not the throwing requireActivity()/requireView() variants.
    val activity = fragment.methods.filter { method ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) && method.parameterTypes.isEmpty() &&
                method.returnType == "Landroidx/fragment/app/FragmentActivity;" &&
                method.implementation?.instructions?.any { it.opcode == Opcode.NEW_INSTANCE } == false
    }.only()
    val view = fragment.methods.filter { method ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) && method.parameterTypes.isEmpty() &&
                method.returnType == "Landroid/view/View;" &&
                method.implementation?.instructions?.count() == 2 &&
                method.fieldReferences().any { it.type == "Landroid/view/View;" }
    }.only()
    return RedgifsNativeTargets(authorization, tokenGetter, call, status, request, callback,
        failure, viewer, activeUri, added, activity, view)
}
