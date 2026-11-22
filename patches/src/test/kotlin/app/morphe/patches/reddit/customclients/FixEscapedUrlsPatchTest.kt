/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Patch
import app.morphe.patches.reddit.customclients.baconreader.api.redditApiPatch as baconReaderApi
import app.morphe.patches.reddit.customclients.baconreader.fix.links.fixEscapedUrlsPatch as baconReaderLinks
import app.morphe.patches.reddit.customclients.boostforreddit.api.redditApiPatch as boostApi
import app.morphe.patches.reddit.customclients.boostforreddit.fix.links.fixEscapedUrlsPatch as boostLinks
import app.morphe.patches.reddit.customclients.infinity.api.redditApiPatch as infinityApi
import app.morphe.patches.reddit.customclients.infinity.fix.links.fixEscapedUrlsPatch as infinityLinks
import app.morphe.patches.reddit.customclients.infinity.fix.links.isNativeResponseBody
import app.morphe.patches.reddit.customclients.joey.api.redditApiPatch as joeyApi
import app.morphe.patches.reddit.customclients.joey.fix.links.fixEscapedUrlsPatch as joeyLinks
import app.morphe.patches.reddit.customclients.redditisfun.api.redditApiPatch as rifApi
import app.morphe.patches.reddit.customclients.redditisfun.fix.links.fixEscapedUrlsPatch as rifLinks
import app.morphe.patches.reddit.customclients.relay.api.redditApiPatch as relayApi
import app.morphe.patches.reddit.customclients.relay.fix.links.fixEscapedUrlsPatch as relayLinks
import app.morphe.patches.reddit.customclients.sync.syncforreddit.api.redditApiPatch as syncApi
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.links.fixEscapedUrlsPatch as syncLinks
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixEscapedUrlsPatchTest {
    @Test
    fun recognizesInfinityResponseBodiesAfterR8InlinesTheirCharsetHelper() {
        assertTrue(isNativeResponseBody(responseBody("Ljava/io/InputStream;", "J")))
        assertTrue(isNativeResponseBody(responseBody("Ljava/io/InputStream;", "J", "Ljava/nio/charset/Charset;")))
    }

    @Test
    fun rejectsCloseablesMissingTheResponseBodyStreamOrAbstractLength() {
        assertFalse(isNativeResponseBody(responseBody("Ljava/io/InputStream;")))
        assertFalse(isNativeResponseBody(responseBody("J")))
    }

    private fun responseBody(vararg returns: String): ClassDef {
        val access = AccessFlags.PUBLIC.value or AccessFlags.ABSTRACT.value
        return ImmutableClassDef(
            "Lnative/ResponseBody;", access, "Ljava/lang/Object;", listOf("Ljava/io/Closeable;"),
            null, emptyList(), emptyList(), returns.mapIndexed { index, type ->
                ImmutableMethod(
                    "Lnative/ResponseBody;", "method$index", emptyList(), type, access,
                    emptySet(), emptySet(), null,
                )
            },
        )
    }

    @Test
    fun exposesAnIndependentSettingsEnabledFeatureForEveryRedditClient() {
        for ((feature, api) in listOf(
            baconReaderLinks to baconReaderApi, boostLinks to boostApi, infinityLinks to infinityApi,
            joeyLinks to joeyApi, rifLinks to rifApi, relayLinks to relayApi, syncLinks to syncApi,
        )) {
            assertEquals("Fix escaped URLs", feature.name)
            assertEquals("Links", feature.category)
            assertEquals(api.compatibility, feature.compatibility)
            assertTrue(morpheSettingsResourcesPatch in dependencies(feature))
            assertFalse(feature in dependencies(api))
            assertFalse(api in dependencies(feature))
        }
    }

    private fun dependencies(patch: Patch): Set<Patch> = buildSet {
        fun visit(current: Patch) {
            if (add(current)) current.dependencies.forEach(::visit)
        }
        visit(patch)
    }
}
