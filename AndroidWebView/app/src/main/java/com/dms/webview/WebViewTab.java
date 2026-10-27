package com.dms.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Base64;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WebViewTab {
    public static final int PERMISSION_REQUEST_CODE = 1001;
    private static PermissionRequest pendingPermissionRequest = null;
    private static ValueCallback<android.net.Uri[]> fileChooserCallback;

    private final WebView webView;
    private String currentUrl;
    private TitleChangeListener titleListener;
    private LoadListener loadListener;
    private static boolean cacheCleared = false;

    public static boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE || pendingPermissionRequest == null)
            return false;
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
        } else {
            pendingPermissionRequest.deny();
        }
        pendingPermissionRequest = null;
        return true;
    }

    public static void handleFileChooserResult(int resultCode, Intent data) {
        if (fileChooserCallback == null) return;
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri result = null;
            if (data.getData() != null) {
                result = data.getData();
            } else if (data.hasExtra("photo_path")) {
                result = android.net.Uri.fromFile(new java.io.File(data.getStringExtra("photo_path")));
            }
            fileChooserCallback.onReceiveValue(result != null
                ? new android.net.Uri[]{ result }
                : null);
        } else {
            fileChooserCallback.onReceiveValue(null);
        }
        fileChooserCallback = null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public WebViewTab(MainActivity activity, String url) {
        webView = new WebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        if (!cacheCleared) {
            cacheCleared = true;
            clearCache(activity);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                currentUrl = url;
                if (loadListener != null) loadListener.onPageStarted();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadListener != null) loadListener.onPageFinished();
                view.evaluateJavascript(getBlobScript(), null);
                if (NetworkLogger.isEnabled()) {
                    view.evaluateJavascript(NetworkLogger.getInjectScript(), null);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                logError("WEBVIEW_ERROR", errorCode + " - " + description + " [" + failingUrl + "]");
                if (loadListener != null) loadListener.onError(description);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                             WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && loadListener != null) {
                    loadListener.onError("HTTP " + errorResponse.getStatusCode());
                }
                if (request.isForMainFrame()) {
                    logError("HTTP_ERROR", errorResponse.getStatusCode() + " [" + request.getUrl() + "]");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                String method = request.getMethod();
                if (u.startsWith("app://")) {
                    if ("app://settings".equals(u)) {
                        activity.runOnUiThread(() -> activity.startActivity(
                            new Intent(activity, SettingsActivity.class)));
                    }
                    return true;
                }
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    if (NetworkLogger.isEnabled()) {
                        NetworkLogger.log(method, u, 0, "", "", "", "(navegacao principal)");
                    }
                    view.loadUrl(u);
                }
                if (u.startsWith("blob:")) {
                    view.evaluateJavascript(blobToJs(u), null);
                    return true;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (titleListener != null) {
                    titleListener.onTitleChanged(title);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    logError("JS_ERROR", msg.message() + " [" + msg.sourceId() + ":" + msg.lineNumber() + "]");
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                List<String> missing = new ArrayList<>();
                boolean needsCamera = false;
                for (String res : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res))
                        needsCamera = true;
                }
                if (needsCamera && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(Manifest.permission.CAMERA);
                }
                if (missing.isEmpty()) {
                    request.grant(request.getResources());
                } else {
                    pendingPermissionRequest = request;
                    ActivityCompat.requestPermissions(activity,
                        missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                              ValueCallback<android.net.Uri[]> filePathCallback,
                                              FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;

                boolean useCamera2 = Config.isCamera2Enabled(activity) && CameraEngine.isSupported(activity);
                Intent cameraIntent = new Intent(activity,
                        useCamera2 ? Camera2Activity.class : CameraActivity.class);
                activity.startActivityForResult(cameraIntent, 1003);
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                activity.runOnUiThread(() -> {
                    WebView popupWebView = new WebView(view.getContext());
                    popupWebView.setWebViewClient(new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                            String popupUrl = r.getUrl().toString();
                            v.destroy();
                            if (popupUrl.startsWith("blob:")) {
                                view.evaluateJavascript(blobToJs(popupUrl), null);
                                return true;
                            }
                            if (!popupUrl.startsWith("http://") && !popupUrl.startsWith("https://"))
                                return true;
                            if (popupUrl.toLowerCase(Locale.US).endsWith(".pdf")) {
                                PdfHandler.handle(activity, popupUrl);
                            } else {
                                activity.addTab(popupUrl);
                            }
                            return true;
                        }
                    });
                    WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(popupWebView);
                    resultMsg.sendToTarget();
                });
                return true;
            }
        });

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void openInNewTab(String url) {
                activity.runOnUiThread(() -> activity.addTab(url));
            }

            @JavascriptInterface
            public void openPdf(String dataUrl) {
                activity.runOnUiThread(() -> {
                    try {
                        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        PdfHandler.handleBytes(activity, bytes, String.valueOf(System.currentTimeMillis()));
                    } catch (Exception e) {
                        android.widget.Toast.makeText(activity,
                            "Erro ao abrir PDF: " + e.getMessage(),
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @JavascriptInterface
            public void logNetwork(String method, String url, int statusCode,
                                    String reqHeaders, String resHeaders,
                                    String reqBody, String resBody) {
                NetworkLogger.log(method, url, statusCode,
                    reqHeaders != null ? reqHeaders : "",
                    resHeaders != null ? resHeaders : "",
                    reqBody != null ? reqBody : "",
                    resBody != null ? resBody : "");
            }
        }, "Android");

        NetworkLogger.setEnabled(Config.isNetworkLoggingEnabled(activity));
        webView.loadUrl(url);
        currentUrl = url;
    }

    private static String blobToJs(String blobUrl) {
        String safe = blobUrl.replace("\\", "\\\\").replace("'", "\\'");
        return "(function(){" +
            "fetch('" + safe + "').then(function(r){return r.blob()}).then(function(blob){" +
            "  var reader=new FileReader();" +
            "  reader.onloadend=function(){" +
            "    var d=reader.result;" +
            "    if(d.indexOf('application/pdf')>0){" +
            "      Android.openPdf(d);" +
            "    }else{" +
            "      Android.openInNewTab(d);" +
            "    }" +
            "  };" +
            "  reader.readAsDataURL(blob);" +
            "});" +
            "})();";
    }

    private static String getBlobScript() {
        return "(function(){" +
            "if(window.__blobHooked)return;" +
            "window.__blobHooked=true;" +
            "function blobToDataUrl(blobUrl){" +
            "  return fetch(blobUrl).then(function(r){return r.blob()}).then(function(blob){" +
            "    return new Promise(function(resolve){" +
            "      var reader=new FileReader();" +
            "      reader.onloadend=function(){resolve(reader.result)};" +
            "      reader.readAsDataURL(blob);" +
            "    });" +
            "  });" +
            "}" +
            "var origOpen=window.open;" +
            "window.open=function(url,name,features){" +
            "  if(typeof url==='string'&&url.startsWith('blob:')){" +
            "    blobToDataUrl(url).then(function(dataUrl){" +
            "      if(dataUrl.indexOf('application/pdf')>0){" +
            "        Android.openPdf(dataUrl);" +
            "      }else{" +
            "        Android.openInNewTab(dataUrl);" +
            "      }" +
            "    });" +
            "    return null;" +
            "  }" +
            "  return origOpen(url,name,features);" +
            "};" +
            "document.addEventListener('click',function(e){" +
            "  var link=e.target.closest('a');" +
            "  if(link&&link.href&&link.href.startsWith('blob:')){" +
            "    e.preventDefault();" +
            "    blobToDataUrl(link.href).then(function(dataUrl){" +
            "      if(dataUrl.indexOf('application/pdf')>0){" +
            "        Android.openPdf(dataUrl);" +
            "      }else{" +
            "        Android.openInNewTab(dataUrl);" +
            "      }" +
            "    });" +
            "  }" +
            "},true);" +
            "})();";
    }

    private static void clearCache(MainActivity activity) {
        try {
            WebView wv = new WebView(activity);
            wv.clearCache(true);
            wv.destroy();
        } catch (Exception ignored) {}
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        activity.deleteDatabase("webview.db");
        activity.deleteDatabase("webviewCache.db");
        activity.deleteDatabase("webviewCacheChromium.db");
    }

    private static void logError(String tag, String message) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "Webview");
            dir.mkdirs();
            String dateStr = new SimpleDateFormat("ddMMyy", Locale.US).format(new Date());
            String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date());

            File logFile = new File(dir, "error_" + dateStr + ".log");
            if (logFile.exists() && logFile.length() > 10 * 1024 * 1024) {
                int count = 1;
                while (new File(dir, "error_" + dateStr + "_" + count + ".log").exists()
                        && new File(dir, "error_" + dateStr + "_" + count + ".log").length() > 10 * 1024 * 1024) {
                    count++;
                }
                logFile = new File(dir, "error_" + dateStr + "_" + count + ".log");
            }

            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println("[" + timestamp + "] [" + tag + "] " + message);
            }
        } catch (Exception ignored) {}
    }

    public WebView getWebView() {
        return webView;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void injectNetworkScript() {
        webView.evaluateJavascript(NetworkLogger.getInjectScript(), null);
    }

    public void destroy() {
        webView.destroy();
    }

    public void setTitleChangeListener(TitleChangeListener listener) {
        this.titleListener = listener;
    }

    public void setLoadListener(LoadListener listener) {
        this.loadListener = listener;
    }

    interface TitleChangeListener {
        void onTitleChanged(String title);
    }

    interface LoadListener {
        void onPageStarted();
        void onPageFinished();
        void onError(String description);
    }
}
