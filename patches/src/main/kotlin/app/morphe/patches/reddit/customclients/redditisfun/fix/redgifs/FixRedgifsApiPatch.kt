/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.http.privateHttpType
import app.morphe.patches.reddit.customclients.morpheBytecodePatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION = "Lapp/morphe/extension/redditisfun/RedgifsApiPatch;"

@Suppress("unused")
val fixRedgifsApi = morpheBytecodePatch(
    name = "Fix Redgifs API",
    description = "Restores native Redgifs playback and handles unavailable videos.",
    extensionPatch = ExtensionPatches.RIF,
    category = PatchCategory.MEDIA,
) { setting ->
    compatibleWith(*AppCompatibility.RedditIsFun)
    execute {
        val targets = resolveRedgifsTargets(buildList { classDefForEach(::add) })
        // The HTTP shim converts legacy OAuth calls to temporary-token GETs and adapts the response for RIF.
        setting.returnEnabled(Fingerprint(definingClass = EXTENSION, name = "isIncluded").method)

        val preferences = mutableClassDefBy(targets.tokenGetter.definingClass)
        // Validate the native getter's result in v0; retain RIF's existing cache/refresh behavior.
        wrapMorpheHook(preferences, targets.tokenGetter, setting = setting, after = """
            invoke-static {v0}, $EXTENSION->validCachedToken(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
        """)

        // Track the authorization call so the native cancellation path can stop it too.
        val owner = mutableClassDefBy(targets.authorization.definingClass)
        setting.patchMethod(owner, owner.methods.single { it.sameMethod(targets.authorization) }) {
            val instructions = checkNotNull(implementation).instructions.toList()
            val index = instructions.indices.single {
                instructions[it].getReference<MethodReference>()?.returnType == targets.callField.type
            }
            require(instructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT) {
                "Unsupported RIF Redgifs authorization call construction"
            }
            val register = getInstruction<OneRegisterInstruction>(index + 1).registerA
            require(register < 16 && checkNotNull(implementation).registerCount <= 16) {
                "Unsupported RIF Redgifs authorization register layout"
            }
            // Save the Call result before execute() overwrites that register with a Response.
            addInstructions(index + 2, "iput-object v$register, p0, ${targets.callField}")
        }

        val pinner = mutableClassDefBy(privateHttpType("Lokhttp3/CertificatePinner;"))
        val pinMethods = pinner.methods.filter {
            it.name.startsWith("findMatchingPins") && it.returnType == "Ljava/util/List;" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
        }
        require(pinMethods.size == 1) { "Unsupported RIF HTTP certificate-pinning implementation" }
        val matchingPins = pinMethods.single()
        // Only the Redgifs API host returns no application pins; system TLS checks still run.
        // Other hosts fall through to the wrapper's preserved native implementation.
        wrapMorpheHook(pinner, matchingPins, before = """
            invoke-static {p1}, $EXTENSION->useSystemTrust(Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :morphe_keep_pins
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v0
            return-object v0
            :morphe_keep_pins
            nop
        """)

        // Expose the obfuscated viewer's current media and lifecycle state to the extension.
        val viewer = mutableClassDefBy(targets.viewer.type)
        val bridgeName = "morpheUnavailableRedgifs"
        require(viewer.methods.none { it.name == bridgeName }) { "RIF Redgifs viewer was already patched" }
        viewer.methods.add(ImmutableMethod(
            viewer.type, bridgeName,
            listOf(ImmutableMethodParameter("Landroid/net/Uri;", emptySet(), null)), "Z",
            AccessFlags.PUBLIC.value, emptySet(), emptySet(), MutableMethodImplementation(6),
        ).toMutable().apply {
            // p0/p1 are the viewer/request URI; v0-v2 carry its activity, view and current media URI.
            addInstructions(0, """
                invoke-virtual {p0}, ${targets.isAdded}
                move-result v0
                if-eqz v0, :morphe_viewer_gone
                invoke-virtual {p0}, ${targets.activity}
                move-result-object v0
                invoke-virtual {p0}, ${targets.view}
                move-result-object v1
                iget-object v2, p0, ${targets.activeUri}
                invoke-static {v0, v1, v2, p1}, $EXTENSION->showUnavailable(Landroid/app/Activity;Landroid/view/View;Landroid/net/Uri;Landroid/net/Uri;)Z
                move-result v0
                return v0
                :morphe_viewer_gone
                const/4 v0, 0x1
                return v0
            """)
        })
        val extension = mutableClassDefBy(EXTENSION)
        val missing = extension.methods.single { it.name == "missingViewer" }
        // Keep v0 separate from p0/p1 so the type check cannot overwrite the callback argument.
        val missingBridge = ImmutableMethod(
            missing.definingClass, missing.name, missing.parameters, missing.returnType,
            missing.accessFlags, missing.annotations, missing.hiddenApiRestrictions, MutableMethodImplementation(3),
        ).toMutable().apply {
            addInstructions(0, """
                instance-of v0, p0, ${viewer.type}
                if-eqz v0, :morphe_unknown_viewer
                check-cast p0, ${viewer.type}
                invoke-virtual {p0, p1}, ${viewer.type}->$bridgeName(Landroid/net/Uri;)Z
                move-result v0
                return v0
                :morphe_unknown_viewer
                const/4 v0, 0x0
                return v0
            """)
        }
        check(extension.methods.remove(missing))
        check(extension.methods.add(missingBridge))

        // Handle metadata 404s without browser fallback; leave other native error paths intact.
        val callback = mutableClassDefBy(targets.callback.definingClass)
        setting.patchMethod(callback, callback.methods.single { it.sameMethod(targets.callback) }) {
            val instructions = checkNotNull(implementation).instructions.toList()
            val index = instructions.indices.single {
                instructions[it].getReference<MethodReference>()?.sameMethod(targets.failure) == true
            }
            val receiver = getInstruction<FiveRegisterInstruction>(index).registerC
            require(receiver !in 0..1 && checkNotNull(implementation).registerCount <= 16) {
                "Unsupported RIF Redgifs failure callback register layout"
            }
            // v0/v1 are free at this label; preserve the receiver and skip fallback only if handled.
            addInstructionsAtControlFlowLabel(index, """
                iget v0, p0, ${targets.statusField}
                iget-object v1, p0, ${targets.requestField}
                invoke-static {v$receiver, v1, v0}, $EXTENSION->handleMissing(Ljava/lang/Object;Landroid/net/Uri;I)Z
                move-result v0
                if-eqz v0, :morphe_original_redgifs_failure
                return-void
                :morphe_original_redgifs_failure
                nop
            """)
        }
    }
}
