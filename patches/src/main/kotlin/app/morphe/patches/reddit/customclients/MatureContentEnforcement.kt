/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.boostforreddit.bindings.BoostApi
import app.morphe.patches.reddit.customclients.infinity.fix.links.isNativeResponseBody
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val LOCK = "Lapp/morphe/extension/shared/settings/MatureContentLock;"
private const val FILTER = "Lapp/morphe/extension/shared/fixes/feed/MatureContentFilter;"
private const val PREFERENCE = "Landroidx/preference/Preference;"
private const val TWO_STATE = "Landroidx/preference/TwoStatePreference;"
private const val BACON_REST = "Lcom/onelouder/baconreader/connectivity/RestClient;"
private const val BACON_ADAPTER = "Lcom/onelouder/baconreader/adapters/SettingsAdapter;"
private const val BACON_SETTING = "Lcom/onelouder/baconreader/adapters/Setting;"
private const val BACON_SETTING_ID = "Lcom/onelouder/baconreader/adapters/SettingIds;"
private const val SHARED_PREFERENCES = "Landroid/content/SharedPreferences;"

internal fun installMatureContentEnforcement(packageName: String) {
    hookNativePreferences()
    when {
        packageName == AppCompatibility.Boost.single().packageName -> {
            hookBoostBackup()
            BoostApi.SearchOptionsView.refreshOptions.fingerprint().wrapMorpheHook(after =
                "invoke-static {p0}, $LOCK->lockBoostSearch(Landroid/view/View;)V")
            BoostApi.DrawerActivity.setDrawerSubscriptions.fingerprint().wrapMorpheHook(before = """
                invoke-static {p0, p1}, Lapp/morphe/extension/boost/navigation/BoostDrawerSubscriptions;->filter(${BoostApi.DrawerActivity.type}Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object p1
            """.trimIndent())
        }
        packageName.startsWith("com.onelouder.baconreader") -> hookBaconReader()
        packageName.startsWith("ml.docilealligator.infinityforreddit") -> hookInfinityBodies()
        packageName.startsWith("o.o.joey") -> Fingerprint(
            definingClass = "Lo/o/joey/SettingActivities/FilterSettings;",
            parameters = emptyList(), returnType = "V",
            custom = { method, _ ->
                method.references<MethodReference>().count { it.name == "setOnCheckedChangeListener" } > 1
            },
        ).wrapMorpheHook(after = "invoke-static {p0}, $LOCK->lockJoeyFilters(Landroid/app/Activity;)V")
    }
}

private fun hookNativePreferences() {
    val classes = buildList { classDefForEach(::add) }
    val native = classes.filterNot { it.type.startsWith("Lapp/morphe/") }
    val read = ImmutableMethodReference(
        LOCK, "readBoolean", listOf(SHARED_PREFERENCES, "Ljava/lang/String;", "Z"), "Z",
    )
    for (owner in native) {
        for (method in owner.methods) {
            val calls = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                val ref = instruction.getReference<MethodReference>()
                if (ref?.definingClass != SHARED_PREFERENCES ||
                    ref.name != "getBoolean" || ref.parameterTypes.map(CharSequence::toString) != read.parameterTypes.drop(1)
                ) return@mapIndexedNotNull null
                val replacement = when (instruction) {
                    is Instruction35c -> BuilderInstruction35c(
                        Opcode.INVOKE_STATIC, instruction.registerCount, instruction.registerC,
                        instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG, read,
                    )
                    is Instruction3rc -> BuilderInstruction3rc(
                        Opcode.INVOKE_STATIC_RANGE, instruction.startRegister, instruction.registerCount, read,
                    )
                    else -> throw PatchException("Unexpected SharedPreferences.getBoolean invocation.")
                }
                index to replacement
            }.orEmpty()
            if (calls.isEmpty()) continue
            val mutable = currentMethod(mutableClassDefBy(owner), method)
            for ((index, instruction) in calls) mutable.replaceInstruction(index, instruction)
        }
    }

    val preference = native.singleOrNull { it.type == PREFERENCE }
    val twoState = native.singleOrNull { it.type == TWO_STATE }
    if (preference != null && twoState != null) {
        val key = preference.methods.firstOrNull { it.name == "getKey" }?.references<FieldReference>()
            ?.singleOrNull { it.type == "Ljava/lang/String;" }
            ?: preference.methods.asSequence().filter { it.parameterTypes.isEmpty() && it.returnType == "Z" }
                .filter { method -> method.references<MethodReference>().any {
                    it.definingClass == "Landroid/text/TextUtils;" && it.name == "isEmpty"
                } }
                .flatMap { it.references<FieldReference>().asSequence() }
                .filter { it.definingClass == PREFERENCE && it.type == "Ljava/lang/String;" }
                .distinct().single()
        val enabled = preference.methods.single { method ->
            method.parameterTypes.isEmpty() && method.returnType == "Z" &&
                method.references<FieldReference>().count { it.definingClass == PREFERENCE && it.type == "Z" } == 3 &&
                method.references<MethodReference>().isEmpty()
        }
        val setter = twoState.methods.single { method ->
            method.parameterTypes.map(CharSequence::toString) == listOf("Z") && method.returnType == "V" &&
                method.implementation?.instructions?.count { it.opcode == Opcode.IPUT_BOOLEAN } == 2
        }
        val preferenceClass = mutableClassDefBy(preference)
        // Read the private native preference key from its defining class.
        val keyGetter = "morpheMatureContentKey"
        preferenceClass.methods.add(newPreferenceKeyGetter(keyGetter, key))
        wrapMorpheHook(preferenceClass, enabled, before = """
            invoke-virtual {p0}, $PREFERENCE->$keyGetter()Ljava/lang/String;
            move-result-object v0
            invoke-static {v0}, $LOCK->disablesPreference(Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :morphe_native_enabled
            const/4 v0, 0x0
            return v0
            :morphe_native_enabled
        """.trimIndent())
        wrapMorpheHook(mutableClassDefBy(twoState), setter, before = """
            invoke-virtual {p0}, $PREFERENCE->$keyGetter()Ljava/lang/String;
            move-result-object v0
            invoke-static {v0, p1}, $LOCK->checked(Ljava/lang/String;Z)Z
            move-result p1
        """.trimIndent())
    }

    // Legacy PreferenceFragment screens (including older Boost settings) use the platform implementation.
    for (owner in native) {
        for (method in owner.methods) {
            val hooks = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                val ref = instruction.getReference<MethodReference>()
                if (ref?.name != "addPreferencesFromResource" ||
                    ref.definingClass !in setOf("Landroid/preference/PreferenceFragment;", "Landroid/preference/PreferenceActivity;")
                ) return@mapIndexedNotNull null
                val receiver = when (instruction) {
                    is Instruction35c -> instruction.registerC
                    is Instruction3rc -> instruction.startRegister
                    else -> return@mapIndexedNotNull null
                }
                Triple(index, receiver, ref.definingClass)
            }.orEmpty()
            if (hooks.isEmpty()) continue
            val mutable = currentMethod(mutableClassDefBy(owner), method)
            for ((index, receiver, type) in hooks.asReversed()) {
                val name = if (type.endsWith("Fragment;")) "lockNativeFragment" else "lockNativeActivity"
                mutable.addInstruction(index + 1, "invoke-static/range {v$receiver .. v$receiver}, $LOCK->$name($type)V")
            }
        }
    }
}

private fun newPreferenceKeyGetter(name: String, key: FieldReference): MutableMethod =
    ImmutableMethod(
        PREFERENCE, name, emptyList(), "Ljava/lang/String;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        null, null, MutableMethodImplementation(2),
    ).toMutable().apply {
        addInstruction(0, "iget-object v0, p0, $key")
        addInstruction(1, "return-object v0")
    }

private inline fun <reified T : Reference> Method.references(): List<T> =
    implementation?.instructions?.mapNotNull { it.getReference<T>() }.orEmpty()

private fun hookBoostBackup() {
    val export = BoostApi.BoostPreferences.exportSettings.fingerprint()
    export.wrapMorpheHook(after = """
        invoke-static {v0}, $LOCK->exportBoostSetting(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v0
    """.trimIndent())

    val restore = BoostApi.BoostSettingsBackup.restore.fingerprint()
    val model = restore.classDef
    val flag = "pref_patcheddit_mature_content_lock"
    model.fields.add(ImmutableField(model.type, flag, "Z", AccessFlags.PUBLIC.value, null, null, null).toMutable())
    restore.wrapMorpheHook(after = """
        iget-boolean v0, p0, ${model.type}->$flag:Z
        invoke-static {p1, v0}, $LOCK->restoreBoostSetting(Landroid/content/Context;Z)V
    """.trimIndent())
}

private fun hookBaconReader() {
    val selectedSetting = """
        invoke-virtual {p0, p1}, $BACON_ADAPTER->getItem(I)$BACON_SETTING
        move-result-object v1
        iget-object v1, v1, $BACON_SETTING->id:$BACON_SETTING_ID
    """.trimIndent()
    Fingerprint(definingClass = BACON_ADAPTER, name = "isEnabled", parameters = listOf("I"), returnType = "Z")
        .wrapMorpheHook(before = """
            invoke-static {}, $LOCK->isLocked()Z
            move-result v0
            if-eqz v0, :morphe_native_setting
            $selectedSetting
            invoke-static {v1}, $LOCK->disablesBaconRow(Ljava/lang/Enum;)Z
            move-result v0
            if-eqz v0, :morphe_native_setting
            const/4 v0, 0x0
            return v0
            :morphe_native_setting
        """.trimIndent())
    Fingerprint(
        definingClass = BACON_ADAPTER, name = "getItemView",
        parameters = listOf("I", "Landroid/view/View;", "Landroid/view/ViewGroup;"), returnType = "Landroid/view/View;",
    ).wrapMorpheHook(after = """
        invoke-static {}, $LOCK->isLocked()Z
        move-result v2
        if-eqz v2, :morphe_native_row
        $selectedSetting
        invoke-static {v0, v1}, $LOCK->styleBaconRow(Landroid/view/View;Ljava/lang/Enum;)V
        :morphe_native_row
    """.trimIndent())
    Fingerprint(
        definingClass = BACON_REST, name = "executeRequest", returnType = "V",
        parameters = listOf("Landroid/content/Context;", "Lorg/apache/http/client/methods/HttpUriRequest;"),
    ).wrapMorpheHook(after = """
        iget-object v0, p0, $BACON_REST->response:Ljava/lang/String;
        iget-object v1, p0, $BACON_REST->url:Ljava/lang/String;
        iget v2, p0, $BACON_REST->responseCode:I
        iget-object p1, p0, $BACON_REST->contentType:Ljava/lang/String;
        invoke-static {v0, v1, v2, p1}, $FILTER->filterLegacyResponse(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
        move-result-object v0
        iput-object v0, p0, $BACON_REST->response:Ljava/lang/String;
    """.trimIndent())
}

private fun hookInfinityBodies() {
    val body = Fingerprint(
        parameters = emptyList(), returnType = "Ljava/lang/String;",
        custom = { method, owner ->
            !owner.type.startsWith("Lapp/morphe/") && isNativeResponseBody(owner) &&
                method.implementation != null &&
                AccessFlags.FINAL.isSet(method.accessFlags) && !AccessFlags.STATIC.isSet(method.accessFlags)
        },
    )
    val owner = body.classDef
    body.wrapMorpheHook(after = """
        invoke-static {v0}, $FILTER->filterNativeBody(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v0
    """.trimIndent())
    owner.methods.filter { it.returnType == "Ljava/io/Reader;" && it.parameterTypes.isEmpty() }.forEach {
        wrapMorpheHook(owner, it, after = """
            invoke-static {v0}, $FILTER->filterNativeReader(Ljava/io/Reader;)Ljava/io/Reader;
            move-result-object v0
        """.trimIndent())
    }
}
