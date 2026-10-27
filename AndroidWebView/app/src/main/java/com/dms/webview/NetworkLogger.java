package com.dms.webview;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class NetworkLogger {
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static volatile boolean running = false;
    private static volatile boolean enabled = false;

    public static void setEnabled(boolean on) {
        enabled = on;
        if (on && !running) {
            running = true;
            Thread thread = new Thread(NetworkLogger::worker, "NetworkLogger");
            thread.setDaemon(true);
            thread.start();
        }
        if (!on) queue.clear();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void worker() {
        while (running || !queue.isEmpty()) {
            try {
                String entry = queue.poll();
                if (entry == null) {
                    Thread.sleep(200);
                    continue;
                }
                writeEntry(entry);
            } catch (Exception ignored) {}
        }
    }

    public static void log(String method, String url, int statusCode,
                            String reqHeaders, String resHeaders,
                            String reqBody, String resBody) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date());

        sb.append("=".repeat(120)).append("\n");
        sb.append(timestamp).append("\n");
        sb.append("Requisicao: ").append(url).append("\n");
        if (reqBody != null && !reqBody.isEmpty()) {
            sb.append(reqBody).append("\n");
        }
        sb.append("\n");
        sb.append("Resposta:\n");
        if (resBody != null && !resBody.isEmpty()) {
            sb.append(resBody).append("\n");
        }

        queue.offer(sb.toString());
    }

    private static void writeEntry(String entry) {
        String dateStr = new SimpleDateFormat("ddMMyy", Locale.US).format(new Date());
        File dir = new File(Environment.getExternalStorageDirectory(), "Webview");
        dir.mkdirs();
        File logFile = new File(dir, "network_" + dateStr + ".log");

        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            int count = 1;
            while (new File(dir, "network_" + dateStr + "_" + count + ".log").exists()
                    && new File(dir, "network_" + dateStr + "_" + count + ".log").length() > MAX_FILE_SIZE) {
                count++;
            }
            logFile = new File(dir, "network_" + dateStr + "_" + count + ".log");
        }

        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
            pw.flush();
        } catch (Exception ignored) {}
    }

    public static String getInjectScript() {
        return "(function() {\n" +
            "  if (window.__networkLogged) return;\n" +
            "  window.__networkLogged = true;\n" +
            "\n" +
            "  var origOpen = XMLHttpRequest.prototype.open;\n" +
            "  var origSend = XMLHttpRequest.prototype.send;\n" +
            "  var origSetReqHeader = XMLHttpRequest.prototype.setRequestHeader;\n" +
            "\n" +
            "  XMLHttpRequest.prototype.open = function(method, url) {\n" +
            "    this._xhrMethod = method;\n" +
            "    this._xhrUrl = (typeof url === 'string' ? url : url.toString());\n" +
            "    this._xhrReqHeaders = {};\n" +
            "    return origOpen.apply(this, arguments);\n" +
            "  };\n" +
            "\n" +
            "  XMLHttpRequest.prototype.setRequestHeader = function(header, value) {\n" +
            "    this._xhrReqHeaders[header] = value;\n" +
            "    return origSetReqHeader.apply(this, arguments);\n" +
            "  };\n" +
            "\n" +
            "  XMLHttpRequest.prototype.send = function(body) {\n" +
            "    var xhr = this;\n" +
            "    xhr.addEventListener('load', function() {\n" +
            "      var reqBody = (body && typeof body === 'string') ? body : '';\n" +
            "      var resBody = xhr.responseText || '';\n" +
            "      var url = xhr._xhrUrl || '';\n" +
            "      Android.logNetwork(xhr._xhrMethod || 'GET', url, xhr.status, '', '', reqBody, resBody);\n" +
            "    });\n" +
            "    xhr.addEventListener('error', function() {\n" +
            "      Android.logNetwork(xhr._xhrMethod || 'GET', xhr._xhrUrl || '', 0, '', '', (body || ''), 'NETWORK ERROR');\n" +
            "    });\n" +
            "    return origSend.apply(this, arguments);\n" +
            "  };\n" +
            "\n" +
            "  var origFetch = window.fetch;\n" +
            "  window.fetch = function(input, init) {\n" +
            "    var url = (typeof input === 'string' ? input : (input.url || input.toString()));\n" +
            "    if (url.startsWith('blob:')) return origFetch.apply(this, arguments);\n" +
            "    var method = (init && init.method) || 'GET';\n" +
            "    var reqBody = (init && init.body && typeof init.body === 'string') ? init.body : '';\n" +
            "    return origFetch.apply(this, arguments).then(function(response) {\n" +
            "      response.clone().text().then(function(body) {\n" +
            "        Android.logNetwork(method, url, response.status, '', '', reqBody, body);\n" +
            "      }).catch(function() {});\n" +
            "      return response;\n" +
            "    }).catch(function(err) {\n" +
            "      Android.logNetwork(method, url, 0, '', '', reqBody, 'FETCH ERROR: ' + err.message);\n" +
            "    });\n" +
            "  };\n" +
            "})();";
    }
}
