/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.submit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.rifFeatureResources
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Element

private const val HOOK = "Lapp/morphe/extension/redditisfun/profile/ProfilePosting;"
private const val FORM = "Lcom/andrewshu/android/reddit/submit/m;"
private const val PROFILE = "Lcom/andrewshu/android/reddit/user/c;"
private const val MENU = "Lcom/andrewshu/android/reddit/user/c\$c;"

@Suppress("unused")
val postToProfilePatch = morpheBytecodePatch(
    name = "Post to profile",
    description = "Adds account-aware profile destinations to Submit and the own-profile menu.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.COMMENTS,
    readOnly = true,
) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(rifFeatureResources, resourcePatch(default = false) {
        execute {
            // Reuse the picker styling in both native Submit layouts.
            for (directory in listOf("layout", "layout-v22")) {
                document("res/$directory/submit_fragment.xml").use { layout ->
                    val buttons = layout.getElementsByTagName("Button")
                    val picker = (0 until buttons.length).map { buttons.item(it) as Element }
                        .single { it.getAttribute("android:onClick") == "pickReddit" }
                    val row = layout.createElement("LinearLayout").apply {
                        setAttribute("android:layout_width", "match_parent")
                        setAttribute("android:layout_height", "wrap_content")
                        setAttribute("android:gravity", "end")
                    }
                    picker.parentNode.insertBefore(row, picker)
                    row.appendChild(picker)
                    row.appendChild(picker.cloneNode(true).apply {
                        (this as Element).removeAttribute("android:onClick")
                        setAttribute("android:id", "@+id/patcheddit_my_profile")
                        setAttribute("android:text", "@string/patcheddit_my_profile")
                        setAttribute("android:visibility", "gone")
                        setAttribute("android:layout_width", "0dp")
                        setAttribute("android:layout_weight", "1")
                    })
                }
            }
            document("res/menu/profile.xml").use { menu ->
                val items = menu.getElementsByTagName("item")
                val inbox = (0 until items.length).map { items.item(it) as Element }
                    .single { it.getAttribute("android:id").endsWith("/menu_inbox") }
                inbox.parentNode.insertBefore(menu.createElement("item").apply {
                    setAttribute("android:id", "@+id/patcheddit_profile_submit")
                    setAttribute("android:title", "@string/submit_link")
                    setAttribute("android:visible", "false")
                }, inbox)
            }
        }
    })
    execute {
        val definitions = buildList { classDefForEach(::add) }
        val prefs = if (resolveRifNativeSku(definitions) == RifNativeSku.FREE) "Lk4/v;" else "Ll4/v;"
        Fingerprint(definingClass = prefs, name = "q0", parameters = emptyList(),
            returnType = "Ljava/lang/String;").method
        Fingerprint(definingClass = HOOK, name = "currentUsername").method.addInstructions(0, """
            invoke-static {}, $prefs->C()$prefs
            move-result-object v0
            invoke-virtual {v0}, $prefs->a1()Z
            move-result v1
            if-nez v1, :morphe_signed_in
            const/4 v0, 0x0
            return-object v0
            :morphe_signed_in
            invoke-virtual {v0}, $prefs->q0()Ljava/lang/String;
            move-result-object v0
            return-object v0
        """)
        // Delegate to the picker callback so rules and flair refresh normally.
        val form = mutableClassDefBy(FORM)
        val picker = form.methods.single { it.name == "onPickReddits" }
        val calls = picker.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
        val select = calls.single { it.definingClass == FORM && it.parameterTypes == listOf("Ljava/lang/String;") }
        val activity = calls.first { it.returnType == "Landroidx/fragment/app/FragmentActivity;" }
        val inflate = form.methods.single {
            it.parameterTypes == listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;")
                    && it.returnType == "Landroid/view/View;"
        }
        val binding = inflate.implementation!!.instructions.first {
            it.opcode == Opcode.IPUT_OBJECT && it.getReference<FieldReference>()?.definingClass == FORM
        }.getReference<FieldReference>()!!
        val root = mutableClassDefBy(binding.type).methods.single {
            it.parameterTypes.isEmpty() && it.returnType == "Landroid/widget/FrameLayout;"
        }
        form.interfaces.add("${HOOK.dropLast(1)}\$Form;")
        fun bridge(name: String, parameters: List<String>, returns: String, registers: Int, code: String) {
            check(form.methods.none { it.name == name })
            form.methods.add(ImmutableMethod(
                FORM, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
                AccessFlags.PUBLIC.value, emptySet(), emptySet(), MutableMethodImplementation(registers),
            ).toMutable().apply { addInstructions(0, code) })
        }
        bridge("morpheSubmitView", emptyList(), "Landroid/view/View;", 2, """
            iget-object v0, p0, $binding
            if-eqz v0, :morphe_no_view
            invoke-virtual {v0}, $root
            move-result-object v0
            return-object v0
            :morphe_no_view
            const/4 v0, 0x0
            return-object v0
        """)
        bridge("morpheSelectDestination", listOf("Ljava/lang/String;"), "V", 2, """
            invoke-virtual {p0, p1}, $select
            return-void
        """)
        wrapMorpheHook(form, inflate, after = "invoke-static {p0}, $HOOK->bind(${HOOK.dropLast(1)}\$Form;)V")
        // Follow native account changes without recreating the shared form.
        val update = form.methods.single { it.name == "onLogin" }.implementation!!.instructions
            .mapNotNull { it.getReference<MethodReference>() }
            .single { it.definingClass == FORM && it.parameterTypes == listOf("Ljava/lang/String;") }
        val accountUpdate = form.methods.single {
            it.name == update.name && it.parameterTypes == update.parameterTypes && it.returnType == update.returnType
        }
        wrapMorpheHook(form, accountUpdate, after =
            "invoke-static {p0}, $HOOK->refresh(${HOOK.dropLast(1)}\$Form;)V")

        // Reuse native ownership comparison; Inbox visibility does not imply ownership.
        val callback = mutableClassDefBy(MENU)
        val prepare = callback.methods.single { method ->
            method.parameterTypes == listOf("Landroid/view/Menu;") && method.returnType == "V" &&
                    method.implementation!!.instructions.any {
                        it.getReference<MethodReference>()?.toString() ==
                                "Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z"
                    }
        }
        val code = prepare.implementation!!.instructions.toList()
        val compare = code.indices.single {
            code[it].getReference<MethodReference>()?.toString() ==
                    "Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z"
        }
        val own = (code[compare + 1] as OneRegisterInstruction).registerA
        prepare.addInstructions(compare + 2, "invoke-static {p1, v$own}, $HOOK->prepare(Landroid/view/Menu;Z)V")
        val viewed = code.mapNotNull { it.getReference<MethodReference>() }
            .single { it.definingClass == PROFILE && it.returnType == "Ljava/lang/String;" }
        val owner = callback.fields.single { it.type == PROFILE }
        val click = callback.methods.single { it.parameterTypes == listOf("Landroid/view/MenuItem;") && it.returnType == "Z" }
        wrapMorpheHook(callback, click, before = """
            iget-object v0, p0, $owner
            invoke-static {v0}, $viewed
            move-result-object v1
            invoke-virtual {v0}, $activity
            move-result-object v0
            invoke-static {v0, p1, v1}, $HOOK->open(Landroid/app/Activity;Landroid/view/MenuItem;Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :morphe_native_menu
            const/4 v0, 0x1
            return v0
            :morphe_native_menu
            nop
        """)
    }
}
