/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RelativeMarkdownLinks {
    private static final String TAG = "<!--(?s:.*?)(?:-->|\\z)|<(?:\"[^\"]*\"|'[^']*'|[^<'\">])*>";
    // Named Matcher groups require Android 8; these renderers also run on Android 6 and 7.
    private static final String CANDIDATE = "(\\[([^\\[\\]\\r\\n]++)]\\("
            + "((/?)(?:<a\\b[^<>]*>)(/?)([ur])/"
            + "([a-z0-9_-]++)(/?)(?:</a\\s*>)(/?))\\))";
    private static final int CONSTRUCT = 1;
    private static final int LABEL = 2;
    private static final int TARGET = 3;
    private static final int LEADING = 4;
    private static final int INNER_LEADING = 5;
    private static final int KIND = 6;
    private static final int NAME = 7;
    private static final int INNER_TRAILING = 8;
    private static final int TRAILING = 9;
    private static final Pattern TOKENS = Pattern.compile(TAG + "|" + CANDIDATE + "|[\\[\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile(TAG);
    private static final Pattern TAG_NAME = Pattern.compile("^<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)\\b");
    private static final Pattern ATTRIBUTES = Pattern.compile(
            "(?:^|\\s)(href|style|face)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_BOUNDARY = Pattern.compile(
            "&(?:#0*(?:10|13|91|93)|#x0*(?:a|d|5b|5d)|lbrack|rbrack);",
            Pattern.CASE_INSENSITIVE);
    private static final String VOID_TAGS = " area base br col embed hr img input link meta param source track wbr ";

    private RelativeMarkdownLinks() {}

    public static String repairHtml(String html) {
        if (html == null || html.length() > 1024 * 1024 || !html.contains("](")) return html;
        Matcher tokens = TOKENS.matcher(html);
        TagState state = new TagState();
        StringBuilder result = null;
        int copied = 0;
        int brackets = 0;
        while (tokens.find()) {
            String token = tokens.group();
            if (tokens.group(CONSTRUCT) != null) {
                String label = tokens.group(LABEL);
                String path = "/" + tokens.group(KIND).toLowerCase(Locale.ROOT) + "/" + tokens.group(NAME);
                if (brackets == 0 && !state.protectedText() && !escaped(html, tokens.start())
                        && (tokens.group(LEADING).isEmpty() || tokens.group(INNER_LEADING).isEmpty())
                        && (tokens.group(TRAILING).isEmpty() || tokens.group(INNER_TRAILING).isEmpty())
                        && validLabel(label) && matchingAnchor(tokens.group(TARGET), path)) {
                    if (result == null) result = new StringBuilder(html.length());
                    result.append(html, copied, tokens.start()).append("<a href=\"https://www.reddit.com")
                            .append(path).append("\">").append(withoutAnchors(label)).append("</a>");
                    copied = tokens.end();
                }
                Matcher tags = TAGS.matcher(token);
                while (tags.find()) state.accept(tags.group());
            } else if (token.charAt(0) == '<') {
                state.accept(token);
            } else if (!escaped(html, tokens.start())) {
                if (token.equals("[")) brackets++;
                else brackets = Math.max(0, brackets - 1);
            }
        }
        return result == null ? html : result.append(html, copied, html.length()).toString();
    }

    private static boolean escaped(String text, int start) {
        int slashes = 0;
        while (start > 0 && text.charAt(--start) == '\\') slashes++;
        return (slashes & 1) != 0;
    }

    private static boolean validLabel(String label) {
        if (ENCODED_BOUNDARY.matcher(label).find()) return false;
        Matcher tags = TAGS.matcher(label);
        TagState state = new TagState();
        while (tags.find()) {
            Matcher name = TAG_NAME.matcher(tags.group());
            if (name.find() && (name.group(2).equalsIgnoreCase("br")
                    || protectedTag(name.group(2).toLowerCase(Locale.ROOT), tags.group()))) return false;
            state.accept(tags.group());
        }
        String text = TAGS.matcher(label).replaceAll("");
        return state.tags.isEmpty() && !text.isEmpty() && text.indexOf('<') < 0 && text.indexOf('>') < 0;
    }

    private static boolean matchingAnchor(String target, String path) {
        int open = target.indexOf('<');
        int end = target.indexOf('>', open);
        Matcher attributes = ATTRIBUTES.matcher(target.substring(open + 2, end));
        String href = null;
        while (attributes.find()) {
            if (!attributes.group(1).equalsIgnoreCase("href")) continue;
            if (href != null) return false;
            href = attributeValue(attributes);
        }
        if (href == null || href.isEmpty()) return false;
        try {
            URI uri = new URI(href);
            if (uri.isAbsolute()) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (!(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                        || host == null || uri.getRawUserInfo() != null
                        || !(host.equalsIgnoreCase("reddit.com")
                        || host.toLowerCase(Locale.ROOT).endsWith(".reddit.com"))) return false;
            } else if (uri.getRawAuthority() != null) {
                return false;
            }
            String actual = uri.getPath();
            if (actual == null) return false;
            if (!actual.startsWith("/")) actual = "/" + actual;
            if (actual.endsWith("/")) actual = actual.substring(0, actual.length() - 1);
            return actual.equalsIgnoreCase(path);
        } catch (URISyntaxException invalidLink) {
            return false;
        }
    }

    private static String withoutAnchors(String label) {
        Matcher tags = TAGS.matcher(label);
        StringBuffer result = new StringBuffer();
        while (tags.find()) {
            Matcher name = TAG_NAME.matcher(tags.group());
            String replacement = name.find() && name.group(2).equalsIgnoreCase("a") ? "" : tags.group();
            tags.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        return tags.appendTail(result).toString();
    }

    private static boolean protectedTag(String name, String tag) {
        if (name.equals("code") || name.equals("pre") || name.equals("tt")
                || name.equals("kbd") || name.equals("samp") || name.equals("script") || name.equals("style")) return true;
        Matcher attributes = ATTRIBUTES.matcher(tag);
        while (attributes.find()) {
            String attribute = attributes.group(1).toLowerCase(Locale.ROOT);
            String value = attributeValue(attributes).toLowerCase(Locale.ROOT);
            if ((attribute.equals("face") || attribute.equals("style") && value.contains("font-family"))
                    && value.contains("monospace")) return true;
        }
        return false;
    }

    private static String attributeValue(Matcher attributes) {
        for (int group = 2; group <= 4; group++) {
            if (attributes.group(group) != null) return attributes.group(group);
        }
        throw new IllegalStateException("Matched HTML attribute has no value");
    }

    private static final class TagState {
        final ArrayDeque<Tag> tags = new ArrayDeque<>();
        int protectedDepth;

        boolean protectedText() {
            return protectedDepth != 0;
        }

        void accept(String text) {
            Matcher match = TAG_NAME.matcher(text);
            if (!match.find()) return;
            String name = match.group(2).toLowerCase(Locale.ROOT);
            if (match.group(1).isEmpty()) {
                if (!text.endsWith("/>") && !VOID_TAGS.contains(" " + name + " ")) {
                    boolean protect = name.equals("a") || protectedTag(name, text);
                    tags.push(new Tag(name, protect));
                    if (protect) protectedDepth++;
                }
            } else {
                boolean present = false;
                for (Tag tag : tags) present |= tag.name.equals(name);
                if (!present) return;
                Tag tag;
                do {
                    tag = tags.pop();
                    if (tag.protect) protectedDepth--;
                } while (!tag.name.equals(name));
            }
        }
    }

    private static final class Tag {
        final String name;
        final boolean protect;

        Tag(String name, boolean protect) {
            this.name = name;
            this.protect = protect;
        }
    }
}
