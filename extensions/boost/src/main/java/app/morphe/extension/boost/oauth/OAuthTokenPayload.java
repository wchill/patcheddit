/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.oauth;

import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonFactory;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonGenerator;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonParser;
import app.morphe.extension.vendored.com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

public final class OAuthTokenPayload {
    public static final int MAX_CHARACTERS = 65536;
    static final JsonFactory JSON_FACTORY = new JsonFactory();

    public final String accessToken;
    public final String refreshToken;
    public final String username;
    public final String clientId;

    public OAuthTokenPayload(String accessToken, String refreshToken, String username, String clientId)
            throws OAuthTokenException {
        this.accessToken = token(accessToken);
        this.refreshToken = token(refreshToken);
        if (username != null && !username.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new OAuthTokenException("The account name in the token JSON is invalid.");
        }
        if (clientId != null && !clientId.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new OAuthTokenException("The OAuth client ID in the token JSON is invalid.");
        }
        this.username = username;
        this.clientId = clientId;
    }

    static String token(String value) throws OAuthTokenException {
        if (value == null || value.isEmpty() || value.length() > 16384) {
            throw new OAuthTokenException("Both access_token and refresh_token must contain a token.");
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) <= 0x20 || value.charAt(i) >= 0x7f) {
                throw new OAuthTokenException("Tokens cannot contain whitespace or non-ASCII characters.");
            }
        }
        return value;
    }

    public static String read(Reader reader) throws IOException, OAuthTokenException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (text.length() + count > MAX_CHARACTERS) {
                throw new OAuthTokenException("Token JSON must not exceed 64 KiB of text.");
            }
            text.append(buffer, 0, count);
        }
        return text.toString();
    }

    public static OAuthTokenPayload parse(String text) throws OAuthTokenException {
        if (text == null || text.length() > MAX_CHARACTERS) {
            throw new OAuthTokenException("Token JSON is missing or exceeds 64 KiB of text.");
        }
        String access = null;
        String refresh = null;
        String username = null;
        String client = null;
        Set<String> fields = new HashSet<>();
        try (JsonParser parser = JSON_FACTORY.createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new OAuthTokenException("Token JSON must contain an object.");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new OAuthTokenException("Token JSON is incomplete or has an invalid field type.");
                }
                String name = parser.getCurrentName();
                if (!fields.add(name)) {
                    throw new OAuthTokenException("Token JSON contains a duplicate field.");
                }
                if (parser.nextToken() == null) {
                    throw new OAuthTokenException("Token JSON is incomplete.");
                }
                switch (name) {
                    case "version":
                        if (parser.getCurrentToken() != JsonToken.VALUE_NUMBER_INT || !"1".equals(parser.getText())) {
                            throw new OAuthTokenException("This token export version is not supported.");
                        }
                        break;
                    case "access_token":
                        access = string(parser);
                        break;
                    case "refresh_token":
                        refresh = string(parser);
                        break;
                    case "username":
                        username = string(parser);
                        break;
                    case "client_id":
                        client = string(parser);
                        break;
                    default:
                        throw new OAuthTokenException(
                                "Unsupported token JSON field. Use access_token, refresh_token, "
                                        + "and optionally username, client_id and version."
                        );
                }
            }
            if (parser.nextToken() != null) {
                throw new OAuthTokenException("Token JSON contains trailing data.");
            }
        } catch (IOException error) {
            throw new OAuthTokenException("Token JSON is not valid JSON.");
        }
        return new OAuthTokenPayload(access, refresh, username, client);
    }

    private static String string(JsonParser parser) throws IOException, OAuthTokenException {
        if (parser.getCurrentToken() != JsonToken.VALUE_STRING) {
            throw new OAuthTokenException("Token JSON fields must contain strings, not null or other types.");
        }
        return parser.getText();
    }

    public String toJson() throws IOException {
        StringWriter output = new StringWriter();
        try (JsonGenerator writer = JSON_FACTORY.createGenerator(output)) {
            writer.useDefaultPrettyPrinter();
            writer.writeStartObject();
            writer.writeNumberField("version", 1);
            if (username != null) writer.writeStringField("username", username);
            if (clientId != null) writer.writeStringField("client_id", clientId);
            writer.writeStringField("access_token", accessToken);
            writer.writeStringField("refresh_token", refreshToken);
            writer.writeEndObject();
        }
        return output.toString();
    }
}
