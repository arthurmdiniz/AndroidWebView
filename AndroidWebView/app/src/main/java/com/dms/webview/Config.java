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
}
