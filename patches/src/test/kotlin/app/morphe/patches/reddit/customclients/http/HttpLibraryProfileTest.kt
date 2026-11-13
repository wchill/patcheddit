/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.http

import app.morphe.patches.reddit.customclients.misc.extension.mapping.MethodId
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpLibraryProfileTest {
    private fun profile(name: String): HttpLibraryProfile {
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream("$name/mapping.txt"))
        return resource.reader().use(HttpLibraryProfile::read)
    }

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
    fun loadsFixtureProfilesForEachVariant() {
        for (name in listOf(
            "baconreader", "joey", "relay/free", "relay/pro",
            "syncforreddit", "redditisfun/free", "redditisfun/donation",
        )) {
            assertTrue(profile(name).mapping.classes.isNotEmpty(), name)
        }
    }

    @Test
    fun selectsTheProfileForEachAppVariant() {
        for ((client, packageName, expected) in listOf(
            Triple("relay", "free.reddit.news", "relay/free"),
            Triple("relay", "reddit.news", "relay/pro"),
            Triple("syncforreddit", "com.laurencedawson.reddit_sync", "syncforreddit"),
            Triple("redditisfun", "com.andrewshu.android.reddit", "redditisfun/free"),
            Triple("redditisfun", "com.andrewshu.android.redditdonation", "redditisfun/donation"),
        )) {
            assertEquals(expected, httpLibraryResourceNamespace(client, packageName))
        }
        assertFailsWith<IllegalStateException> { httpLibraryResourceNamespace("relay", "unknown") }
        for (packageName in listOf(
            "unknown", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev",
        )) {
            assertFailsWith<IllegalStateException> { httpLibraryResourceNamespace("syncforreddit", packageName) }
        }
        assertEquals("joey", httpLibraryResourceNamespace("joey", "o.o.joey"))
    }

    @Test
    fun usesRelayProSymbols() {
        val free = profile("relay/free")
        val pro = profile("relay/pro")
        val parameters = listOf("Lokhttp3/Call;", "Ljava/io/IOException;")
        assertEquals("onFailure", free.mapping.methods[MethodId("Lokhttp3/Callback;", "c", parameters, "V")])
        assertEquals("onFailure", pro.mapping.methods[MethodId("Lokhttp3/Callback;", "d", parameters, "V")])
        assertEquals(
            "cacheResponse",
            pro.mapping.methods[MethodId("Lokhttp3/Response;", "g", emptyList(), "Lokhttp3/Response;")],
        )
    }
}
