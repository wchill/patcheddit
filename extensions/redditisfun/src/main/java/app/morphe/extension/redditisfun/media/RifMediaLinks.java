package app.morphe.extension.redditisfun.media;

import app.morphe.extension.shared.fixes.slink.SafeRedirects;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class RifMediaLinks {
    private static final Pattern MATRIX_DOWNLOAD = Pattern.compile(
            "/_matrix/media/v[0-9]+/download/reddit\\.com/[A-Za-z0-9._~-]{1,512}/?");
    private static final Pattern IMAGE = Pattern.compile(
            "/[A-Za-z0-9_-]+\\.(?:png|jpe?g|gif|webp|avif|bmp)", Pattern.CASE_INSENSITIVE);

    private RifMediaLinks() {}

    static boolean isMatrixPath(String path) {
        return MATRIX_DOWNLOAD.matcher(path).matches();
    }

    static URI target(String link) {
        URI uri = SafeRedirects.httpsUri(link);
        if (isMatrix(uri)) return withoutFragment(uri);
        if (!SafeRedirects.hasHost(uri, "www.reddit.com", "reddit.com", "np.reddit.com",
                "new.reddit.com", "old.reddit.com")) return null;
        if (!"/media".equals(uri.getRawPath()) && !"/media/".equals(uri.getRawPath())) return null;
        URI target = SafeRedirects.httpsUri(SafeRedirects.query(uri, "url"));
        return isImage(target) || isMatrix(target) ? withoutFragment(target) : null;
    }

    static boolean isMatrix(URI uri) {
        return SafeRedirects.hasHost(uri, "matrix.redditspace.com") && isMatrixPath(uri.getRawPath());
    }

    static URI resolve(URI matrix, SafeRedirects.ConnectionFactory factory) throws IOException {
        Set<URI> visited = new HashSet<>();
        SafeRedirects requests = factory == null ? new SafeRedirects() : new SafeRedirects(factory);
        URI current = matrix;
        for (int hop = 0; hop < 3 && isMatrix(current) && visited.add(current); hop++) {
            URI next = requests.redirect(current, null);
            if (next == null) return null;
            current = withoutFragment(next);
            if (isImage(current)) return current;
        }
        return null;
    }

    private static boolean isImage(URI uri) {
        return SafeRedirects.hasHost(uri, "i.redd.it", "preview.redd.it", "external-preview.redd.it")
                && IMAGE.matcher(uri.getRawPath()).matches();
    }

    private static URI withoutFragment(URI uri) {
        String text = uri.toASCIIString();
        int fragment = text.indexOf('#');
        return fragment < 0 ? uri : URI.create(text.substring(0, fragment));
    }
}
