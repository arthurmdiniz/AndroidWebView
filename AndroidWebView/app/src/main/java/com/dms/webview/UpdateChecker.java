package com.dms.webview;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateChecker {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/arthurmdiniz/AndroidWebView/releases/latest";
    private static final String UPDATES_DIR = "updates";
    private static final String APK_NAME = "Webview-Nativo-update.apk";
    private static final int TIMEOUT_MS = 15000;

    public static void check(Activity activity) {
        Toast.makeText(activity, "Verificando atualização...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject release = fetchLatestRelease();
                String tag = release.getString("tag_name");
                String apkUrl = findApkUrl(release);
                String installed = installedVersion(activity);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (compareVersions(tag, installed) <= 0) {
                        showMessage(activity, "Você já está na versão mais recente (" + installed + ").");
                    } else if (apkUrl == null) {
                        showMessage(activity, "Versão " + tag + " disponível, mas sem APK anexado.");
                    } else {
                        showUpdateDialog(activity, tag, apkUrl);
                    }
                });
            } catch (Exception e) {
                showError(activity, e);
            }
        }).start();
    }

    static int compareVersions(String a, String b) {
        String[] pa = stripVersion(a).split("\\.");
        String[] pb = stripVersion(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int diff = parsePart(i < pa.length ? pa[i] : "0") - parsePart(i < pb.length ? pb[i] : "0");
            if (diff != 0) return diff;
        }
        return 0;
    }

    private static String stripVersion(String version) {
        return version.trim().replaceFirst("^[vV]", "");
    }

    private static int parsePart(String part) {
        String digits = part.replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private static JSONObject fetchLatestRelease() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IOException("Resposta inválida do GitHub", e);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String findApkUrl(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name").endsWith(".apk")) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private static String installedVersion(Activity activity) throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        return info.versionName;
    }

    private static void showUpdateDialog(Activity activity, String tag, String apkUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("Atualização disponível")
                .setMessage("Versão " + tag + " disponível. A atualização é opcional. Deseja atualizar agora?")
                .setPositiveButton("Atualizar", (d, w) -> download(activity, apkUrl))
                .setNegativeButton("Agora não", null)
                .show();
    }

    private static void download(Activity activity, String apkUrl) {
        Toast.makeText(activity, "Baixando atualização...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File apk = downloadApk(activity, apkUrl);
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) install(activity, apk);
                });
            } catch (Exception e) {
                showError(activity, e);
            }
        }).start();
    }

    private static File downloadApk(Activity activity, String apkUrl) throws IOException {
        File dir = new File(activity.getCacheDir(), UPDATES_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta de atualização");
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) f.delete();
        }
        File apk = new File(dir, APK_NAME);
        HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " ao baixar o APK");
            }
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(apk)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
        } finally {
            conn.disconnect();
        }
        return apk;
    }

    private static void install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Permissão necessária")
                    .setMessage("Permita instalar apps deste aplicativo e toque em Verificar atualização novamente.")
                    .setPositiveButton("Abrir configurações", (d, w) -> activity.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()))))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static void showMessage(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Atualização")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static void showError(Activity activity, Exception e) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) {
                Toast.makeText(activity, "Falha na atualização: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
