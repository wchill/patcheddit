/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpLibraryProfileTest {
    @Test
    fun keepsCoreAndSyntheticHelpersInertButRewritesAddonsAndAppCallbacks() {
        val profile = HttpLibraryProfile.read(StringReader("""
            # native-prefix: Lokhttp3/
            # native-prefix: Lnative/okio/
            # native-helper: Lnative/Synthetic;
            # caller-prefix: Lokhttp3/logging/
            # caller-class: Lokhttp3/JavaNetCookieJar;
            okhttp3.Request -> native.Request:
                java.lang.String method() -> a
        """.trimIndent()))
        assertTrue(profile.isNative("Lokhttp3/internal/RealCall;"))
        assertTrue(profile.isNative("Lnative/okio/Buffer;"))
        assertTrue(profile.isNative("Lnative/Synthetic;"))
        assertTrue(profile.isNative("Lnative/Request;"))
        assertFalse(profile.isNative("Lokhttp3/logging/HttpLoggingInterceptor;"))
        assertFalse(profile.isNative("Lokhttp3/JavaNetCookieJar;"))
        assertFalse(profile.isNative("Lapp/CustomRequestBody;"))
    }

    @Test
    fun loadsTheSixFixtureProfilesWithSeparateRifMappings() {
        for (profile in listOf(
            "baconreader", "joey", "relay", "syncforreddit", "redditisfun/free", "redditisfun/donation",
        )) {
            val resource = requireNotNull(javaClass.classLoader.getResourceAsStream("$profile/mapping.txt"))
            val parsed = resource.reader().use(HttpLibraryProfile::read)
            assertTrue(parsed.mapping.classes.isNotEmpty(), profile)
        }
    }
}
