/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Collects statistics about a tile page, including its frequency of use, the last access time, the amount of tiles
 * present in the page expressed as a fill factor, and the total number of hits.
 *
 * <p>The information provided by this class drives the expiration policies of the disk quota module
 */
@SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
public class PageStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 719776699585233200L;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private long id;

    private long pageId;

    /**
     * Approximate average frequency of use of this page per minute, computed each time page
     * {@link #addHitsAndAccessTime hits } are added based on the previous frequency of use, the time elapsed since the
     * last use ({@link #lastAccessTimeMinutes}), and the new number of hits added in that period of time.
     */
    private float frequencyOfUse;

    private int lastAccessTimeMinutes;

    private float fillFactor;

    private BigInteger numHits;

    PageStats() {
        //
    }

    public PageStats(long pageId) {
        this.pageId = Long.valueOf(pageId);
        // should be the same than the tile creation time as is used as a base to measure the
        // frequency of use of this page
        this.numHits = BigInteger.ZERO;
        this.lastAccessTimeMinutes = SystemUtils.get().currentTimeMinutes();
    }

    PageStats(TilePage page) {
        this(page.getId());
    }

    public void addHitsAndAccessTime(final long addedHits, int lastAccessTimeMinutes, final int creationTimeMinutes) {

        if (lastAccessTimeMinutes < creationTimeMinutes) {
            lastAccessTimeMinutes = creationTimeMinutes;
        }

        if (fillFactor <= 0f) {
            // we're in trouble, how could this happen? well because somehow the hits are being
            // recorded before the quota increase? it's not that tragic
            fillFactor = Float.MIN_VALUE;
        }

        this.numHits = this.numHits.add(BigInteger.valueOf(addedHits));
        BigDecimal age = new BigDecimal(1 + lastAccessTimeMinutes - creationTimeMinutes);

        this.frequencyOfUse = new BigDecimal(this.numHits)
                .divide(age, 7, RoundingMode.CEILING)
                .multiply(new BigDecimal(fillFactor))
                .floatValue();

        this.lastAccessTimeMinutes = lastAccessTimeMinutes;
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
        BigDecimal addedFillFactor = addedTiles.divide(new BigDecimal(maxTiles), 7, RoundingMode.CEILING);
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[page: ").append(pageId);
        sb.append(", fillFactor: ").append(fillFactor);
        sb.append(", frequencyOfUse: ").append(frequencyOfUse);
        sb.append(", last access: ")
                .append(SystemUtils.get().currentTimeMinutes() - lastAccessTimeMinutes)
                .append("m ago]");
        return sb.toString();
    }

    public void setNumHits(BigInteger numHits) {
        this.numHits = numHits;
    }

    public BigInteger getNumHits() {
        return numHits;
    }
}
