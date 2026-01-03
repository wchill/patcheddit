package app.morphe.extension.boostforreddit.utils;

import android.util.LruCache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class CacheUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static File getHttpCacheDir() {
        return new File(Utils.getContext().getCacheDir(), "undelete_http_cache");
    }

    public static File getMapCacheDir() {
        File f = new File(Utils.getContext().getCacheDir(), "undelete_obj_cache");
        if (!f.exists()) {
            boolean success = f.mkdir();
            if (!success) {
                throw new RuntimeException("Unable to create cache dir " + f);
            }
        }
        return f;
    }

    public static LruCache<String, String> readCacheFromDisk(String cacheName, int size) {
        try {
            File dir = getMapCacheDir();
            File child = new File(dir, cacheName + ".json");
            // Can't use readValue because it was optimized out
            String json = readStringFromFile(child);
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            LruCache<String, String> cache = new LruCache<>(size);
            node.fields();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                cache.put(entry.getKey(), entry.getValue().asText());
            }
            return cache;
        } catch (FileNotFoundException e) {
            return new LruCache<>(size);
        } catch (Exception e) {
            LoggingUtils.logException(false, e::toString, e);
            throw new RuntimeException(e);
        }
    }

    public static <K, V> void writeCacheToDisk(String cacheName, LruCache<K, V> cache) {
        try {
            File dir = getMapCacheDir();
            File child = new File(dir, cacheName + ".json");
            String json = objectMapper.writeValueAsString(cache.snapshot());
            writeStringToFile(child, json);
        } catch (Exception e) {
            LoggingUtils.logException(false, e::toString, e);
            throw new RuntimeException(e);
        }
    }

    public static void writeStringToFile(String p, String s) throws IOException {
        // TODO: Use java.nio
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(p))) {
            bw.write(s);
        }
    }

    public static void writeStringToFile(File f, String s) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write(s);
        }
    }

    private static String readStringFromFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line;
        while ((line = br.readLine()) != null)
        {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
