/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients

import app.morphe.patcher.patch.Option
import app.morphe.patcher.patch.OptionException
import app.morphe.patches.reddit.customclients.boostforreddit.api.spoofClientPatch as boostPatch
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpoofClientDefaultsTest {
    private fun clientIdOption(): Option<String> {
        lateinit var option: Option<String>
        spoofClientPatch("Test OAuth defaults", "Tests shared OAuth defaults") { clientId, _, _ ->
            option = clientId
        }
        return option
    }

    @Test
    fun decodesTheDefaultOnlyWhenApplyingIt() {
        val option = clientIdOption()
        assertNull(option.default)
        assertNull(option.value)
        assertFalse(option.required)
        applyDefaultClientId(option)
        val value = requireNotNull(option.value)
        assertTrue(value.matches(Regex("^[\\w-]{22}$")))
        assertEquals("eUgwYVRuSkV0NnFVZ0duODM1QjR2Zw==",
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8)))
        assertNull(option.default)
    }

    @Test
    fun missingAndBlankValuesUseTheSameDefault() {
        val option = clientIdOption()
        applyDefaultClientId(option)
        val expected = option.value
        for (blank in listOf(null, "", " \t\r\n ", "\u00a0")) {
            option.value = blank
            applyDefaultClientId(option)
            assertEquals(expected, option.value)
        }
    }

    @Test
    fun customValuesAndSubsequentEditsArePreserved() {
        val option = clientIdOption()
        for (value in listOf("abcdefghijklmnopqrstuv", "ABCDEFGHIJKLMNOPQRSTUV", "1234567890_-1234567890")) {
            option.value = value
            applyDefaultClientId(option)
            assertEquals(value, option.value)
        }
        option.value = null
        applyDefaultClientId(option)
        assertTrue(!option.value.isNullOrBlank())
    }

    @Test
    fun rejectsMalformedCustomClientIds() {
        val option = clientIdOption()
        for (invalid in listOf("short", "a".repeat(21), "a".repeat(23), "a".repeat(21) + "!", " " + "a".repeat(22))) {
            assertFailsWith<OptionException.ValueValidationException> { option.value = invalid }
        }
    }

    @Test
    fun boostUsesRedReader127MetadataWithoutTheDecodedId() {
        val clientId = boostPatch.options["client-id"]
        assertNull(clientId.default)
        assertNull(clientId.value)
        assertFalse(clientId.required)
        assertEquals("redreader://rr_oauth_redir", boostPatch.options["redirect-uri"].default)
        assertEquals("org.quantumbadger.redreader/1.27", boostPatch.options["user-agent"].default)
    }
}
