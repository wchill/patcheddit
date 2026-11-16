/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EscapedUrls {
    private static final Logger LOGGER = Logger.getLogger(EscapedUrls.class.getName());
    static final long MAX_BODY_BYTES = 8L * 1024 * 1024;
    private static final String URL = "(?i:\\b(?:https?://|www\\.)"
            + "(?:(?!&(?:lt|gt|quot|apos|#(?:34|39|60|62));)[^\\s<>\"'`])++)";
    private static final Pattern URLS = Pattern.compile(URL);
    private static final Pattern MARKDOWN = Pattern.compile(
            "(?m)^ {0,3}(`{3,}|~{3,})[^\\r\\n]*|^(?: {4}|\\t)[^\\r\\n]*|`+|" + URL);
    private static final Pattern HTML = Pattern.compile(
            "(?is)(?:<|&lt;)(code|pre)\\b.*?(?:</|&lt;/)\\1\\s*(?:>|&gt;)|" + URL);
    private static final Pattern ESCAPE = Pattern.compile(
            "(?<!\\\\)\\\\([_*~])|(?<!%5[cC])%5[cC]([_*~])");

    private EscapedUrls() {}

    static String repairJson(String text) {
        return repairJson(text, false);
    }

    /** Infinity's native OkHttp decoder has no request context; only accept Reddit things. */
    public static String repairRedditResponse(String text) {
        if (text == null || text.length() > MAX_BODY_BYTES
                || !text.contains("\"kind\"")
                || text.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) return text;
        return repairJson(text, true);
    }

    private static String repairJson(String text, boolean thingsOnly) {
        if (text.indexOf('\\') < 0 && !text.contains("%5C") && !text.contains("%5c")) return text;
        try {
            JSONTokener parser = new JSONTokener(text);
            Object root = parser.nextValue();
            if (!(root instanceof JSONObject || root instanceof JSONArray) || parser.nextClean() != 0) {
                LOGGER.warning("Unexpected Reddit URL repair JSON document");
                return text;
            }
            return repair(root, thingsOnly) ? root.toString() : text;
        } catch (JSONException exception) {
            LOGGER.log(Level.WARNING, "Could not parse Reddit content for URL repair", exception);
            return text;
        }
    }

    private static boolean repair(Object node, boolean thingsOnly) throws JSONException {
        boolean changed = false;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) changed |= repair(array.get(i), thingsOnly);
        } else if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if (thingsOnly && (object.optString("kind").equals("t1") || object.optString("kind").equals("t3"))) {
                return repair(object.optJSONObject("data"), false);
            }
            JSONArray names = object.names();
            if (names == null) return false;
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object value = object.get(key);
                if (!thingsOnly && value instanceof String) {
                    Pattern tokens;
                    switch (key) {
                        case "body":
                        case "selftext":
                            tokens = MARKDOWN;
                            break;
                        case "body_html":
                        case "selftext_html":
                            tokens = HTML;
                            break;
                        case "url":
                        case "url_overridden_by_dest":
                            tokens = URLS;
                            break;
                        default:
                            continue;
                    }
                    String original = (String) value;
                    String fixed = repairText(original, tokens);
                    if (!original.equals(fixed)) {
                        object.put(key, fixed);
                        changed = true;
                    }
                } else {
                    changed |= repair(value, thingsOnly);
                }
            }
        }
        return changed;
    }

    private static String repairText(String text, Pattern tokens) {
        Matcher matcher = tokens.matcher(text);
        StringBuilder result = new StringBuilder();
        String fence = null;
        int copied = 0;
        int position = 0;
        while (matcher.find(position)) {
            position = matcher.end();
            String token = matcher.group();
            if (tokens == MARKDOWN) {
                String delimiter = matcher.group(1);
                if (delimiter != null) {
                    if (fence == null) {
                        fence = delimiter;
                    } else if (delimiter.charAt(0) == fence.charAt(0)
                            && delimiter.length() >= fence.length()
                            && text.substring(matcher.end(1), matcher.end()).trim().isEmpty()) {
                        fence = null;
                    }
                    continue;
                }
                if (fence != null) continue;
                if (token.charAt(0) == '`') {
                    int end = closingBackticks(text, position, token.length());
                    if (end != -1) position = end;
                    continue;
                }
            }
            if (!URLS.matcher(token).lookingAt()) continue;
            if (tokens == MARKDOWN) token = markdownUrl(token);
            Matcher escapes = ESCAPE.matcher(token);
            if (!escapes.find()) continue;
            result.append(text, copied, matcher.start());
            result.append(escapes.replaceAll("$1$2"));
            copied = matcher.start() + token.length();
        }
        return copied == 0 ? text : result.append(text, copied, text.length()).toString();
    }

    private static String markdownUrl(String token) {
        int parentheses = 0;
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            if (current == '\\') {
                i++;
            } else if (current == '(') {
                parentheses++;
            } else if (current == ')') {
                if (parentheses == 0) return token.substring(0, i);
                parentheses--;
            }
        }
        return token;
    }

    private static int closingBackticks(String text, int start, int count) {
        int position = start;
        while ((position = text.indexOf('`', position)) != -1) {
            int end = position + 1;
            while (end < text.length() && text.charAt(end) == '`') end++;
            if (end - position == count) return end;
            position = end;
        }
        return -1;
    }
}
