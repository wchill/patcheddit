/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.relay.media;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reddit.news.oauth.reddit.model.links.MediaDetails;
import reddit.news.oauth.reddit.model.links.MediaPreview;

public final class MediaViewer {
    private static final int MAX_MANIFEST = 512 * 1024;

    private MediaViewer() {}

    public static Object resolve(Object fetcher, String link) {
        int kind = MediaLinks.kind(link);
        if (kind == MediaLinks.NONE) return null;
        if (kind == MediaLinks.DASH) {
            return NativeMedia.defer(() -> {
                String manifest = MediaLinks.manifestUrl(link);
                try {
                    ArrayList<MediaPreview> previews = fetchDash(fetcher, manifest);
                    if (previews.isEmpty()) throw new IOException("Reddit video manifest has no playable media");
                    return previews;
                } catch (IOException failure) {
                    Log.e("RelayMedia", "Could not load Reddit video manifest", failure);
                    throw failure;
                }
            });
        }
        ArrayList<MediaPreview> previews = new ArrayList<>(1);
        // Keep the original URL, including signing parameters, in every native field.
        previews.add(new MediaPreview(new MediaDetails(kind == MediaLinks.IMAGE ? link : ""),
                new MediaDetails(kind == MediaLinks.IMAGE ? link : ""), link, "", "", "", "", kind));
        return NativeMedia.just(previews);
    }

    public static String manifestBase(String input, String nativeBase) {
        return MediaLinks.uri(input) != null ? input : nativeBase;
    }

    public static boolean isHls(String filename, String url) {
        return MediaLinks.uri(url) == null ? filename.contains(".m3u8") : MediaLinks.isHls(url);
    }

    private static final class PublicClient {
        // Initialized only when the lazy manifest request runs on Relay's I/O scheduler.
        static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
                .dns(host -> {
                    List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
                    for (InetAddress address : addresses) {
                        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                                || address.isMulticastAddress()
                                || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) {
                            throw new UnknownHostException("Non-public Reddit media destination");
                        }
                    }
                    return addresses;
                }).build();
    }

    private static ArrayList<MediaPreview> fetchDash(Object fetcher, String url) throws IOException {
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (MediaLinks.uri(url) == null) throw new IOException("Invalid Reddit media redirect");
            try (Response response = PublicClient.CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    HttpUrl next = location == null ? null : response.request().url().resolve(location);
                    if (next == null) throw new IOException("Reddit media redirect has no destination");
                    url = next.toString();
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("Reddit media HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null || body.contentLength() > MAX_MANIFEST) {
                    throw new IOException("Missing or oversized Reddit video manifest");
                }
                try (InputStream input = body.byteStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > MAX_MANIFEST) throw new IOException("Oversized Reddit video manifest");
                        bytes.write(buffer, 0, count);
                    }
                    return NativeMedia.parseDash(fetcher, response.request().url().toString(),
                            new ByteArrayInputStream(bytes.toByteArray()));
                }
            }
        }
        throw new IOException("Too many Reddit media redirects");
    }
}
