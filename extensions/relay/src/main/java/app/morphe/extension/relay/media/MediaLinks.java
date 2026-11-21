/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MediaLinks {
    static final int NONE = 0, IMAGE = 1, VIDEO = 2, ANIMATED = 3, DASH = 4;

    private MediaLinks() {}

    private static URI webUri(String link) {
        if (link == null || link.length() > 8192) return null;
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme(), host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
            int port = uri.getPort();
            if (port != -1 && port != (scheme.equalsIgnoreCase("https") ? 443 : 80)) return null;
            return uri;
        } catch (URISyntaxException invalidUrl) {
            return null;
        }
    }

    static URI uri(String link) {
        URI uri = webUri(link);
        if (uri == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("i.redd.it") || host.equals("preview.redd.it")
                || host.equals("external-preview.redd.it") || host.equals("v.redd.it")
                || host.equals("vcf.redd.it") || host.equals("packaged-media.redd.it")
                || host.equals("redditmedia.com") || host.endsWith(".redditmedia.com")
                || host.equals("redditstatic.com") || host.endsWith(".redditstatic.com") ? uri : null;
    }

    static String inlineImage(String link) {
        int kind = kind(link);
        if (kind == IMAGE || kind == ANIMATED) return link;
        URI uri = webUri(link);
        if (uri == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT), path = uri.getPath();
        if (host.equals("giphy.com") || host.equals("www.giphy.com")) {
            String id = path.substring(path.lastIndexOf('/') + 1);
            id = id.substring(id.lastIndexOf('-') + 1);
            return id.matches("[A-Za-z0-9]{1,64}") && path.startsWith("/gifs/")
                    ? "https://media.giphy.com/media/" + id + "/giphy.gif" : null;
        }
        if (!(host.equals("i.imgur.com") || host.equals("media.giphy.com") || host.equals("i.giphy.com"))) return null;
        String format = format(uri);
        String suffix = format.isEmpty() ? path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : format;
        return suffix.matches("jpe?g|pjpg|png|gif|webp|avif|bmp") ? link : null;
    }

    static String format(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals < 0 || !part.substring(0, equals).equals("format")) continue;
            try {
                return URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8.name())
                        .toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException invalidFormat) {
                return "";
            }
        }
        return "";
    }

    static boolean videoRoot(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT), path = uri.getRawPath();
        return (host.equals("v.redd.it") || host.equals("vcf.redd.it"))
                && path.matches("/(?:[A-Za-z0-9]+|link/[A-Za-z0-9]+/asset/[A-Za-z0-9]+)/?");
    }

    static int kind(String link) {
        URI uri = uri(link);
        if (uri == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")) return NONE;
        if (videoRoot(uri)) return DASH;
        String path = uri.getPath().toLowerCase(Locale.ROOT);
        String format = format(uri);
        String suffix = format.isEmpty() ? path.substring(path.lastIndexOf('.') + 1) : format;
        // The requested format can differ from the signed URL's filename.
        switch (suffix) {
            case "mpd": return DASH;
            case "mp4": case "webm": case "m3u8": case "m4v": case "m4a": return VIDEO;
            case "gif": case "webp": return ANIMATED;
            case "jpg": case "jpeg": case "pjpg": case "png": case "apng":
            case "avif": case "bmp": return IMAGE;
            default:
                // Image CDNs also serve extensionless thumbnails, avatars, and emoji.
                String last = path.substring(path.lastIndexOf('/') + 1);
                String host = uri.getHost().toLowerCase(Locale.ROOT);
                return format.isEmpty() && !last.contains(".") && !last.isEmpty()
                        && !host.equals("v.redd.it") && !host.equals("vcf.redd.it")
                        && !host.equals("packaged-media.redd.it") && !host.endsWith("redditstatic.com")
                        ? IMAGE : NONE;
        }
    }

    public static boolean canHandle(String link) {
        return kind(link) != NONE;
    }

    public static boolean isHls(String link) {
        URI uri = uri(link);
        if (uri == null) return false;
        String format = format(uri);
        return format.isEmpty() ? uri.getPath().toLowerCase(Locale.ROOT).contains(".m3u8")
                : format.equals("m3u8");
    }

    public static String manifestUrl(String link) {
        URI uri = uri(link);
        if (uri == null || !videoRoot(uri)) return link;
        int query = link.indexOf('?'), fragment = link.indexOf('#');
        int end = Math.min(query < 0 ? link.length() : query, fragment < 0 ? link.length() : fragment);
        String base = link.substring(0, end);
        return base + (base.endsWith("/") ? "" : "/") + "DASHPlaylist.mpd" + link.substring(end);
    }
}
