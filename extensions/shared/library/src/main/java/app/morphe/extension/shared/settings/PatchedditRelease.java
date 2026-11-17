/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.json.JSONException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.requests.Requester;

final class PatchedditRelease {
    private static final String MANIFEST =
            "https://raw.githubusercontent.com/wchill/patcheddit/%s/patches-bundle.json";
    private static final Pattern VERSION = Pattern.compile(
            "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    private PatchedditRelease() {}

    static String latest(String installed) throws IOException {
        String latest = fetch(new URL(String.format(java.util.Locale.ROOT, MANIFEST, "main")));
        if (isPreRelease(installed)) {
            String development = fetch(new URL(String.format(java.util.Locale.ROOT, MANIFEST, "dev")));
            if (compare(development, latest) > 0) latest = development;
        }
        return latest;
    }

    static String fetch(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Patcheddit");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Patcheddit release check returned HTTP " + status);
            }
            String version = Requester.parseJSONObject(connection).getString("version");
            try {
                parsed(version);
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid Patcheddit release version", failure);
            }
            return version;
        } catch (JSONException failure) {
            throw new IOException("Invalid Patcheddit release manifest", failure);
        } finally {
            connection.disconnect();
        }
    }

    static boolean isPreRelease(String version) {
        return parsed(version).group(4) != null;
    }

    static int compare(String first, String second) {
        Matcher left = parsed(first);
        Matcher right = parsed(second);
        for (int group = 1; group <= 3; group++) {
            int result = number(left.group(group)).compareTo(number(right.group(group)));
            if (result != 0) return result;
        }
        String leftPre = left.group(4);
        String rightPre = right.group(4);
        if (leftPre == null || rightPre == null) {
            return leftPre == rightPre ? 0 : leftPre == null ? 1 : -1;
        }
        String[] leftParts = leftPre.split("\\.");
        String[] rightParts = rightPre.split("\\.");
        for (int index = 0; index < Math.min(leftParts.length, rightParts.length); index++) {
            String a = leftParts[index];
            String b = rightParts[index];
            boolean aNumeric = a.matches("[0-9]+");
            boolean bNumeric = b.matches("[0-9]+");
            int result = aNumeric && bNumeric ? number(a).compareTo(number(b))
                    : aNumeric != bNumeric ? (aNumeric ? -1 : 1) : a.compareTo(b);
            if (result != 0) return result;
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static Matcher parsed(String version) {
        Matcher result = VERSION.matcher(version);
        if (!result.matches()) throw new IllegalArgumentException("Invalid patch version: " + version);
        String pre = result.group(4);
        if (pre != null) {
            for (String part : pre.split("\\.")) {
                if (part.matches("0[0-9]+")) {
                    throw new IllegalArgumentException("Invalid numeric prerelease identifier: " + version);
                }
            }
        }
        return result;
    }

    private static BigInteger number(String value) {
        return new BigInteger(value);
    }
}
