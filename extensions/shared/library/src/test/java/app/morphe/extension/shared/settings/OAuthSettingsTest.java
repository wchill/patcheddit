/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void savesLegacyOptionsForNativeBoostAndSharedApiSettingsWithoutOverwritingOverrides() {
        for (String[] keys : new String[][] {
                { "pref_dev_reddit_client_id", "pref_dev_reddit_redirect_url", "pref_dev_reddit_user_agent" },
                { OAuthSettings.CLIENT_ID, OAuthSettings.REDIRECT_URI, OAuthSettings.USER_AGENT }
        }) {
            Map<String, Object> disk = new HashMap<>();
            disk.put(keys[1], "existing://callback");
            disk.put(keys[2], " ");
            String[] legacy = { "abcdefghijklmnopqrstuv", "legacy://callback", "legacy-agent" };
            assertTrue(OAuthSettings.persistPatchOptions(preferences(disk), keys, legacy));
            assertEquals(legacy[0], disk.get(keys[0]));
            assertEquals("existing://callback", disk.get(keys[1]));
            assertEquals(legacy[2], disk.get(keys[2]));

            SharedPreferences reopened = preferences(disk);
            assertEquals(legacy[0], reopened.getString(keys[0], "new-patch-default"));
            // A later repatch or a deliberately cleared override must not rerun the migration.
            reopened.edit().putString(keys[2], "").commit();
            assertTrue(OAuthSettings.persistPatchOptions(reopened, keys,
                    new String[] { "new-client", "new://callback", "new-agent" }));
            assertEquals(legacy[0], disk.get(keys[0]));
            assertEquals("", disk.get(keys[2]));
        }
    }

    private static SharedPreferences preferences(Map<String, Object> disk) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[] { SharedPreferences.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getString":
                        case "getBoolean":
                            return disk.getOrDefault(args[0], args[1]);
                        case "edit":
                            Map<String, Object> pending = new HashMap<>();
                            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                    new Class<?>[] { SharedPreferences.Editor.class }, (editor, action, values) -> {
                                        switch (action.getName()) {
                                            case "putString":
                                            case "putBoolean":
                                                pending.put((String) values[0], values[1]);
                                                return editor;
                                            case "commit":
                                                disk.putAll(pending);
                                                return true;
                                            default: throw new AssertionError(action.getName());
                                        }
                                    });
                        default: throw new AssertionError(method.getName());
                    }
                });
    }
}
