package com.dms.webview;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashLogger {
    private static boolean initialized = false;

    public static void init(Context context) {
        if (initialized) return;
        initialized = true;

        final File logDir = new File(Environment.getExternalStorageDirectory(), "Webview");
        logDir.mkdirs();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            File logFile = new File(logDir, "crash_" + timestamp + ".log");

            try (FileWriter fw = new FileWriter(logFile);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println("=== CRASH LOG ===");
                pw.println("Time: " + timestamp);
                pw.println("App: " + context.getPackageName());
                pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println();
                throwable.printStackTrace(pw);
                pw.println();
                pw.println("=== END ===");
            } catch (Exception e) {
                try {
                    File fallback = new File(context.getExternalFilesDir(null), "crash_" + timestamp + ".log");
                    try (FileWriter fw2 = new FileWriter(fallback);
                         PrintWriter pw2 = new PrintWriter(fw2)) {
                        throwable.printStackTrace(pw2);
                    }
                } catch (Exception ignored) {}
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
    }
}
