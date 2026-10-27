package com.dms.webview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateCheckerTest {

    @Test
    public void sameVersionWithTagPrefixIsEqual() {
        assertEquals(0, UpdateChecker.compareVersions("v1.0.0", "1.0"));
    }

    @Test
    public void newerReleaseIsGreater() {
        assertTrue(UpdateChecker.compareVersions("v1.1.0", "1.0") > 0);
        assertTrue(UpdateChecker.compareVersions("v2.0.0", "1.9.9") > 0);
    }

    @Test
    public void olderReleaseIsLess() {
        assertTrue(UpdateChecker.compareVersions("v1.0.0", "1.0.1") < 0);
    }
}
