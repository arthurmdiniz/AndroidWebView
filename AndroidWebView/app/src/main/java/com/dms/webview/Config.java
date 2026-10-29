package com.dms.webview;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {
    private static final String PREFS_NAME = "dms_config";
    private static final String KEY_URL = "url";
    private static final String KEY_LOGGING = "network_logging";
    private static final String KEY_SIDE_CAPTURE = "side_capture_button";
    private static final String KEY_PHOTO_QUALITY = "photo_quality";
    private static final String KEY_CAMERA2 = "camera2_engine_enabled";
    public static final int PHOTO_QUALITY_LOW = 80;
    public static final int PHOTO_QUALITY_MEDIUM = 90;
    public static final int PHOTO_QUALITY_HIGH = 100;
    public static final String DEFAULT_URL = "https://www.google.com";
    public static final String PREDEFINED_URL = "http://localhost:81/mobile/";
    private static final String KEY_PROXY_MODE = "proxy_mode";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final String KEY_PROXY_BYPASS_LOCAL = "proxy_bypass_local";
    private static final String KEY_PROXY_CACHED_HOST = "proxy_cached_host";
    private static final String KEY_PROXY_CACHED_PORT = "proxy_cached_port";
    private static final String KEY_PROXY_CACHED_SOURCE = "proxy_cached_source";
    private static final String KEY_PROXY_CACHED_BYPASS = "proxy_cached_bypass";
    private static final String KEY_PROXY_CACHED_AT = "proxy_cached_at";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";
    public static final String MODE_OFF = "off";
    public static final int DEFAULT_PROXY_PORT = 3128;

    public static String loadUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_URL, null);
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        return null;
    }

    public static void saveUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_URL, url.trim()).apply();
    }

    public static void clear(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_URL).apply();
    }

    public static boolean isNetworkLoggingEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LOGGING, false);
    }

    public static void setNetworkLoggingEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LOGGING, enabled).apply();
    }

    public static boolean isSideCaptureButtonEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SIDE_CAPTURE, false);
    }

    public static void setSideCaptureButtonEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SIDE_CAPTURE, enabled).apply();
    }

    public static boolean isCamera2Enabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CAMERA2, false);
    }

    public static void setCamera2Enabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_CAMERA2, enabled).apply();
    }

    public static int getPhotoQuality(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PHOTO_QUALITY, PHOTO_QUALITY_HIGH);
    }

    public static void setPhotoQuality(Context context, int quality) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_PHOTO_QUALITY, quality).apply();
    }

    public static String getProxyMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROXY_MODE, MODE_AUTO);
    }

    public static void setProxyMode(Context context, String mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROXY_MODE, mode).apply();
    }

    public static String getProxyHost(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROXY_HOST, "");
    }

    public static void setProxyHost(Context context, String host) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROXY_HOST, host.trim()).apply();
    }

    public static int getProxyPort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT);
    }

    public static void setProxyPort(Context context, int port) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_PROXY_PORT, port).apply();
    }

    public static boolean isProxyBypassLocalEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PROXY_BYPASS_LOCAL, true);
    }

    public static void setProxyBypassLocalEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PROXY_BYPASS_LOCAL, enabled).apply();
    }

    public static String getProxyCachedHost(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROXY_CACHED_HOST, "");
    }

    public static int getProxyCachedPort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PROXY_CACHED_PORT, 0);
    }

    public static String getProxyCachedSource(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROXY_CACHED_SOURCE, "");
    }

    public static String getProxyCachedBypass(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROXY_CACHED_BYPASS, "");
    }

    public static long getProxyCachedAt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_PROXY_CACHED_AT, 0);
    }

    public static void saveProxyCache(Context context, String host, int port, String source,
                                      String bypass, long at) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_PROXY_CACHED_HOST, host)
            .putInt(KEY_PROXY_CACHED_PORT, port)
            .putString(KEY_PROXY_CACHED_SOURCE, source)
            .putString(KEY_PROXY_CACHED_BYPASS, bypass)
            .putLong(KEY_PROXY_CACHED_AT, at)
            .apply();
    }

    public static void clearProxyCache(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_PROXY_CACHED_HOST)
            .remove(KEY_PROXY_CACHED_PORT)
            .remove(KEY_PROXY_CACHED_SOURCE)
            .remove(KEY_PROXY_CACHED_BYPASS)
            .remove(KEY_PROXY_CACHED_AT)
            .apply();
    }
}
