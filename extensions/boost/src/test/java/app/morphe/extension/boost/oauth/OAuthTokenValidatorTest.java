/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.oauth;

import app.morphe.extension.vendored.com.fasterxml.jackson.databind.JsonNode;
import app.morphe.extension.vendored.com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class OAuthTokenValidatorTest {
    private static final String REFRESHED = "{\"access_token\":\"fixture-fresh-access\","
            + "\"token_type\":\"bearer\",\"scope\":\"identity read\",\"expires_in\":3600}";
    private static final String ME = "{\"name\":\"Fixture_User\",\"is_mod\":false,"
            + "\"features\":{\"nested\":[true,null,{\"name\":\"not-the-account\"}]}}";
    private final List<Request> requests = new ArrayList<>();

    private OAuthTokenValidator validator(int[] statuses, String... bodies) {
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    int index = requests.size();
                    requests.add(request);
                    if (index >= statuses.length) throw new AssertionError("Unexpected request");
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(statuses[index])
                            .message("Fixture response")
                            .body(ResponseBody.create(bodies[index], MediaType.get("application/json")))
                            .build();
                })
                .build();
        return new OAuthTokenValidator(client);
    }

    private OAuthTokenPayload input(String username, String clientId) throws OAuthTokenException {
        return new OAuthTokenPayload("fixture-old-access", "fixture-refresh", username, clientId);
    }

    @Test
    public void validatesBothTokenOwnersAndPreservesNativeRefreshData() throws Exception {
        long started = System.currentTimeMillis();
        OAuthTokenValidator.VerifiedAccount result = validator(new int[]{200, 200, 200}, ME, REFRESHED, ME)
                .validate(input("fixture_user", "fixture-client"), "fixture-client", "fixture-agent/1");
        assertEquals("Fixture_User", result.tokens.username);
        assertEquals("fixture-fresh-access", result.tokens.accessToken);
        assertEquals("fixture-refresh", result.tokens.refreshToken);
        assertEquals("fixture-client", result.tokens.clientId);
        JsonNode data = new ObjectMapper().readTree(result.oauthData);
        assertEquals("identity read", data.get("scope").asText());
        assertEquals("fixture-refresh", data.get("refresh_token").asText());
        assertEquals("bearer", data.get("token_type").asText());
        assertEquals(3600, data.get("expires_in").asInt());
        assertTrue(result.expirationDate.getTime() >= started + 3600000);
        assertEquals("https://oauth.reddit.com/api/v1/me", requests.get(0).url().toString());
        assertEquals("bearer fixture-old-access", requests.get(0).header("Authorization"));
        Request refresh = requests.get(1);
        assertEquals("https://www.reddit.com/api/v1/access_token", refresh.url().toString());
        assertEquals(Credentials.basic("fixture-client", ""), refresh.header("Authorization"));
        Buffer body = new Buffer();
        refresh.body().writeTo(body);
        assertEquals("grant_type=refresh_token&refresh_token=fixture-refresh", body.readUtf8());
        assertEquals("bearer fixture-fresh-access", requests.get(2).header("Authorization"));
        for (Request request : requests) assertEquals("fixture-agent/1", request.header("User-Agent"));
    }

    @Test
    public void restoresAnExpiredAccessTokenUsingTheRefreshToken() throws Exception {
        OAuthTokenValidator.VerifiedAccount result =
                validator(new int[]{401, 200, 200}, "{}", REFRESHED, ME)
                        .validate(input(null, null), "fixture-client", "fixture-agent");
        assertEquals("Fixture_User", result.tokens.username);
        assertEquals("fixture-fresh-access", result.tokens.accessToken);
    }

    @Test
    public void retainsRotatedRefreshTokens() throws Exception {
        String rotated = REFRESHED.replace("}", ",\"refresh_token\":\"fixture-rotated-refresh\"}");
        OAuthTokenValidator.VerifiedAccount result = validator(new int[]{200, 200, 200}, ME, rotated, ME)
                .validate(input(null, null), "fixture-client", "fixture-agent");
        assertEquals("fixture-rotated-refresh", result.tokens.refreshToken);
        assertEquals("fixture-rotated-refresh",
                new ObjectMapper().readTree(result.oauthData).get("refresh_token").asText());
    }

    @Test
    public void rejectsDifferentOAuthClientsBeforeMakingRequests() throws Exception {
        OAuthTokenValidator validator = validator(new int[]{});
        assertThrows(OAuthTokenException.class, () ->
                validator.validate(input(null, "other-client"), "fixture-client", "fixture-agent"));
        assertTrue(requests.isEmpty());
    }

    @Test
    public void rejectsTokensForDifferentAccounts() throws Exception {
        OAuthTokenValidator validator = validator(new int[]{200, 200, 200},
                "{\"name\":\"Different_User\"}", REFRESHED, ME);
        assertThrows(OAuthTokenException.class, () ->
                validator.validate(input(null, null), "fixture-client", "fixture-agent"));
    }

    @Test
    public void verifiesExportedUsernameEvenWhenAccessTokenExpired() throws Exception {
        OAuthTokenValidator validator = validator(new int[]{401, 200, 200}, "{}", REFRESHED, ME);
        assertThrows(OAuthTokenException.class, () ->
                validator.validate(input("Different_User", null), "fixture-client", "fixture-agent"));
    }

    @Test
    public void rejectsRevokedRefreshTokensWithoutEchoingServerContent() throws Exception {
        OAuthTokenValidator validator = validator(new int[]{200, 400},
                ME, "{\"error\":\"fixture-refresh should never appear in an error\"}");
        OAuthTokenException error = assertThrows(OAuthTokenException.class, () ->
                validator.validate(input(null, null), "fixture-client", "fixture-agent"));
        assertEquals(2, requests.size());
        assertFalse(error.getMessage().contains("fixture-refresh"));
    }

    @Test
    public void rejectsResponsesWithoutIdentityPermission() throws Exception {
        OAuthTokenValidator validator = validator(new int[]{200, 200, 403}, ME, REFRESHED, "{}");
        assertThrows(OAuthTokenException.class, () ->
                validator.validate(input(null, null), "fixture-client", "fixture-agent"));
    }

    @Test
    public void rejectsInvalidTokenResponsesAndNativeExpirationOverflow() throws Exception {
        String[] invalid = {
                "{}", "null", "[1]", "{",
                REFRESHED.replace("\"fixture-fresh-access\"", "42"),
                REFRESHED.replace("\"bearer\"", "\"unsupported\""),
                REFRESHED.replace("\"identity read\"", "null"),
                REFRESHED.replace("}", ",\"refresh_token\":42}"),
                REFRESHED.replace("}", ",\"refresh_token\":\"\"}"),
                REFRESHED.replace("}", ",\"access_token\":\"duplicate\"}"),
                REFRESHED + "{}",
                REFRESHED.replace("3600", "0"),
                REFRESHED.replace("3600", "-1"),
                REFRESHED.replace("3600", "2147484"),
                REFRESHED.replace("3600", "1.5"),
                REFRESHED.replace("3600", "\"3600\""),
        };
        for (String body : invalid) {
            requests.clear();
            OAuthTokenValidator validator = validator(new int[]{200, 200}, ME, body);
            OAuthTokenException error = assertThrows(OAuthTokenException.class, () ->
                    validator.validate(input(null, null), "fixture-client", "fixture-agent"));
            assertFalse(error.getMessage().contains("fixture-fresh-access"));
        }
    }

    @Test
    public void rejectsRedirectsAndRateLimits() throws Exception {
        for (int status : new int[]{302, 403, 429, 500}) {
            requests.clear();
            OAuthTokenValidator validator = validator(new int[]{status}, "{}");
            assertThrows(OAuthTokenException.class, () ->
                    validator.validate(input(null, null), "fixture-client", "fixture-agent"));
            assertEquals(1, requests.size());
        }
    }
}
