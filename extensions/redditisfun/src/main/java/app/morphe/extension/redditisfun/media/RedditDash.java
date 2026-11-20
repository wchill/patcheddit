/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.redditisfun.media;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.HttpUrl;

final class RedditDash {
    static final class Track {
        String mime, codecs, url, index, initialization;
        int width, height, bandwidth;
        long area() { return (long) width * height; }
    }

    static final class Manifest {
        String duration;
        Track video, poster, audio;
    }

    private RedditDash() {}

    static MediaLoader.Album resolve(Context context, String base, byte[] bytes) throws IOException {
        Manifest manifest = parse(base, new String(bytes, StandardCharsets.UTF_8));
        File directory = new File(context.getCacheDir(), "patcheddit-dash");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create DASH cache");
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Could not list DASH cache");
        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int i = 0; i <= files.length - 128; i++) {
            if (!files[i].delete()) throw new IOException("Could not trim DASH cache");
        }
        File file = File.createTempFile("inline-", ".mpd", directory);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(serialize(manifest).getBytes(StandardCharsets.UTF_8));
        }
        return new MediaLoader.Album(new MediaLoader.Item(manifest.poster.url, Uri.fromFile(file).toString(),
                true, manifest.video.width, manifest.video.height));
    }

    static Manifest parse(String base, String xml) throws IOException {
        Manifest manifest = new Manifest();
        List<Track> video = new ArrayList<>(), audio = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            Track current = null;
            String mime = null;
            for (int event = parser.nextToken(); event != XmlPullParser.END_DOCUMENT; event = parser.nextToken()) {
                if (event == XmlPullParser.DOCDECL || parser.getDepth() > 16) throw new IOException("Unsafe DASH metadata");
                if (event == XmlPullParser.START_TAG) {
                    switch (parser.getName()) {
                        case "MPD": manifest.duration = attribute(parser, "mediaPresentationDuration"); break;
                        case "AdaptationSet": mime = attribute(parser, "mimeType"); break;
                        case "Representation":
                            if (video.size() + audio.size() >= 128) throw new IOException("Too many DASH tracks");
                            current = new Track();
                            current.mime = attribute(parser, "mimeType");
                            if (current.mime == null) current.mime = mime;
                            current.codecs = attribute(parser, "codecs");
                            current.width = integer(parser, "width");
                            current.height = integer(parser, "height");
                            current.bandwidth = integer(parser, "bandwidth");
                            break;
                        case "BaseURL":
                            if (current == null) throw new IOException("Unsupported DASH base URL");
                            HttpUrl url = HttpUrl.get(base + "/").resolve(parser.nextText().trim());
                            if (url == null || MediaLoader.publicUrl(url.toString()) == null
                                    || !url.host().equals("v.redd.it")
                                    || !url.encodedPath().startsWith(HttpUrl.get(base + "/").encodedPath())) {
                                throw new IOException("Unsafe DASH track destination");
                            }
                            current.url = url.toString();
                            break;
                        case "SegmentBase":
                            if (current != null) current.index = range(attribute(parser, "indexRange"));
                            break;
                        case "Initialization":
                            if (attribute(parser, "sourceURL") != null) throw new IOException("Unsupported DASH initialization URL");
                            if (current != null) current.initialization = range(attribute(parser, "range"));
                            break;
                        case "SegmentTemplate": case "SegmentList": case "ContentProtection":
                            throw new IOException("Unsupported DASH track layout");
                    }
                } else if (event == XmlPullParser.END_TAG && parser.getName().equals("Representation")) {
                    if (current != null && current.url != null && current.bandwidth > 0) {
                        if ("video/mp4".equals(current.mime) && current.width > 0 && current.height > 0
                                && current.width <= 8192 && current.height <= 8192) video.add(current);
                        if ("audio/mp4".equals(current.mime)) audio.add(current);
                    }
                    current = null;
                }
            }
        } catch (XmlPullParserException | NumberFormatException exception) {
            throw new IOException("Invalid DASH metadata", exception);
        }
        if (video.isEmpty() || manifest.duration == null || !manifest.duration.matches("PT[0-9.HMS]{1,48}")) {
            throw new IOException("DASH manifest has no usable video");
        }
        manifest.poster = video.get(0);
        for (Track track : video) {
            if (track.area() < manifest.poster.area()) manifest.poster = track;
            if (Math.min(track.width, track.height) <= 480
                    && (manifest.video == null || track.area() > manifest.video.area())) manifest.video = track;
        }
        if (manifest.video == null) manifest.video = manifest.poster;
        for (Track track : audio) {
            if (manifest.audio == null || track.bandwidth > manifest.audio.bandwidth) manifest.audio = track;
        }
        return manifest;
    }

    static String serialize(Manifest manifest) throws IOException {
        StringWriter text = new StringWriter();
        XmlSerializer xml = Xml.newSerializer();
        xml.setOutput(text);
        xml.startTag(null, "MPD").attribute(null, "xmlns", "urn:mpeg:dash:schema:mpd:2011")
                .attribute(null, "type", "static").attribute(null, "minBufferTime", "PT1.5S")
                .attribute(null, "mediaPresentationDuration", manifest.duration);
        xml.startTag(null, "Period").attribute(null, "duration", manifest.duration);
        track(xml, manifest.video, "video");
        if (manifest.audio != null) track(xml, manifest.audio, "audio");
        xml.endTag(null, "Period").endTag(null, "MPD");
        xml.flush();
        return text.toString();
    }

    private static void track(XmlSerializer xml, Track track, String kind) throws IOException {
        xml.startTag(null, "AdaptationSet").attribute(null, "mimeType", track.mime)
                .attribute(null, "contentType", kind);
        xml.startTag(null, "Representation").attribute(null, "id", kind)
                .attribute(null, "bandwidth", Integer.toString(track.bandwidth));
        if (track.codecs != null) xml.attribute(null, "codecs", track.codecs);
        if (track.width > 0) xml.attribute(null, "width", Integer.toString(track.width))
                .attribute(null, "height", Integer.toString(track.height));
        xml.startTag(null, "BaseURL").text(track.url).endTag(null, "BaseURL");
        if (track.index != null) {
            xml.startTag(null, "SegmentBase").attribute(null, "indexRange", track.index);
            if (track.initialization != null) xml.startTag(null, "Initialization")
                    .attribute(null, "range", track.initialization).endTag(null, "Initialization");
            xml.endTag(null, "SegmentBase");
        }
        xml.endTag(null, "Representation").endTag(null, "AdaptationSet");
    }

    private static String attribute(XmlPullParser parser, String key) { return parser.getAttributeValue(null, key); }
    private static int integer(XmlPullParser parser, String key) {
        String value = attribute(parser, key);
        return value == null ? 0 : Integer.parseInt(value);
    }
    private static String range(String value) throws IOException {
        if (value != null && !value.matches("[0-9]{1,12}-[0-9]{1,12}")) throw new IOException("Invalid DASH byte range");
        return value;
    }
}
