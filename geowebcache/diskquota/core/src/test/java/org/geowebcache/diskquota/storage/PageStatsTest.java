package org.geowebcache.diskquota.storage;

import java.math.BigInteger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PageStatsTest {

    private static final int CREATION_TIME_MINUTES = 1000;

    private MockSystemUtils mockSysUtils;

    @Before
    public void setUp() {
        mockSysUtils = new MockSystemUtils();
        mockSysUtils.setCurrentTimeMinutes(CREATION_TIME_MINUTES);
        mockSysUtils.setCurrentTimeMillis(CREATION_TIME_MINUTES * 60 * 1000);
        SystemUtils.set(mockSysUtils);
    }

    @Test
    public void testAddHitsNoFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0f);
        stats.addHitsAndAccessTime(10, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(Float.MIN_VALUE, frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testAddHitsNoFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0f);
        stats.addHitsAndAccessTime(10, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(Float.MIN_VALUE, frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testAddHitsFullFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(1f);
        final int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(numHits, frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testAddHitsFullFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(1f);

        // 10 hits added in the two minutes after page creation
        int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(numHits / 3f, frequencyOfUsePerMinute, 1e-6f);

        // 100 hits added in one minute later
        numHits = 100;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 3, CREATION_TIME_MINUTES);
        frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(110f / 4f, frequencyOfUsePerMinute, 1e-5f);
    }

    @Test
    public void testAddHitsHalfFillFactor() {
        PageStats stats = new PageStats(1);
        stats.setFillFactor(0.5f);
        final int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(numHits / 2f, frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testAddHitsHalfFillFactorNewLastAccessTime() {
        PageStats stats = new PageStats(1);
        float fillFactor = 0.5f;
        stats.setFillFactor(fillFactor);
        int numHits = 10;
        stats.addHitsAndAccessTime(numHits, CREATION_TIME_MINUTES + 2, CREATION_TIME_MINUTES);
        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        Assert.assertEquals(fillFactor * (numHits / 3f), frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testAddTiles() {
        PageStats stats = new PageStats(1);
        final BigInteger maxTiles = BigInteger.valueOf(1000);

        stats.addTiles(1, maxTiles);
        Assert.assertEquals(1 / 1000f, stats.getFillFactor(), 1e-6f);

        stats.addTiles(499, maxTiles);
        Assert.assertEquals(0.5f, stats.getFillFactor(), 1e-6f);

        stats.addTiles(500, maxTiles);
        Assert.assertEquals(1f, stats.getFillFactor(), 1e-6f);
    }
}
