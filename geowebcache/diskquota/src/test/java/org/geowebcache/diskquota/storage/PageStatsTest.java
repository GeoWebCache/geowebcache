package org.geowebcache.diskquota.storage;

import java.math.BigInteger;

import junit.framework.TestCase;

public class PageStatsTest extends TestCase {

    private static final int CREATION_TIME_MINUTES = 1000;

    private MockSystemUtils mockSysUtils;

    public void setUp() {
        mockSysUtils = new MockSystemUtils();
        mockSysUtils.setCurrentTimeMinutes(CREATION_TIME_MINUTES);
        mockSysUtils.setCurrentTimeMillis(CREATION_TIME_MINUTES * 60 * 1000);
        SystemUtils.set(mockSysUtils);
    }

    public void testAddHitsNoFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0f);
        stats.addHitsAndAccessTime(10, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(Float.MIN_VALUE, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddHitsNoFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0f);
        stats.addHitsAndAccessTime(10, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(Float.MIN_VALUE, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddHitsFullFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(1f);
        final int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(numHits, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddHitsFullFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(1f);

        // 10 hits added in the two minutes after page creation
        int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(numHits / 3f, frequencyOfUsePerMinute, 1e-6f);

        // 100 hits added in one minute later
        numHits = 100;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 3, CREATION_TIME_MINUTES);
        frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(110f / 4f, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddHitsHalfFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0.5f);
        final int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(numHits / 2f, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddHitsHalfFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        float fillFactor = 0.5f;
        stats.setFillFactor(fillFactor);
        int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(fillFactor * (numHits / 3f), frequencyOfUsePerMinute, 1e-6f);
    }

    public void testAddTiles() {
        PageStats stats = new PageStats(1);
        final BigInteger maxTiles = BigInteger.valueOf(1000);

        stats.addTiles(1, maxTiles);
        assertEquals(1 / 1000f, stats.getFillFactor(), 1e-6f);

        stats.addTiles(499, maxTiles);
        assertEquals(0.5f, stats.getFillFactor(), 1e-6f);

        stats.addTiles(500, maxTiles);
        assertEquals(1f, stats.getFillFactor(), 1e-6f);
    }

}
