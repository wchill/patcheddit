/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.redditisfun.bindings.RifNativeSku
import app.morphe.patches.reddit.customclients.redditisfun.bindings.resolveRifNativeSku
import app.morphe.patches.reddit.customclients.wrapMorpheHook
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val URI = "Landroid/net/Uri;"
private const val STOCK_CHECK = "Lcom/andrewshu/android/reddit/login/oauth2/c;->p($URI)Z"
private const val STOCK_BUILDER = "Lokhttp3/c0\$a;"
private const val BUILDER = "Lapp/morphe/extension/shared/http/libraries/okhttp3/Request\$Builder;"
private const val HOOK = "Lapp/morphe/extension/redditisfun/feed/GuestListings;"

internal val guestListingsPatch = patch {
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(ExtensionPatches.RIF)

    execute {
        val free = resolveRifNativeSku(buildList { classDefForEach(::add) }) == RifNativeSku.FREE
        val prefix = if (free) "Lh3/" else "Li3/"
        val base = mutableClassDefBy("${prefix}c;")
        val get = mutableClassDefBy("${prefix}g;")
        val account = base.methods.single {
            it.name == "I" && it.parameterTypes.isEmpty() && it.returnType == "Landroid/accounts/Account;"
        }
        val retry = base.fields.single { it.name == "p" && it.type == "Z" }
        val guest = base.methods.single { method ->
            method.name == "S" && method.parameterTypes.isEmpty() && method.returnType == "Z" &&
                    method.implementation?.instructions.orEmpty().any {
                        it.opcode == Opcode.INVOKE_STATIC && it.getReference<MethodReference>().toString() == STOCK_CHECK
                    }
        }
        val uri = guest.implementation!!.instructions.single {
            val reference = it.getReference<MethodReference>()
            it.opcode == Opcode.INVOKE_VIRTUAL && reference?.definingClass == base.type &&
                    reference.parameterTypes.isEmpty() && reference.returnType == URI
        }.getReference<MethodReference>()
        require(get.superclass == base.type && get.methods.any { method ->
            method.implementation?.instructions.orEmpty().any {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                        it.getReference<MethodReference>().toString() == "$STOCK_BUILDER->g()$STOCK_BUILDER"
            }
        }) { "Could not identify the native RIF GET worker." }

        // Let RIF negotiate and cache its existing app-only token. The original check
        // still handles its native OAuth routes; never use guest auth for an account retry.
        wrapMorpheHook(base, guest, before = """
            instance-of v0, p0, ${get.type}
            if-eqz v0, :morphe_skip_before
            iget-boolean v0, p0, $retry
            if-nez v0, :morphe_skip_before
            invoke-virtual {p0}, $account
            move-result-object v0
            if-nez v0, :morphe_skip_before
            invoke-virtual {p0}, $uri
            move-result-object v0
            invoke-static {v0}, $HOOK->isListing($URI)Z
            move-result v0
            if-eqz v0, :morphe_skip_before
            return v0
        """)

        // Stock RIF only attaches the app-only bearer to its original /api/v1/ routes.
        val listingWorker = mutableClassDefBy("${prefix}a\$a;")
        require(listingWorker.superclass == get.type) { "Could not identify the native RIF listing worker." }
        for (worker in listOf(base, listingWorker)) {
            val headers = worker.methods.single { method ->
                method.name == "B" && method.returnType == "V" && method.parameterTypes.size == 4 &&
                        method.parameterTypes.takeLast(3) == listOf(STOCK_BUILDER, "Ljava/lang/String;", URI)
            }
            wrapMorpheHook(worker, headers, after = """
                if-eqz p3, :morphe_skip_after
                invoke-virtual {p0}, $guest
                move-result v0
                if-eqz v0, :morphe_skip_after
                invoke-static {p2, p3, p4}, $HOOK->authorize(${BUILDER}Ljava/lang/String;$URI)V
            """)
        }
    }
}
