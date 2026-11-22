/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.reddit.customclients.misc.extension.mapping.DexMappingRewriter
import app.morphe.patches.reddit.customclients.misc.extension.mapping.ProguardMapping
import app.morphe.util.ResourceGroup
import app.morphe.util.inputStreamFromBundledResource
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Files

private const val ACTIVITY = "app.morphe.extension.youtube.YouTubeActivity"
internal const val YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR = "Lapp/morphe/extension/youtube/YouTubeActivity;"
private const val UPSTREAM = "Lcom/pierfrancescosoffritti/androidyoutubeplayer/"
private const val PRIVATE = "Lapp/morphe/extension/youtube/libraries/"

internal val youtubePlayerPatch = patch(default = false) {
    dependsOn(addResourcesPatch(
        "youtube",
        ResourceGroup("raw", "patcheddit_youtube_player.html"),
    ))
    extendWith {
        val input = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream("extensions/youtube.mpe")) {
            "The YouTube player extension was not bundled."
        }
        val dex = input.use { DexBackedDexFile.fromInputStream(null, it.buffered()) }
        val definitions = dex.classes.toList()
        // Isolate the bundled player and Kotlin runtime from the host APK's libraries.
        val privateTypes = definitions.map { it.type }
            .filter { it.startsWith(UPSTREAM) || it.startsWith("Lkotlin/") }
            .associateWith { PRIVATE + it.removePrefix("L") }
        val rewriter = DexMappingRewriter(ProguardMapping.classNames(privateTypes), definitions)
        val pool = DexPool(dex.opcodes)
        rewriter.rewrite(definitions).forEach(pool::internClass)
        val output = MemoryDataStore()
        pool.writeTo(output)
        ByteArrayInputStream(output.data)
    }
    execute {
        checkNotNull(inputStreamFromBundledResource("settings/licenses", "Android-YouTube-Player-LICENSE.txt")).use { source ->
            val license = resourceFile("META-INF/patcheddit-android-youtube-player-LICENSE.txt", copy = false)
            Files.createDirectories(license.parentFile.toPath())
            license.outputStream().use(source::copyTo)
        }
        checkNotNull(inputStreamFromBundledResource("youtube", "values/patcheddit_youtube_strings.xml")).use { source ->
            document(source).use { strings ->
                document("res/values/strings.xml").use { target -> mergeMorpheResources(target, strings, "string") }
            }
        }
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as Element
            application.appendChild(manifest.createElement("activity").apply {
                setAttribute("android:name", ACTIVITY)
                setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                setAttribute("android:configChanges", "orientation|screenSize|keyboardHidden")
                setAttribute("android:exported", "false")
            })
        }

        val read = Fingerprint(
            definingClass = PRIVATE + UPSTREAM.removePrefix("L") + "core/player/views/WebViewYouTubePlayer;",
            filters = listOf(methodCall(definingClass = "Landroid/content/res/Resources;", name = "openRawResource")),
        ).match()
        // Build-time Android resource IDs do not match the host APK's IDs.
        read.method.replaceInstruction(
            read.instructionMatches.single().index,
            "invoke-static/range {p0 .. p0}, $YOUTUBE_PLAYER_ACTIVITY_DESCRIPTOR->openPlayerHtml(Landroid/view/View;)Ljava/io/InputStream;",
        )
    }
}
