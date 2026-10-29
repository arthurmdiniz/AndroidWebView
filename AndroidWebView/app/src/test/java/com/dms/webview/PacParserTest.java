package com.dms.webview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class PacParserTest {

    private static final String PAC_SINGLE_PROXY =
        "function FindProxyForURL(url, host) {\n"
        + "    if (shExpMatch(url, \"*.interno.example\")) return \"DIRECT\";\n"
        + "    return \"PROXY squid.example:3128\";\n"
        + "}";

    private static final String PAC_DIRECT_ONLY =
        "function FindProxyForURL(url, host) {\n"
        + "    return \"DIRECT\";\n"
        + "}";

    @Test
    public void extractsProxyFromProxyRule() {
        assertEquals("squid.example:3128", PacParser.extractProxy(PAC_SINGLE_PROXY));
    }

    @Test
    public void returnsNullWhenPacHasOnlyDirect() {
        assertNull(PacParser.extractProxy(PAC_DIRECT_ONLY));
    }

    @Test
    public void usesFirstProxyWhenMoreThanOne() {
        String pac = "return \"PROXY a.example:8080; PROXY b.example:3128\";";
        assertEquals("a.example:8080", PacParser.extractProxy(pac));
    }

    @Test
    public void addsWildcardsToBypassWithoutWildcard() {
        String pac = "if (shExpMatch(url, \"dominio.interno\")) return \"DIRECT\";";
        assertEquals(Collections.singletonList("*dominio.interno*"),
            PacParser.extractBypassRules(pac));
    }

    @Test
    public void discardsBypassWithPath() {
        String pac = "if (shExpMatch(url, \"*.interno.example/app*\")) return \"DIRECT\";"
            + "if (shExpMatch(url, \"*.externo.example*\")) return \"DIRECT\";";
        assertEquals(Collections.singletonList("*.externo.example*"),
            PacParser.extractBypassRules(pac));
    }

    @Test
    public void removesDuplicatedBypassRulesKeepingOrder() {
        String pac = "shExpMatch(url, \"b.example\");"
            + "shExpMatch(url, \"a.example\");"
            + "shExpMatch(url, \"b.example\");";
        List<String> rules = PacParser.extractBypassRules(pac);
        assertEquals(Arrays.asList("*b.example*", "*a.example*"), rules);
    }

    @Test
    public void limitsBypassRulesTo100() {
        StringBuilder pac = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            pac.append("shExpMatch(url, \"host").append(i).append(".example\");");
        }
        assertEquals(100, PacParser.extractBypassRules(pac.toString()).size());
    }

    @Test
    public void bypassIsNeverNull() {
        assertTrue(PacParser.extractBypassRules(PAC_DIRECT_ONLY).isEmpty());
        assertTrue(PacParser.extractBypassRules(null).isEmpty());
    }

    @Test
    public void looksLikePacAcceptsPacScript() {
        assertTrue(PacParser.looksLikePac(PAC_SINGLE_PROXY));
        assertTrue(PacParser.looksLikePac("function findproxyforurl(url, host) {}"));
    }

    @Test
    public void looksLikePacRejectsErrorHtml() {
        String html = "<html><head><title>404 Not Found</title></head>"
            + "<body><h1>Not Found</h1></body></html>";
        assertFalse(PacParser.looksLikePac(html));
        assertFalse(PacParser.looksLikePac(null));
    }
}
