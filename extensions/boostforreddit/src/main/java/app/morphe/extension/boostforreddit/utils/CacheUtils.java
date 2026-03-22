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
}
