package app.morphe.patches.all.misc.debugging

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.getPatchesBundleVersion
import app.morphe.util.removeFromParent
import org.w3c.dom.Element

@Suppress("unused")
internal val enableSentryTelemetryPatch = resourcePatch(
    //name = "Enable sending Sentry telemetry",
    description = "Enables sending logging and crash reports to Sentry for debugging purposes.",
    use = false
) {
    val sentryDsnOption = stringOption(
        key = "sentry-dsn",
        default = "",
        title = "Sentry DSN",
        description = "The Sentry DSN to use for sending telemetry data.",
        required = true,
    ) {
        it!!.isNotEmpty() && it.startsWith("https://")
    }

    execute {
        document("AndroidManifest.xml").use { document ->
            val applicationNode =
                document
                    .getElementsByTagName("application")
                    .item(0) as Element

            // Remove all existing Sentry metadata entries to avoid conflicts
            val metadataNodeList = document.getElementsByTagName("meta-data");
            val nodesToRemove = mutableListOf<Element>()
            for (i in 0 until metadataNodeList.length) {
                val node = metadataNodeList.item(i) as Element
                if (node.getAttribute("android:name").startsWith("io.sentry")) {
                    nodesToRemove.add(node)
                }
            }
            nodesToRemove.forEach { node ->
                node.removeFromParent()
            }

            val manifest = document.getElementsByTagName("manifest").item(0) as Element
            val packageName = manifest.getAttribute("package")
            val packageVersion = manifest.getAttribute("android:versionName")
            val packageVersionCode = manifest.getAttribute("android:versionCode")
            val patchVersion = getPatchesBundleVersion()

            // Add Sentry metadata entries
            val metadata = mutableMapOf(
                "io.sentry.dsn" to sentryDsnOption.value!!,
                "io.sentry.release" to packageVersion,
                "io.sentry.dist" to packageVersionCode,
                "io.sentry.environment" to patchVersion,
                "io.sentry.auto-init" to "true",
                "io.sentry.send-default-pii" to "false",
                "io.sentry.traces.user-interaction.enable" to "true",
                "io.sentry.attach-screenshot" to "true",
                "io.sentry.attach-view-hierarchy" to "true",
                "io.sentry.session-replay.on-error-sample-rate" to "1.0",
                "io.sentry.session-replay.session-sample-rate" to "0.0",
            )

            applicationNode.appendChild(document.createElement("meta-data").apply {
                setAttribute("android:name", "io.sentry.dsn")
                setAttribute("android:value", sentryDsnOption.value!!)
            })
        }
    }
}