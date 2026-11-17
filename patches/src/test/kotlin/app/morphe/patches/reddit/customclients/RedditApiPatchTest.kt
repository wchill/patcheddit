/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import app.morphe.patches.reddit.customclients.baconreader.api.redditApiPatch as baconReaderApi
import app.morphe.patches.reddit.customclients.baconreader.api.legacySpoofClientPatch as baconReaderLegacy
import app.morphe.patches.reddit.customclients.baconreader.api.spoofClientPatch as baconReaderSpoof
import app.morphe.patches.reddit.customclients.baconreader.fix.apilookups.fixRedditApiLookups as baconReaderLookups
import app.morphe.patches.reddit.customclients.baconreader.fix.gallery.fixFailedGalleryImagesPatch as baconReaderGallery
import app.morphe.patches.reddit.customclients.baconreader.fix.login.modifyLoginWebView as baconReaderLogin
import app.morphe.patches.reddit.customclients.baconreader.fix.morecomments.fixMoreComments as baconReaderComments
import app.morphe.patches.reddit.customclients.boostforreddit.api.redditApiPatch as boostApi
import app.morphe.patches.reddit.customclients.boostforreddit.api.legacySpoofClientPatch as boostLegacy
import app.morphe.patches.reddit.customclients.boostforreddit.api.spoofClientPatch as boostSpoof
import app.morphe.patches.reddit.customclients.boostforreddit.fix.apilookups.fixRedditApiLookups as boostLookups
import app.morphe.patches.reddit.customclients.boostforreddit.fix.feed.fixRAll as boostRAll
import app.morphe.patches.reddit.customclients.boostforreddit.fix.gallery.fixFailedGalleryImagesPatch as boostGallery
import app.morphe.patches.reddit.customclients.boostforreddit.fix.login.modifyLoginWebView as boostLogin
import app.morphe.patches.reddit.customclients.boostforreddit.fix.slink.fixSlinksPatch as boostLinks
import app.morphe.patches.reddit.customclients.infinity.api.redditApiPatch as infinityApi
import app.morphe.patches.reddit.customclients.infinity.api.legacySpoofClientPatch as infinityLegacy
import app.morphe.patches.reddit.customclients.infinity.api.spoofClientPatch as infinitySpoof
import app.morphe.patches.reddit.customclients.infinity.fix.login.modifyLoginWebView as infinityLogin
import app.morphe.patches.reddit.customclients.joey.api.redditApiPatch as joeyApi
import app.morphe.patches.reddit.customclients.joey.api.legacySpoofClientPatch as joeyLegacy
import app.morphe.patches.reddit.customclients.joey.api.spoofClientPatch as joeySpoof
import app.morphe.patches.reddit.customclients.joey.fix.apilookups.fixRedditApiLookups as joeyLookups
import app.morphe.patches.reddit.customclients.joey.fix.gallery.fixFailedGalleryImagesPatch as joeyGallery
import app.morphe.patches.reddit.customclients.joey.fix.login.modifyLoginWebView as joeyLogin
import app.morphe.patches.reddit.customclients.joey.fix.morecomments.fixMoreComments as joeyComments
import app.morphe.patches.reddit.customclients.redditisfun.api.redditApiPatch as rifApi
import app.morphe.patches.reddit.customclients.redditisfun.api.legacySpoofClientPatch as rifLegacy
import app.morphe.patches.reddit.customclients.redditisfun.api.spoofClientPatch as rifSpoof
import app.morphe.patches.reddit.customclients.redditisfun.fix.apilookups.fixRedditApiLookups as rifLookups
import app.morphe.patches.reddit.customclients.redditisfun.fix.gallery.fixFailedGalleryImagesPatch as rifGallery
import app.morphe.patches.reddit.customclients.redditisfun.fix.login.modifyLoginWebView as rifLogin
import app.morphe.patches.reddit.customclients.redditisfun.fix.morecomments.fixMoreComments as rifComments
import app.morphe.patches.reddit.customclients.relay.api.redditApiPatch as relayApi
import app.morphe.patches.reddit.customclients.relay.api.legacySpoofClientPatch as relayLegacy
import app.morphe.patches.reddit.customclients.relay.api.spoofClientPatch as relaySpoof
import app.morphe.patches.reddit.customclients.relay.fix.apilookups.fixRedditApiLookups as relayLookups
import app.morphe.patches.reddit.customclients.relay.fix.gallery.fixFailedGalleryImagesPatch as relayGallery
import app.morphe.patches.reddit.customclients.relay.fix.login.modifyLoginWebView as relayLogin
import app.morphe.patches.reddit.customclients.relay.fix.morecomments.fixMoreComments as relayComments
import app.morphe.patches.reddit.customclients.relay.fix.slink.fixSLinksPatch as relayLinks
import app.morphe.patches.reddit.customclients.relay.fix.sidebar.fixSidebarPatch as relaySidebar
import app.morphe.patches.reddit.customclients.sync.syncforreddit.api.redditApiPatch as syncApi
import app.morphe.patches.reddit.customclients.sync.syncforreddit.api.legacySpoofClientPatch as syncLegacy
import app.morphe.patches.reddit.customclients.sync.syncforreddit.api.spoofClientPatch as syncSpoof
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.apilookups.fixRedditApiLookups as syncLookups
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery.fixFailedGalleryImagesPatch as syncGallery
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.login.modifyLoginWebView as syncLogin
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.morecomments.fixMoreComments as syncComments
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.slink.fixSLinksPatch as syncLinks
import app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user.useUserEndpointPatch

class RedditApiPatchTest {
    private val clients = listOf(
        baconReaderApi to baconReaderSpoof,
        boostApi to boostSpoof,
        infinityApi to infinitySpoof,
        joeyApi to joeySpoof,
        rifApi to rifSpoof,
        relayApi to relaySpoof,
        syncApi to syncSpoof,
    )

    @Test
    fun bundlesExactlyTheApplicableFixesIncludingGalleryRepairsExceptForInfinity() {
        val expected = mapOf(
            baconReaderApi to setOf(baconReaderSpoof, baconReaderLogin, baconReaderLookups, baconReaderGallery, baconReaderComments),
            boostApi to setOf(boostSpoof, boostLogin, boostLookups, boostGallery, boostRAll, boostLinks),
            infinityApi to setOf(infinitySpoof, infinityLogin),
            joeyApi to setOf(joeySpoof, joeyLogin, joeyLookups, joeyGallery, joeyComments),
            rifApi to setOf(rifSpoof, rifLogin, rifLookups, rifGallery, rifComments),
            relayApi to setOf(relaySpoof, relayLogin, relayLookups, relayGallery, relayComments, relayLinks, relaySidebar),
            syncApi to setOf(syncSpoof, syncLogin, syncLookups, syncGallery, syncComments, syncLinks, useUserEndpointPatch),
        )
        for ((meta, spoof) in clients) {
            assertEquals(expected.getValue(meta), meta.dependencies)
            assertEquals(spoof.compatibility, meta.compatibility)
            assertEquals("Reddit API fixes", meta.name)
            assertTrue(meta.default)
            assertTrue(meta.dependencies.all { it.name == null })
        }
    }

    @Test
    fun mandatoryFixesDoNotRegisterSettingsAndOnlyNonBoostClientsExposeSpoofControls() {
        for ((meta, spoof) in clients) {
            assertEquals(spoof != boostSpoof, morpheSettingsResourcesPatch in dependencies(spoof))
            for (fix in meta.dependencies - spoof) {
                assertFalse(morpheSettingsResourcesPatch in dependencies(fix))
            }
        }
    }

    @Test
    fun forwardsOAuthOverridesToHiddenDependenciesBeforeTheyExecuteIncludingInfinitysManifestOption() {
        for ((meta, spoof) in clients) {
            assertEquals(setOf("client-id", "redirect-uri", "user-agent"), meta.options.keys)
            for ((key, option) in meta.options) assertSame(spoof.options[key], option)
            try {
                meta.options["client-id"] = "abcdefghijklmnopqrstuv"
                meta.options["redirect-uri"] = "custom://callback"
                meta.options["user-agent"] = "custom-agent"
                assertEquals("abcdefghijklmnopqrstuv", spoof.options["client-id"].value)
                assertEquals("custom://callback", spoof.options["redirect-uri"].value)
                assertEquals("custom-agent", spoof.options["user-agent"].value)
            } finally {
                meta.options.values.forEach { it.reset() }
            }
            assertNull(meta.options["client-id"].default)
        }
    }

    @Test
    fun legacyPatchIsOptInAndForwardsSavedOptionsToTheCompleteApiBundle() {
        for ((legacy, api) in listOf(
            baconReaderLegacy to baconReaderApi, boostLegacy to boostApi, infinityLegacy to infinityApi,
            joeyLegacy to joeyApi, rifLegacy to rifApi, relayLegacy to relayApi, syncLegacy to syncApi,
        )) {
            assertEquals("Spoof client", legacy.name)
            assertFalse(legacy.default)
            assertEquals(setOf(api, morpheSettingsContextPatch), legacy.dependencies)
            assertEquals(api.compatibility, legacy.compatibility)
            assertFalse(legacy in dependencies(api))
            for ((key, option) in api.options) assertSame(option, legacy.options[key])
            try {
                legacy.options["client-id"] = "abcdefghijklmnopqrstuv"
                legacy.options["redirect-uri"] = "legacy://callback"
                legacy.options["user-agent"] = "legacy-agent"
                assertEquals("abcdefghijklmnopqrstuv", api.options["client-id"].value)
                assertEquals("legacy://callback", api.options["redirect-uri"].value)
                assertEquals("legacy-agent", api.options["user-agent"].value)
            } finally {
                legacy.options.values.forEach { it.reset() }
            }
        }
    }

    private fun dependencies(patch: Patch<*>): Set<Patch<*>> = buildSet {
        fun visit(current: Patch<*>) {
            if (add(current)) current.dependencies.forEach(::visit)
        }
        visit(patch)
    }
}
