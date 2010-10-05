/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota.paging;

import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class TilePage implements Serializable {

    /**
     * Do not change if don't know what you're doing. Used in the custom
     * {@link #writeObject(java.io.ObjectOutputStream)} and
     * {@link #readObject(java.io.ObjectInputStream)} methods
     */
    private static final long serialVersionUID = 1L;

    /**
     * Current time with near-minute precision, meaning we can hold values up till year 2115
     */
    private static final AtomicInteger currentTime = new AtomicInteger(
            (int) (System.currentTimeMillis() / 1000L / 60L));
    static {
        CustomizableThreadFactory tf = new CustomizableThreadFactory("Minute-Timer");
        tf.setDaemon(true);
        tf.setThreadPriority((Thread.MAX_PRIORITY - Thread.MIN_PRIORITY) / 5);
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, tf);
        Runnable command = new Runnable() {
            public void run() {
                int minutesSinceJan1_1970 = (int) (System.currentTimeMillis() / 1000L / 60L);
                TilePage.currentTime.set(minutesSinceJan1_1970);
            }
        };
        executorService.scheduleAtFixedRate(command, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * x, y, z index for this page, in reverse order (z, y, x) to aid in a more efficient short
     * circuit comparison at {@link #equals(Object)}
     */
    private int[] zyxIndex;

    private AtomicLong numHits;

    private AtomicLong numTilesInPage;

    private transient int hashCode;

    private String gridsetId;

    /**
     * Last access time, with near-minute precision
     */
    private int accessTimeMinutes;

    private final String layerName;

    public TilePage(final String layerName, final String gridsetId, final int x, final int y,
            final int z) {
        this(layerName, gridsetId, x, y, z, 0L, 0L, 0);
    }

    public TilePage(final String layerName, final String gridSetId, final int x, final int y,
            final int z, final long numHits, final long numTilesInPage,
            final int lastAccessTimeMinutes) {
        this.layerName = layerName;
        this.gridsetId = gridSetId;
        this.zyxIndex = new int[] { z, y, x };
        this.numHits = new AtomicLong(numHits);
        this.accessTimeMinutes = lastAccessTimeMinutes;
        this.numTilesInPage = new AtomicLong(numTilesInPage);

        this.hashCode = 17 * (zyxIndex[0] + zyxIndex[1] ^ 2 + zyxIndex[2] ^ 3);
    }

    public void markHit() {
        numHits.addAndGet(1L);
        accessTimeMinutes = currentTime.get();
    }

    public String getGridsetId() {
        return gridsetId;
    }

    public long getNumTilesInPage() {
        return this.numTilesInPage.get();
    }

    public long getNumHits() {
        long hits = numHits.get();
        return hits;
    }

    public int getLastAccessTimeMinutes() {
        return accessTimeMinutes;
    }

    public int getX() {
        return zyxIndex[2];
    }

    public int getY() {
        return zyxIndex[1];
    }

    public int getZ() {
        return zyxIndex[0];
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TilePage)) {
            return false;
        }
        TilePage p = (TilePage) o;
        boolean equals = Arrays.equals(zyxIndex, p.zyxIndex);
        equals &= layerName.equals(p.layerName) && gridsetId.equals(p.gridsetId);
        return equals;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("[").append('\'')
                .append(layerName).append('/').append(gridsetId).append("', [").append(zyxIndex[2])
                .append(',').append(zyxIndex[1]).append(',').append(zyxIndex[0])
                .append("]. Hits: ").append(getNumHits()).append(". Access: ");
        if (accessTimeMinutes == 0) {
            sb.append("never");
        } else {
            sb.append(currentTime.get() - accessTimeMinutes).append("m ago.");
        }
        sb.append(", tiles: ").append(numTilesInPage);
        sb.append(']');
        return sb.toString();
    }

    /**
     * Increments by one the counter of available tiles for the page and returns the new value for
     * the counter
     */
    public long addTile() {
        return this.numTilesInPage.incrementAndGet();
    }

    /**
     * Decrements by one the counter of available tiles for the page and returns the new value for
     * the counter
     */
    public long removeTile() {
        return this.numTilesInPage.decrementAndGet();
    }

    public String getLayerName() {
        return layerName;
    }
}
