/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.oauth;

import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonGenerator;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonParseException;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonParser;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class OAuthTokenValidator {
    private final OkHttpClient client;

    public OAuthTokenValidator() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build());
    }

    OAuthTokenValidator(OkHttpClient client) {
        this.client = client;
    }

    public VerifiedAccount validate(OAuthTokenPayload input, String clientId, String userAgent)
            throws IOException, OAuthTokenException {
        if (clientId == null || clientId.isEmpty() || userAgent == null || userAgent.isEmpty()) {
            throw new OAuthTokenException("Configure Boost's Reddit API settings before importing tokens.");
        }
        if (input.clientId != null && !input.clientId.equals(clientId)) {
            throw new OAuthTokenException(
                    "These tokens use a different OAuth client. Configure the matching client ID "
                            + "in Boost's API settings before importing."
            );
        }
        String originalUsername = identity(input.accessToken, userAgent, true);
        checkInterrupted();
        Request request = new Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("User-Agent", userAgent)
                .header("Authorization", Credentials.basic(clientId, ""))
                .post(new FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", input.refreshToken)
                        .build())
                .build();
        String access;
        String refresh;
        String scope;
        long seconds;
        Date expiration;
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 400 || response.code() == 401) {
                throw new OAuthTokenException(
                        "Reddit rejected the refresh token. It may be revoked or belong to a "
                                + "different OAuth client. No account was changed."
                );
            }
            requireSuccess(response);
            Map<String, String> data = json(response);
            access = OAuthTokenPayload.token(requiredString(data, "access_token"));
            if (!"bearer".equalsIgnoreCase(requiredString(data, "token_type"))) {
                throw new OAuthTokenException("Reddit returned an unsupported token type.");
            }
            scope = requiredString(data, "scope");
            String expires = data.get("expires_in");
            if (expires == null || !expires.matches("[0-9]{1,7}")) {
                throw new OAuthTokenException("Reddit did not return a valid token expiration.");
            }
            seconds = Long.parseLong(expires);
            // Native OAuthData multiplies expires_in using a signed 32-bit integer.
            if (seconds <= 0 || seconds > Integer.MAX_VALUE / 1000) {
                throw new OAuthTokenException("Reddit returned an unsupported token expiration.");
            }
            expiration = new Date(System.currentTimeMillis() + seconds * 1000);
            refresh = data.containsKey("refresh_token")
                    ? OAuthTokenPayload.token(requiredString(data, "refresh_token"))
                    : input.refreshToken;
        }
        String username = identity(access, userAgent, false);
        if ((originalUsername != null && !originalUsername.equalsIgnoreCase(username))
                || (input.username != null && !input.username.equalsIgnoreCase(username))) {
            throw new OAuthTokenException(
                    "The access token, refresh token or exported account name identifies a "
                            + "different account. No account was changed."
            );
        }
        checkInterrupted();
        OAuthTokenPayload tokens = new OAuthTokenPayload(access, refresh, username, clientId);
        StringWriter nativeData = new StringWriter();
        try (JsonGenerator writer = OAuthTokenPayload.JSON_FACTORY.createGenerator(nativeData)) {
            writer.writeStartObject();
            writer.writeStringField("access_token", access);
            writer.writeStringField("refresh_token", refresh);
            writer.writeNumberField("expires_in", seconds);
            writer.writeStringField("token_type", "bearer");
            writer.writeStringField("scope", scope);
            writer.writeEndObject();
        }
        return new VerifiedAccount(tokens, nativeData.toString(), expiration);
    }

    private String identity(String accessToken, String userAgent, boolean allowExpired)
            throws IOException, OAuthTokenException {
        checkInterrupted();
        Request request = new Request.Builder()
                .url("https://oauth.reddit.com/api/v1/me")
                .header("User-Agent", userAgent)
                .header("Authorization", "bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (allowExpired && response.code() == 401) return null;
            if (response.code() == 401 || response.code() == 403) {
                throw new OAuthTokenException(
                        "Reddit could not identify this account. Tokens must be valid and include "
                                + "the identity permission. No account was changed."
                );
            }
            requireSuccess(response);
            String username = requiredString(json(response), "name");
            if (!username.matches("[A-Za-z0-9_-]{1,32}")) {
                throw new OAuthTokenException("Reddit returned an invalid account name.");
            }
            return username;
        }
    }

    private static void requireSuccess(Response response) throws OAuthTokenException {
        if (!response.isSuccessful()) {
            throw new OAuthTokenException(
                    "Reddit returned HTTP " + response.code()
                            + ". Check your connection and API settings, then retry. No account was changed."
            );
        }
    }

    private static Map<String, String> json(Response response) throws IOException, OAuthTokenException {
        ResponseBody body = response.body();
        if (body == null) throw new OAuthTokenException("Reddit returned an empty response.");
        final String text;
        try (Reader reader = body.charStream()) {
            text = OAuthTokenPayload.read(reader);
        }
        Map<String, String> values = new HashMap<>();
        Set<String> fields = new HashSet<>();
        try (JsonParser parser = OAuthTokenPayload.JSON_FACTORY.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new OAuthTokenException("Reddit returned an invalid response.");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new OAuthTokenException("Reddit returned incomplete JSON.");
                }
                String field = parser.getCurrentName();
                if (!fields.add(field)) throw new OAuthTokenException("Reddit returned a duplicate JSON field.");
                JsonToken value = parser.nextToken();
                if (value == null) throw new OAuthTokenException("Reddit returned incomplete JSON.");
                if ("expires_in".equals(field) && value.isNumeric()) {
                    values.put(field, parser.getText());
                } else if (("access_token".equals(field) || "refresh_token".equals(field)
                        || "token_type".equals(field) || "scope".equals(field) || "name".equals(field))
                        && value == JsonToken.VALUE_STRING) {
                    values.put(field, parser.getText());
                } else if ("refresh_token".equals(field) && value != JsonToken.VALUE_NULL) {
                    throw new OAuthTokenException("Reddit returned an invalid refresh token.");
                } else {
                    parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw new OAuthTokenException("Reddit returned trailing JSON data.");
            }
            return values;
        } catch (JsonParseException error) {
            throw new OAuthTokenException("Reddit returned invalid JSON.");
        }
    }

    private static String requiredString(Map<String, String> data, String field) throws OAuthTokenException {
        String value = data.get(field);
        if (value == null || value.isEmpty()) {
            throw new OAuthTokenException("Reddit's response is missing required account or token data.");
        }
        return value;
    }

    static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Token transfer cancelled.");
    }

    public static final class VerifiedAccount {
        public final OAuthTokenPayload tokens;
        public final String oauthData;
        public final Date expirationDate;

        private VerifiedAccount(OAuthTokenPayload tokens, String oauthData, Date expirationDate) {
            this.tokens = tokens;
            this.oauthData = oauthData;
            this.expirationDate = expirationDate;
        }
    }
}
