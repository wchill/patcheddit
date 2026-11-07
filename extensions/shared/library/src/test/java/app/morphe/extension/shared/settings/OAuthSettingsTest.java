/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class OAuthSettingsTest {
    @Test
    public void validatesAllClientIdCharactersAndTheExactLengthForBothPreferenceStores() {
        for (String key : new String[] { OAuthSettings.CLIENT_ID, "pref_dev_reddit_client_id" }) {
            for (String value : new String[] { "", "abcdefghijklmnopqrstuv", "1234567890_-1234567890" }) {
                assertNull(OAuthSettings.validationError(key, value));
            }
            for (String value : new String[] { "short", "a".repeat(21), "a".repeat(23),
                    "a".repeat(21) + "!", " " + "a".repeat(22), "a".repeat(22) + " " }) {
                assertNotNull(OAuthSettings.validationError(key, value));
            }
        }
    }

    @Test
    public void acceptsCustomSchemesAndUnderscoredHostsWithoutAllowingUserInformation() {
        for (String key : new String[] { OAuthSettings.REDIRECT_URI, "pref_dev_reddit_redirect_url" }) {
            for (String value : new String[] { "", "redreader://rr_oauth_redir",
                    "https://127.0.0.1:65023/authorize_callback", "dbrady://relay" }) {
                assertNull(OAuthSettings.validationError(key, value));
            }
            for (String value : new String[] { "localhost", " https://example.com", "https://invalid host",
                    "https://user@example.com", "redreader://user@rr_oauth_redir" }) {
                assertNotNull(OAuthSettings.validationError(key, value));
            }
        }
    }

    @Test
    public void rejectsHeaderInjectionControlCharactersAndNonAsciiUserAgents() {
        for (String key : new String[] { OAuthSettings.USER_AGENT, "pref_dev_reddit_user_agent" }) {
            assertNull(OAuthSettings.validationError(key, "org.quantumbadger.redreader/1.27"));
            assertNull(OAuthSettings.validationError(key, ""));
            for (String invalid : new String[] { "agent\r\nInjected: header", "agent\n", "agent\u0000",
                    "agent\u007f", "agent\u00a0" }) {
                assertNotNull(OAuthSettings.validationError(key, invalid));
            }
        }
    }

    @Test
    public void changesOnlyTheClientIdInTheNativeAuthorizationUrl() {
        String original = "https://www.reddit.com/api/v1/authorize.compact?client_id=native&response_type=code&state=";
        assertEquals("https://www.reddit.com/api/v1/authorize.compact?client_id=abcdefghijklmnopqrstuv&response_type=code&state=",
                OAuthSettings.replaceClientId(original, "abcdefghijklmnopqrstuv"));
        assertEquals(original, OAuthSettings.replaceClientId(original, "native"));
        assertThrows(IllegalArgumentException.class,
                () -> OAuthSettings.replaceClientId("https://www.reddit.com/login", "abcdefghijklmnopqrstuv"));
    }

    @Test
    public void rejectsUnknownPreferenceKeysEvenWhenTheValueIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> OAuthSettings.validationError("unknown", ""));
    }
}
