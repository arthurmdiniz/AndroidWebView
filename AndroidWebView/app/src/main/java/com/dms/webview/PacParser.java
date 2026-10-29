package com.dms.webview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PacParser {
    private static final int MAX_BYPASS_RULES = 100;
    private static final Pattern PROXY_PATTERN =
            Pattern.compile("(?i)PROXY\\s+([\\w.-]+):(\\d{1,5})");
    private static final Pattern BYPASS_PATTERN =
            Pattern.compile("shExpMatch\\(url,\\s*\"([^\"]+)\"\\)");

    private PacParser() {
    }

    public static boolean looksLikePac(String body) {
        return body != null && body.toLowerCase(java.util.Locale.ROOT).contains("findproxyforurl");
    }

    public static String extractProxy(String pac) {
        if (pac == null) return null;
        Matcher m = PROXY_PATTERN.matcher(pac);
        if (!m.find()) return null;
        return m.group(1) + ":" + m.group(2);
    }

    public static List<String> extractBypassRules(String pac) {
        Set<String> rules = new LinkedHashSet<>();
        if (pac == null) return new ArrayList<>();
        Matcher m = BYPASS_PATTERN.matcher(pac);
        while (m.find() && rules.size() < MAX_BYPASS_RULES) {
            String rule = normalizeRule(m.group(1));
            if (rule != null) rules.add(rule);
        }
        return new ArrayList<>(rules);
    }

    private static String normalizeRule(String raw) {
        String rule = raw.trim();
        if (rule.isEmpty() || rule.contains("/")) return null;
        if (!rule.startsWith("*")) rule = "*" + rule;
        if (!rule.endsWith("*")) rule = rule + "*";
        return rule;
    }
}
