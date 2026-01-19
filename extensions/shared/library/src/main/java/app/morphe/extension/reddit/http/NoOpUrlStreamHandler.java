package app.morphe.extension.reddit.http;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class NoOpUrlStreamHandler extends URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
