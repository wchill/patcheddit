package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val SHARE_NAV = "Lapp/morphe/extension/shared/fixes/slink/ShareLinkNavigation;"
internal const val OPEN_SHARE = "open(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z"
private const val ANDROID = "http://schemas.android.com/apk/res/android"

internal data class ShareViewFilter(val host: String, val pathAttribute: String, val path: String)

internal val redditMailHosts = listOf("click.redditmail.com", "c.redditmail.com")
internal val redditAppLinkHosts = listOf("s.reddit.com", "applink.reddit.com", "reddit.onelink.me", "reddit.app.link")

internal fun redditWrapperViewFilters(
    activity: String, hosts: List<String> = redditMailHosts + redditAppLinkHosts,
) = shareViewFilters(setOf(activity, activity.substringAfterLast('.', "").let { ".$it" }),
    *hosts.map { ShareViewFilter(it, "pathPrefix", "/") }.toTypedArray())

internal fun hookWrapperActivity(
    activity: String, target: String? = activity, handler: String = "$SHARE_NAV->openWrapperActivity",
) {
    val method = Fingerprint(definingClass = "L${activity.replace('.', '/')};",
        name = "onCreate", parameters = listOf("Landroid/os/Bundle;"), returnType = "V").method
    val code = checkNotNull(method.implementation)
    val index = code.instructions.indexOfFirst {
        it.opcode == Opcode.INVOKE_SUPER && it.getReference<MethodReference>()?.name == "onCreate"
    } + 1
    check(index == 1 && code.registerCount in 3..16) { "Unexpected link activity initialization in $activity" }
    val targetName = if (target == null) "const/4 v0, 0x0" else "const-string v0, \"$target\""
    method.addInstructionsWithLabels(index, """
        $targetName
        invoke-static {p0, v0}, $handler(Landroid/app/Activity;Ljava/lang/String;)Z
        move-result v0
        if-eqz v0, :open_native
        return-void
    """, ExternalLabel("open_native", method.getInstruction(index)))
}

internal fun shareViewFilters(activityNames: Set<String>, vararg filters: ShareViewFilter) = patch(default = false) {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            addShareViewFilters(manifest, activityNames, *filters)
        }
    }
}

internal fun addShareViewFilters(manifest: Document, activityNames: Set<String>, vararg filters: ShareViewFilter) {
    val activity = manifest.getElementsByTagName("activity").let { activities ->
        (0 until activities.length).asSequence().map { activities.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") in activityNames }
    }
    for ((host, pathAttribute, path) in filters) {
        activity.appendChild(manifest.createElement("intent-filter").apply {
            fun entry(tag: String, vararg attrs: Pair<String, String>) = manifest.createElement(tag).apply {
                attrs.forEach { (name, value) -> setAttributeNS(ANDROID, "android:$name", value) }
            }
            appendChild(entry("action", "name" to "android.intent.action.VIEW"))
            appendChild(entry("category", "name" to "android.intent.category.DEFAULT"))
            appendChild(entry("category", "name" to "android.intent.category.BROWSABLE"))
            appendChild(entry("data", "scheme" to "https", "host" to host, pathAttribute to path))
        })
    }
}

internal fun bindShareLinkAccessToken(instructions: String) {
    Fingerprint(
        definingClass = SHARE_NAV,
        name = "getAccessToken",
        parameters = listOf("Landroid/content/Context;"),
        returnType = "Ljava/lang/String;",
    ).apply {
        // Wrap the null-returning stub to obtain scratch registers without changing its inputs.
        wrapMorpheHook(classDef, method, before = instructions)
    }
}
