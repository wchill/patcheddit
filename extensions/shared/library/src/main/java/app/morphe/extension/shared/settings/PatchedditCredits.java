/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.requests.Requester;

final class PatchedditCredits {
    static final String CONTRIBUTORS = "https://github.com/wchill/patcheddit/graphs/contributors";

    static final class Contributor {
        final String login;
        final int contributions;

        Contributor(String login, int contributions) {
            this.login = login;
            this.contributions = contributions;
        }

        String profileUrl() {
            return "https://github.com/" + login;
        }
    }

    static final class Notice {
        final String name;
        final String text;

        Notice(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }

    private PatchedditCredits() {}

    static List<Contributor> contributors() throws IOException {
        return contributors(new URL("https://api.github.com/repos/wchill/patcheddit/contributors"));
    }

    static List<Contributor> contributors(URL endpoint) throws IOException {
        List<Contributor> result = new ArrayList<>();
        for (int page = 1; ; page++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            URL url = new URL(endpoint, endpoint.getPath() + "?per_page=100&page=" + page);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Patcheddit");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("GitHub contributors returned HTTP " + status);
                }
                JSONArray entries = Requester.parseJSONArray(connection);
                for (int index = 0; index < entries.length(); index++) {
                    JSONObject entry = entries.getJSONObject(index);
                    String login = entry.getString("login");
                    int count = entry.getInt("contributions");
                    if (!login.matches("[A-Za-z0-9][A-Za-z0-9-]*(\\[bot\\])?") || count < 0) {
                        throw new IOException("Invalid GitHub contributor");
                    }
                    result.add(new Contributor(login, count));
                }
                if (entries.length() < 100) return result;
            } catch (JSONException failure) {
                throw new IOException("Invalid GitHub contributor response", failure);
            } finally {
                connection.disconnect();
            }
        }
    }

    static List<Notice> notices(InputStream input) throws IOException {
        List<Notice> result = new ArrayList<>();
        try (InputStream source = input) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray entries = new JSONArray(bytes.toString("UTF-8"));
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                String name = entry.getString("name");
                String text = entry.getString("text");
                if (name.isEmpty() || text.isEmpty()) throw new IOException("Empty license notice");
                result.add(new Notice(name, text));
            }
            if (result.isEmpty()) throw new IOException("No license notices were bundled");
            return result;
        } catch (JSONException failure) {
            throw new IOException("Invalid bundled license notices", failure);
        }
    }
}
