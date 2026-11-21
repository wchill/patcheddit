/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.history

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.redditisfun.rifFeatureResources
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Element

private const val HOOK = "Lapp/morphe/extension/redditisfun/history/HistoryImport;"
private const val PROVIDER = "Lcom/andrewshu/android/reddit/history/UriHistoryProvider;"
private const val BASE = "Lcom/talklittle/basecontentprovider/BaseContentProvider;"

@Suppress("unused")
val fixHistoryImportPatch = morpheBytecodePatch(
    name = "Fix history import",
    description = "Adds missing visits from backups without replacing existing browsing history.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.ADVANCED,
    readOnly = true,
) {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(rifFeatureResources, resourcePatch(default = false) {
        execute {
            document("res/values/strings.xml").use { strings ->
                val nodes = strings.getElementsByTagName("string")
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index) as Element
                    when (node.getAttribute("name")) {
                        "pref_settings_import" -> node.textContent = "Import settings and history"
                        "pref_settings_import_summary" ->
                            node.textContent = "Imports any history in the backup. WARNING: Will overwrite ALL settings"
                    }
                }
            }
        }
    })
    execute {
        val provider = mutableClassDefBy(PROVIDER)
        // Share the native helper so imports yield to ordinary history writes.
        val database = Fingerprint(
            definingClass = BASE, name = "B", parameters = emptyList(),
            returnType = "Landroid/database/sqlite/SQLiteDatabase;",
        ).method
        check(database.implementation!!.instructions.any {
            it.getReference<MethodReference>()?.toString() ==
                    "Landroid/database/sqlite/SQLiteOpenHelper;->getWritableDatabase()Landroid/database/sqlite/SQLiteDatabase;"
        })
        // Let native resources supply the provider authority.
        val uri = provider.methods.single { it.name == "r" && it.returnType == "Landroid/net/Uri;" }
            .implementation!!.instructions.first().getReference<MethodReference>()!!
        check(uri.returnType == "Landroid/net/Uri;" && uri.parameterTypes.isEmpty())
        Fingerprint(definingClass = HOOK, name = "historyUri").method.addInstructions(0, """
            invoke-static {}, $uri
            move-result-object v0
            return-object v0
        """)
        check(provider.methods.none { it.name == "morpheHistoryDatabase" })
        provider.interfaces.add("${HOOK.dropLast(1)}\$Owner;")
        provider.methods.add(ImmutableMethod(
            PROVIDER, "morpheHistoryDatabase", emptyList(), "Landroid/database/sqlite/SQLiteDatabase;",
            AccessFlags.PUBLIC.value, emptySet(), emptySet(), MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(0, """
                invoke-virtual {p0}, $database
                move-result-object v0
                return-object v0
            """)
        })
        // Consume history without replacing the native ZIP/settings loop.
        Fingerprint(
            definingClass = "Lcom/andrewshu/android/reddit/settings/export/c;",
            name = "A", parameters = listOf("[Ljava/lang/Void;"), returnType = "Ljava/lang/Boolean;",
        ).method.apply {
            val code = implementation!!.instructions.toList()
            val index = code.indices.single {
                code[it].getReference<MethodReference>()?.toString() ==
                        "Ljava/util/zip/ZipInputStream;->getNextEntry()Ljava/util/zip/ZipEntry;"
            }
            check(code[index].opcode == Opcode.INVOKE_VIRTUAL && code[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
            val stream = (code[index] as FiveRegisterInstruction).registerC
            val entry = (code[index + 1] as OneRegisterInstruction).registerA
            val context = code.take(index).single {
                it.opcode == Opcode.CHECK_CAST && it.getReference<TypeReference>()?.type == "Landroid/app/Activity;"
            }.let { (it as OneRegisterInstruction).registerA }
            addInstructions(index + 2,
                "invoke-static {v$context, v$stream, v$entry}, $HOOK->onEntry(Landroid/content/Context;Ljava/util/zip/ZipInputStream;Ljava/util/zip/ZipEntry;)V")
        }
    }
}
