package app.morphe.extension.reddit.http;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@SuppressWarnings("unused")
public class JrawUtils {
    public static URL createUrl(String href) {
        try {
            URI uri = new URI(href);
            String path = uri.getPath();
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }
            if (uri.getFragment() != null) {
                path += "#" + uri.getFragment();
            }
            return new URL(uri.getScheme(), uri.getRawAuthority(), uri.getPort(), path, new NoOpUrlStreamHandler());
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL", e);
        }
    }

    public static String fixOauthFinalUrl(String originalUrl) {
        String[] parts = originalUrl.split("\\?");
        return "https://localhost?" + parts[1];
    }
}
