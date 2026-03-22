package app.morphe.extension.boostforreddit.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import app.morphe.extension.shared.Utils;

public class CacheUtils {
    public static File getHttpCacheDir() {
        return new File(Utils.getContext().getCacheDir(), "undelete_http_cache");
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
