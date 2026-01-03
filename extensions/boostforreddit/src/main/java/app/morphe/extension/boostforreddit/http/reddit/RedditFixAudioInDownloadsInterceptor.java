package app.morphe.extension.boostforreddit.http.reddit;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @noinspection unused
 */
public class RedditFixAudioInDownloadsInterceptor implements Interceptor {
    private static boolean enabled = false;
    private static final Pattern VIDEO_REGEX = Pattern.compile("^(https?://v\\.redd\\.it/[a-z0-9]+)/audio");

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        if (!enabled) {
            return chain.proceed(request);
        }
        Matcher matcher = VIDEO_REGEX.matcher(url);
        if (!matcher.find()) {
            return chain.proceed(request);
        }

        try {
            String baseUrl = matcher.group(1);
            JsonNode submissionData;
            try (Response originalSubmission = HttpUtils.get(baseUrl)) {
                try (Response submissionApiResponse = HttpUtils.get(originalSubmission.request().url() + "/.json")) {
                    submissionData = HttpUtils.getJsonFromString(submissionApiResponse.body().string());
                }
            }

            String dashUrl = submissionData.get(0).get("data").get("children").get(0).get("data").get("media").get("reddit_video").get("dash_url").asText();

            Response dashPlaylistResponse = HttpUtils.get(dashUrl);
            InputStream dashPlaylistStream = dashPlaylistResponse.body().byteStream();
            String audioFilename = parseDashPlaylist(dashPlaylistStream);

            return HttpUtils.get(baseUrl + "/" + audioFilename);
        } catch (Exception e) {
            LoggingUtils.logException(false, () -> "Failed to retrieve audio: " + e);
            return chain.proceed(request);
        }
    }

    private String parseDashPlaylist(InputStream xmlDocumentStream) throws IOException, SAXException, ParserConfigurationException {
        /*
        Looking for something like this in the DASH playlist

        <Representation audioSamplingRate="48000" bandwidth="131398" codecs="mp4a.40.2" id="7" mimeType="audio/mp4">
          <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2" />
          <BaseURL>DASH_AUDIO_128.mp4</BaseURL>
          <SegmentBase indexRange="833-1008" timescale="48000">
            <Initialization range="0-832" />
          </SegmentBase>
        </Representation>
        */

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlDocumentStream);
        doc.getDocumentElement().normalize();

        NodeList representations = doc.getElementsByTagName("Representation");
        int maxBandwidth = 0;
        String baseUrl = null;

        for (int i = 0; i < representations.getLength(); i++) {
            Element representation = (Element) representations.item(i);
            String bandwidthStr = representation.getAttribute("bandwidth");
            if (bandwidthStr == null || bandwidthStr.isBlank()) continue;

            int bandwidth = Integer.parseInt(bandwidthStr);
            if (bandwidth > maxBandwidth) {
                baseUrl = representation.getElementsByTagName("BaseURL").item(0).getTextContent();
            }
        }

        if (baseUrl != null) return baseUrl;
        throw new RuntimeException("Unable to find matching audio track");
    }
}
