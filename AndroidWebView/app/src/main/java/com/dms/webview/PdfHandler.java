package com.dms.webview;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PdfHandler {

    public static void cleanTmp() {
        try {
            File dir = new File(new File(Environment.getExternalStorageDirectory(), "Webview"), "tmp");
            if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    public static void handle(final Context context, final String pdfUrl) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(pdfUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                File dir = new File(new File(Environment.getExternalStorageDirectory(), "Webview"), "tmp");
                dir.mkdirs();
                File pdfFile = new File(dir, "url_" + System.currentTimeMillis() + ".pdf");

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(pdfFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }

                ((android.app.Activity) context).runOnUiThread(() ->
                    printPdf(context, pdfFile));
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() ->
                    new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.Theme_DMS))
                        .setTitle("Erro")
                        .setMessage("Nao foi possivel abrir o PDF: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
            }
        }).start();
    }

    public static void handleBytes(Context context, byte[] data, String name) {
        try {
            File dir = new File(new File(Environment.getExternalStorageDirectory(), "Webview"), "tmp");
            dir.mkdirs();
            File pdfFile = new File(dir, "pdf_" + name + ".pdf");
            try (FileOutputStream out = new FileOutputStream(pdfFile)) {
                out.write(data);
            }
            printPdf(context, pdfFile);
        } catch (Exception e) {
            new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.Theme_DMS))
                .setTitle("Erro")
                .setMessage("Nao foi possivel abrir o PDF: " + e.getMessage())
                .setPositiveButton("OK", null)
                .show();
        }
    }

    public static void printPdf(Context context, File pdfFile) {
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) return;

        String jobName = "Documento_" + System.currentTimeMillis();

        printManager.print(jobName, new PrintDocumentAdapter() {
            private byte[] pdfBytes;

            @Override
            public void onStart() {
                super.onStart();
                try {
                    pdfBytes = new byte[(int) pdfFile.length()];
                    try (FileInputStream fis = new FileInputStream(pdfFile)) {
                        int offset = 0;
                        while (offset < pdfBytes.length) {
                            int read = fis.read(pdfBytes, offset, pdfBytes.length - offset);
                            if (read == -1) break;
                            offset += read;
                        }
                    }
                } catch (Exception e) {
                    pdfBytes = null;
                }
            }

            @Override
            public void onLayout(PrintAttributes oldAttributes,
                                  PrintAttributes newAttributes,
                                  CancellationSignal cancellationSignal,
                                  LayoutResultCallback callback,
                                  Bundle extras) {
                if (cancellationSignal.isCanceled() || pdfBytes == null) {
                    callback.onLayoutCancelled();
                    return;
                }
                PrintDocumentInfo info = new PrintDocumentInfo.Builder("pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
                callback.onLayoutFinished(info, true);
            }

            @Override
            public void onWrite(PageRange[] pages,
                                 ParcelFileDescriptor destination,
                                 CancellationSignal cancellationSignal,
                                 WriteResultCallback callback) {
                if (pdfBytes == null) {
                    callback.onWriteFailed("PDF invalido");
                    return;
                }
                try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    out.write(pdfBytes);
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed(e.getMessage());
                }
            }

            @Override
            public void onFinish() {
                super.onFinish();
                pdfBytes = null;
            }
        }, new PrintAttributes.Builder().build());
    }
}
