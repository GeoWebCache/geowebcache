package org.geowebcache.diskquota.storage;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;

@Entity
public class PageStats implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 719776699585233200L;

    @PrimaryKey(sequence = "page_stats_seq")
    private long id;

    @SecondaryKey(name = "page_stats_by_page_id", relate = Relationship.ONE_TO_ONE, relatedEntity = TilePage.class, onRelatedEntityDelete = DeleteAction.CASCADE)
    private long pageId;

    /**
     * Approximate average frequency of use of this page per minute, computed each time page
     * {@link #addHits(long) hits } are added based on the previous frequency of use, the time
     * elapsed since the last use ({@link #lastAccessTimeMinutes}), and the new number of hits added
     * in that period of time.
     */
    @SecondaryKey(name = "LFU", relate = Relationship.MANY_TO_ONE)
    private float frequencyOfUse;

    @SecondaryKey(name = "LRU", relate = Relationship.MANY_TO_ONE)
    private int lastAccessTimeMinutes;

    @SecondaryKey(name = "fill_factor", relate = Relationship.MANY_TO_ONE)
    private float fillFactor;

    PageStats() {
        //
    }

    public PageStats(long pageId) {
        this.pageId = Long.valueOf(pageId);
        // should be the same than the tile creation time as is used as a base to measure the
        // frequency of use of this page
        this.lastAccessTimeMinutes = SystemUtils.get().currentTimeMinutes();
    }

    PageStats(TilePage page) {
        this(page.getId());
    }

    public void addHits(long numHits) {
        if (fillFactor <= 0f) {
            // we're in trouble, how could this happen? well because somehow the hits are being
            // recorded before the quota increase? it's not that tragic
            fillFactor = Float.MIN_VALUE;
        }
        // how relevant is this number of hits in relation to the number of tiles present in the
        // page?
        float hitsFactor = numHits / fillFactor;

        int now = SystemUtils.get().currentTimeMinutes();
        float diffMinutes = now - this.lastAccessTimeMinutes;
        // float averageAddedFrequency = diffMinutes == 0 ? numHits : numHits / diffMinutes;
        float averageAddedFrequency = diffMinutes == 0 ? hitsFactor : hitsFactor / diffMinutes;
        float newAvgFreq = diffMinutes == 0 ? (this.frequencyOfUse + averageAddedFrequency)
                : (this.frequencyOfUse + averageAddedFrequency) / 2f;
        this.frequencyOfUse = newAvgFreq;
        this.lastAccessTimeMinutes = now;
    }

    public void addTiles(long numTiles, BigInteger maxTiles) {
        if (fillFactor == 1.0f && numTiles >= 0) {
            return;
        }
        if (fillFactor == 0.0f && numTiles <= 0) {
            return;
        }
        // trading some computational overhead by storage savings here...
        BigDecimal currFillFactor = new BigDecimal(fillFactor);
        BigDecimal addedTiles = new BigDecimal(numTiles);
        BigDecimal addedFillFactor = addedTiles.divide(new BigDecimal(maxTiles), 7,
                RoundingMode.CEILING);
        currFillFactor = currFillFactor.add(addedFillFactor);
        this.fillFactor = currFillFactor.floatValue();
        if (fillFactor > 1f) {
            fillFactor = 1f;
        } else if (fillFactor < 0f) {
            fillFactor = 0f;
        }
    }

    public float getFillFactor() {
        return fillFactor;
    }

    public void setFillFactor(float fillFactor) {
        this.fillFactor = fillFactor;
    }

    public int getLastAccessTimeMinutes() {
        return lastAccessTimeMinutes;
    }

    public void setLastAccessMinutes(int lastAccessMinutes) {
        this.lastAccessTimeMinutes = lastAccessMinutes;
    }

    public long getPageId() {
        return pageId;
    }

    public void setPageId(long pageId) {
        this.pageId = pageId;
    }

    public float getFrequencyOfUsePerMinute() {
        return frequencyOfUse;
    }

    public void setFrequencyOfUsePerMinute(float lfuHotnes) {
        this.frequencyOfUse = lfuHotnes;
    }

}
