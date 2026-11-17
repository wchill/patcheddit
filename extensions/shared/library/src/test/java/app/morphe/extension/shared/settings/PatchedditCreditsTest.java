/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PatchedditCreditsTest {
    @Test
    public void loadsAllContributorPagesAndLinksProfiles() throws Exception {
        List<String> queries = new ArrayList<>();
        List<Boolean> disconnected = new ArrayList<>();
        URL endpoint = endpoint(queries, disconnected, 200,
                contributors(100), "[{\"login\":\"last-user\",\"contributions\":2}]");
        List<PatchedditCredits.Contributor> result = PatchedditCredits.contributors(endpoint);
        assertEquals(101, result.size());
        assertEquals("https://github.com/last-user", result.get(100).profileUrl());
        assertEquals(2, result.get(100).contributions);
        assertEquals(List.of("per_page=100&page=1", "per_page=100&page=2"), queries);
        assertEquals(List.of(true, true), disconnected);
    }

    @Test
    public void reportsRateLimitsInsteadOfReturningAnEmptyCreditList() throws Exception {
        List<Boolean> disconnected = new ArrayList<>();
        URL endpoint = endpoint(new ArrayList<>(), disconnected, 403, "{\"message\":\"Rate limited\"}");
        IOException error = assertThrows(IOException.class, () -> PatchedditCredits.contributors(endpoint));
        assertTrue(error.getMessage().contains("403"));
        assertEquals(List.of(true), disconnected);
    }

    @Test
    public void rejectsMalformedContributorsAndDoesNotReturnPartialPages() throws Exception {
        URL endpoint = endpoint(new ArrayList<>(), new ArrayList<>(), 200,
                contributors(100), "[{\"login\":\"../not-a-profile\",\"contributions\":1}]");
        assertThrows(IOException.class, () -> PatchedditCredits.contributors(endpoint));
    }

    @Test
    public void readsCompleteOfflineNoticeTextWithoutTruncation() throws Exception {
        String text = "Copyright\n\n" + "Complete license text\n".repeat(10000);
        String data = new JSONArray().put(new JSONObject().put("name", "Component").put("text", text)).toString();
        List<PatchedditCredits.Notice> notices = PatchedditCredits.notices(input(data));
        assertEquals("Component", notices.get(0).name);
        assertEquals(text, notices.get(0).text);
        for (String invalid : List.of("[]", "{}", "[{\"name\":\"Missing text\"}]",
                "[{\"name\":\"\",\"text\":\"License\"}]")) {
            assertThrows(IOException.class, () -> PatchedditCredits.notices(input(invalid)));
        }
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String contributors(int count) throws Exception {
        JSONArray entries = new JSONArray();
        for (int index = 0; index < count; index++) {
            entries.put(new JSONObject().put("login", "user" + index).put("contributions", index + 1));
        }
        return entries.toString();
    }

    private static URL endpoint(List<String> queries, List<Boolean> disconnected, int status,
                                String... pages) throws Exception {
        return new URL(null, "https://api.github.test/contributors", new URLStreamHandler() {
            @Override
            protected HttpURLConnection openConnection(URL url) {
                int index = queries.size();
                queries.add(url.getQuery());
                disconnected.add(false);
                return new HttpURLConnection(url) {
                    @Override public int getResponseCode() { return status; }
                    @Override public InputStream getInputStream() { return input(pages[index]); }
                    @Override public void connect() {}
                    @Override public void disconnect() { disconnected.set(index, true); }
                    @Override public boolean usingProxy() { return false; }
                };
            }
        });
    }
}
