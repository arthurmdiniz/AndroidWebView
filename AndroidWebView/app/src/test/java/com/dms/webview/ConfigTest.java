package com.dms.webview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class ConfigTest {

    @Test
    public void photoQualityLevelsAreOrdered() {
        assertEquals(80, Config.PHOTO_QUALITY_LOW);
        assertEquals(90, Config.PHOTO_QUALITY_MEDIUM);
        assertEquals(100, Config.PHOTO_QUALITY_HIGH);
    }

    @Test
    public void defaultUrlsAreConfigured() {
        assertNotNull(Config.DEFAULT_URL);
        assertNotNull(Config.PREDEFINED_URL);
    }
}
