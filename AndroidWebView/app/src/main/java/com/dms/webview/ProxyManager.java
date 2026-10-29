package com.dms.webview;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ProxyInfo;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ProxyManager {
    public interface Callback { void onDone(boolean changed); }
    public interface TextCallback { void onText(String text); }

    private static final String TEST_URL = "https://drive.google.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";
    private static final int PAC_MAX_BYTES = 512 * 1024;
    private static final int PAC_TIMEOUT_MS = 5000;
    private static final int PROBE_CONNECT_TIMEOUT_MS = 5000;
    private static final int PROBE_READ_TIMEOUT_MS = 8000;
    private static final String PRIVATE_BYPASS_1 = "127.0.0.1";
    private static final String PRIVATE_BYPASS_2 = "192.168.*";
    private static final String PRIVATE_BYPASS_3 = "10.*";
    private static final Pattern HOST_PATTERN = Pattern.compile("[\\w.-]+");
    private static final Pattern RULE_PATTERN = Pattern.compile("[\\w.*-]+");

    private static volatile Proxy active = null;
    private static volatile String activeLabel = "";
    private static volatile String activeSource = "";
    private static volatile List<String> bypassRules = new ArrayList<>();
    private static volatile boolean bypassLocal = true;
    private static volatile String status = "Proxy não configurado.";
    private static volatile boolean webViewWarmedUp = false;

    private ProxyManager() {
    }

    public static void init(Context context) {
        String mode = Config.getProxyMode(context);
        bypassLocal = Config.isProxyBypassLocalEnabled(context);
        bypassRules = splitRules(Config.getProxyCachedBypass(context));

        if (Config.MODE_OFF.equals(mode)) {
            setActive(null, 0, "", "Proxy desativado.");
            return;
        }
        if (Config.MODE_MANUAL.equals(mode)) {
            String host = Config.getProxyHost(context);
            int port = Config.getProxyPort(context);
            if (isValidEndpoint(host, port)) {
                setActive(host, port, "manual", "Proxy manual: " + host + ":" + port + ".");
            } else {
                setActive(null, 0, "", "Proxy manual sem host/porta válidos.");
            }
            return;
        }
        String host = Config.getProxyCachedHost(context);
        int port = Config.getProxyCachedPort(context);
        String source = Config.getProxyCachedSource(context);
        if (isValidEndpoint(host, port)) {
            setActive(host, port, source,
                "Proxy ativo: " + host + ":" + port + " (detectado via " + source + ").");
        } else {
            setActive(null, 0, "", "Nenhum proxy detectado. Use \"Detectar agora\".");
        }
    }

    public static void warmUpWebView(Context context) {
        if (webViewWarmedUp) return;
        try {
            WebView wv = new WebView(context);
            wv.destroy();
            webViewWarmedUp = true;
        } catch (RuntimeException e) {
            status = "Falha ao inicializar o WebView: " + e.getMessage();
        }
    }

    public static void applyToWebView(Context ctx, Runnable onDone) {
        Runnable done = onDone != null ? onDone : () -> { };
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            status = "WebView sem suporte a proxy; apenas chamadas nativas usam o proxy.";
            done.run();
            return;
        }
        ProxyController controller = ProxyController.getInstance();
        Proxy p = active;
        if (p == null) {
            controller.clearProxyOverride(ContextCompat.getMainExecutor(ctx), done);
            return;
        }
        ProxyConfig.Builder builder = new ProxyConfig.Builder()
            .addProxyRule(activeLabel)
            .addDirect();
        if (bypassLocal) {
            builder.bypassSimpleHostnames()
                .addBypassRule(PRIVATE_BYPASS_1)
                .addBypassRule(PRIVATE_BYPASS_2)
                .addBypassRule(PRIVATE_BYPASS_3);
        }
        for (String rule : bypassRules) {
            if (RULE_PATTERN.matcher(rule).matches()) builder.addBypassRule(rule);
        }
        controller.setProxyOverride(builder.build(), ContextCompat.getMainExecutor(ctx), done);
    }

    public static void discover(Context ctx, Callback cb) {
        Context app = ctx.getApplicationContext();
        List<String> candidates = candidateUrls(app);
        new Thread(() -> runDiscovery(app, candidates, cb)).start();
    }

    private static void runDiscovery(Context app, List<String> candidates, Callback cb) {
        String prevLabel = activeLabel;
        List<String> prevBypass = bypassRules;
        List<String> tried = new ArrayList<>();
        String foundHost = null;
        int foundPort = 0;
        String foundSource = null;
        List<String> foundBypass = null;

        for (String candidate : candidates) {
            String source = hostOf(candidate);
            String body;
            try {
                body = fetchPac(candidate);
            } catch (IOException e) {
                tried.add(source + " (" + e.getMessage() + ")");
                continue;
            }
            if (!PacParser.looksLikePac(body)) {
                tried.add(source + " (não é um PAC)");
                continue;
            }
            String proxy = PacParser.extractProxy(body);
            if (proxy == null) {
                tried.add(source + " (sem regra PROXY)");
                continue;
            }
            int colon = proxy.lastIndexOf(':');
            String host = proxy.substring(0, colon);
            int port = Integer.parseInt(proxy.substring(colon + 1));
            if (!isValidEndpoint(host, port)) {
                tried.add(source + " (proxy inválido: " + proxy + ")");
                continue;
            }
            if (!probe(host, port)) {
                tried.add(source + " (proxy " + proxy + " não respondeu)");
                continue;
            }
            foundHost = host;
            foundPort = port;
            foundSource = source;
            foundBypass = PacParser.extractBypassRules(body);
            break;
        }

        if (foundHost != null) {
            long now = System.currentTimeMillis();
            Config.saveProxyCache(app, foundHost, foundPort, foundSource, joinRules(foundBypass), now);
            String label = foundHost + ":" + foundPort;
            if (Config.MODE_AUTO.equals(Config.getProxyMode(app))) {
                bypassRules = foundBypass;
                boolean changed = !label.equals(prevLabel) || !foundBypass.equals(prevBypass);
                setActive(foundHost, foundPort, foundSource,
                    "Proxy ativo: " + label + " (detectado via " + foundSource + ").");
                applyToWebView(app, () -> postCallback(cb, changed));
                return;
            }
            status = "Detectado: " + label + " (via " + foundSource + ").";
        } else {
            Config.clearProxyCache(app);
            status = "Nenhum proxy detectado. Endereços tentados: "
                + (tried.isEmpty() ? "nenhum" : String.join(", ", tried)) + ".";
            if (Config.MODE_AUTO.equals(Config.getProxyMode(app))) {
                boolean changed = prevLabel != null && !prevLabel.isEmpty();
                bypassRules = new ArrayList<>();
                setActive(null, 0, "", status);
                applyToWebView(app, () -> postCallback(cb, changed));
                return;
            }
        }
        postCallback(cb, false);
    }

    public static HttpURLConnection open(URL url, int connectTimeout, int readTimeout) throws IOException {
        Proxy p = isBypassed(url.getHost()) ? null : active;
        HttpURLConnection c = (HttpURLConnection) (p == null ? url.openConnection() : url.openConnection(p));
        c.setConnectTimeout(connectTimeout);
        c.setReadTimeout(readTimeout);
        return c;
    }

    public static String status() {
        return status;
    }

    public static String label() {
        return activeLabel;
    }

    public static String testConnection() {
        long start = System.currentTimeMillis();
        String via = activeLabel.isEmpty() ? "conexão direta" : "proxy " + activeLabel;
        try {
            HttpURLConnection c = open(new URL(TEST_URL), PROBE_CONNECT_TIMEOUT_MS, PROBE_READ_TIMEOUT_MS);
            try {
                int code = c.getResponseCode();
                return "HTTP " + code + " em " + (System.currentTimeMillis() - start) + " ms (" + via + ")";
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            return "Falha via " + via + ": " + e.getMessage();
        }
    }

    public static void runDiagnostics(Context ctx, TextCallback cb) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Modo: ").append(Config.getProxyMode(app)).append('\n');
            sb.append("Proxy ativo: ").append(activeLabel.isEmpty() ? "nenhum (direto)" : activeLabel).append('\n');
            sb.append("Origem: ").append(activeSource.isEmpty() ? "-" : activeSource).append('\n');
            sb.append("Status: ").append(status).append('\n');
            sb.append("Regras de bypass: ").append(bypassRules.size()).append('\n');
            sb.append("Proxy do sistema: ").append(systemProxy(app)).append('\n');
            sb.append("Domínios de busca: ").append(joinOrDash(searchDomains(app))).append('\n');
            sb.append("WebView com suporte a proxy: ")
                .append(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ? "sim" : "não")
                .append('\n');
            sb.append("Teste (drive.google.com): ").append(testConnection());
            new Handler(Looper.getMainLooper()).post(() -> cb.onText(sb.toString()));
        }).start();
    }

    private static void setActive(String host, int port, String source, String message) {
        if (host == null || host.isEmpty()) {
            active = null;
            activeLabel = "";
            activeSource = "";
        } else {
            active = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
            activeLabel = host + ":" + port;
            activeSource = source;
        }
        status = message;
    }

    private static boolean isBypassed(String host) {
        if (host == null || host.isEmpty()) return true;
        String h = host.toLowerCase(Locale.ROOT);
        if (!h.contains(".") || h.equals("localhost") || h.equals("127.0.0.1")) return true;
        if (bypassLocal && (h.startsWith("192.168.") || h.startsWith("10."))) return true;
        for (String rule : bypassRules) {
            if (globMatches(rule.toLowerCase(Locale.ROOT), h)) return true;
        }
        return false;
    }

    private static boolean globMatches(String glob, String text) {
        StringBuilder regex = new StringBuilder();
        for (char ch : glob.toCharArray()) {
            if (ch == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return text.matches(regex.toString());
    }

    private static boolean isValidEndpoint(String host, int port) {
        return host != null && HOST_PATTERN.matcher(host).matches() && port > 0 && port <= 65535;
    }

    private static List<String> candidateUrls(Context ctx) {
        List<String> list = new ArrayList<>();
        list.add("http://wpad/wpad.dat");
        for (String domain : searchDomains(ctx)) {
            String dom = domain.startsWith(".") ? domain.substring(1) : domain;
            if (dom.isEmpty()) continue;
            String url = "http://wpad." + dom + "/wpad.dat";
            if (!list.contains(url)) list.add(url);
        }
        return list;
    }

    private static List<String> searchDomains(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = net == null ? null : cm.getLinkProperties(net);
            String domains = lp == null ? null : lp.getDomains();
            if (domains != null) {
                for (String d : domains.split("[\\s,]+")) {
                    if (!d.isEmpty()) out.add(d);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static String systemProxy(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            ProxyInfo info = cm == null ? null : cm.getDefaultProxy();
            if (info == null || info.getHost() == null || info.getHost().isEmpty()) return "nenhum";
            return info.getHost() + ":" + info.getPort();
        } catch (RuntimeException e) {
            return "indisponível";
        }
    }

    private static String fetchPac(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(PAC_TIMEOUT_MS);
        c.setReadTimeout(PAC_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > PAC_MAX_BYTES) throw new IOException("arquivo maior que 512 KB");
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static boolean probe(String host, int port) {
        try {
            Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            HttpURLConnection c = (HttpURLConnection) new URL(TEST_URL).openConnection(p);
            c.setConnectTimeout(PROBE_CONNECT_TIMEOUT_MS);
            c.setReadTimeout(PROBE_READ_TIMEOUT_MS);
            try {
                c.getResponseCode();
                return true;
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (IOException e) {
            return url;
        }
    }

    private static List<String> splitRules(String raw) {
        List<String> rules = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return rules;
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) rules.add(line.trim());
        }
        return rules;
    }

    private static String joinRules(List<String> rules) {
        return rules == null ? "" : String.join("\n", rules);
    }

    private static String joinOrDash(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static void postCallback(Callback cb, boolean changed) {
        if (cb == null) return;
        new Handler(Looper.getMainLooper()).post(() -> cb.onDone(changed));
    }
}
